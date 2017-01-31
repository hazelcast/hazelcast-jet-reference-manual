# Overview of Jet's Architecture and API

## DAG

At the core of Jet is the distributed computation engine based on the
paradigm of a _directed acyclic graph_ (DAG). In this graph, vertices
are units of data processing and edges are units of data routing and
transfer.

## Job

`Job` is a handle to the execution of a `DAG`. To create a job,
supply the `DAG` to a previously created `JetInstance`:

```java
JetInstance jet = Jet.newJetInstance(); // or Jet.newJetClient();
DAG dag = new DAG();
dag.newVertex(..);
jet.newJob(dag).execute().get();
```

As hinted in the code example, the job submission API is identical
whether you use it from a client machine or directly on an instance of a
Jet cluster member. This works because the `Job` instance is
serializable and the client can send it over the network when submitting
the job. The same `Job` instance can be submitted for execution many
times.

Job execution is asynchronous. The `execute()` call returns as soon as
the Jet cluster has been contacted and the serialized job sent to it.
The user gets a `Future` which can be inspected or waited on to find out
the outcome of a computation job. It is also cancelable and can send a
cancelation command to the Jet cluster.

Note that the `Future` only signals the status of the job, it doesn't
contain the result of the computation. The DAG explicitly models the
storing of results via its _sink_ vertices. Typically the results will
be in a Hazelcast map or another structure and have to be accessed by
their own API after the job is done.

### Resource Deployment

If the Jet cluster hasn't been started with all the job's computation
code already on the classpath, the code will have to be deployed
together with the Job instance:

```java
JobConfig config = new JobConfig();
config.addJar("..");
jet.newJob(dag, config).execute().get();
```

When persisting and reading data from the underlying IMDG instance, it
is important to be aware that the deployed code is used _only_ within
the scope of the executing Jet job.

## Vertex

Vertex is the main unit of work in a Jet computation. Conceptually, it
receives input from its inbound edges and emits data to its outbound
edges. Practically, it is a number of `Processor` instances which
receive each its own part of the full stream traveling over the inbound
edges, and likewise emits its own part of the full stream going down
the outbound edges.

### Edge ordinal

An edge is connected to a vertex with a given _ordinal_, which
identifies it to the vertex and its processors. When a processor
receives an item, it knows the ordinal of the edge on which the item
came in. Things are similar on the outbound side: the processor emits an
item to a given ordinal, but also has the option to emit the same item
to all ordinals. This is the most typical case and allows easy
replication of a data stream across several edges.

Whenever not explicitly stated, edge ordinal is assumed to be 0. There
must be no gaps in ordinal assignment, which means the vertex will have
inbound edges with ordinals 0..N and outbound edges with ordinals 0..M.

### Source and sink

Jet uses only one kind of vertex, but in practice there is an important
distinction between:

* _internal_ vertex which accepts input and transforms it into output;
* _source_ vertex which generates output without receiving anything;
* _sink_ vertex which consumes input and doesn't emit anything.

Sources and sinks must interact with the environment to store/load data,
making their implementation more involved compared to the internal
vertices, whose logic is self-contained.

### Local and Global Parallelism

The vertex is implemented by one or more instances of `Processor` on
each member. Each vertex can specify how many of its processors will run
per cluster member using the `localParallelism` property; every member
will have the same number of processors. A new `Vertex` instance has
this property set to `-1`, which requests to use the default value equal
to the configured size of the cooperative thread pool. The latter
defaults to `Runtime.availableProcessors()`.

The _global parallelism_ of the vertex is also an important value,
especially in terms of the distribution of partitions among processors.
It is equal to local parallelism multiplied by cluster size.

## Processor

`Processor` is the main type whose implementation is up to the user: it
contains the code of the computation to be performed by a vertex. There
are a number of Processor building blocks in the Jet API which allow the
user to just specify the computation logic, while the provided code
handles the processor's cooperative behavior. Refer to the section on
[AbstractProcessor](#abstractprocessor) below.

A processor's work can be conceptually described as follows: "receive
data from zero or more input streams and emit data into zero or more
output streams." Each stream maps to a single DAG edge (either inbound
or outbound). There is no requirement on the correspondence between
input and output items; a processor can emit any data it sees fit,
including none at all. The same `Processor` abstraction is used for all
kinds of vertices, including sources and sinks.

### Cooperative multithreading

Cooperative multithreading is one of the core features of Jet and can be
roughly compared to [green
threads](https://en.wikipedia.org/wiki/Green_threads). It is purely a
library-level feature and doesn't involve any low-level system or JVM
tricks; the [`Processor`](processor) API is simply designed in such a
way that the processor can do a small amount of work each time it is
invoked, then yield back to the Jet engine. The engine manages a thread
pool of fixed size and on each thread the processors take their turn in
a round-robin fashion.

The point of cooperative multithreading is much lower context-switching
cost and precise knowledge of the status of a processor's input and
output buffers, which determines its ability to make progress.

`Processor` instances are cooperative by default. The processor can opt
out of cooperative multithreading by overriding `isCooperative()` to
return `false`. Jet will then start a dedicated thread for it.

#### Requirements on a cooperative Processor

To maintain good overall throughput, a cooperative processor must take
care not to hog the thread for too long (a rule of thumb is up to a
millisecond at a time). Jet's design strongly favors cooperative
processors and most processors can and should be implemented to fit
these requirements. The major exception are sources and sinks because
they often have no choice but calling into blocking I/O APIs.

### Outbox

The processor deposits the items it wants to emit to an instance of
`Outbox`, which has a separate bucket for each outbound edge. The
buckets are unbounded, but each has a defined "high water mark" that
says when the bucket should be considered full. When the processor
realizes it has hit the high water mark, it should return from the
current processing callback and let the execution engine drain the
outbox.

## Steps taken to create and initialize a job

These are the steps taken to create and initialize a Jet job:

1. User builds the DAG and submits it to the local Jet client instance.
1. The client instance serializes the DAG and sends it to a member of
the Jet cluster. This member becomes the _coordinator_ for this Jet job.
1. Coordinator deserializes the DAG and builds an execution plan for
each member.
1. Coordinator serializes the execution plans and distributes each to
its target member.
1. Each member acts upon its execution plan by creating all the needed
tasklets, concurrent queues, network senders/receivers, etc.
1. Coordinator sends the signal to all members to start job execution.

The most visible consequence of the above process is the
`ProcessorMetaSupplier` type: the user must provide one for each
`Vertex`. In step 3 the coordinator deserializes the meta-supplier as a
constituent of the `DAG` and asks it to create `ProcessorSupplier`
instances which go into the execution plans. A separate instance of
`ProcessorSupplier` is created specifically for each member's plan. In
step 4 the coordinator serializes these and sends each to its member. In
step 5 each member deserializes its `ProcessorSupplier` and asks it to
create as many `Processor` instances as configured by the vertex's
`localParallelism` property.

This process is so involved because each `Processor` instance may need
to be differently configured. This is especially relevant for processors
driving a source vertex: typically each one will emit only a slice of
the total data stream, as appropriate to the partitions it is in charge
of.

## Convenience API to implement a Processor

### AbstractProcessor

`AbstractProcessor` is a convenience class designed to deal with most of
the boilerplate in implementing the full `Processor` API.

The first line of convenience are the `tryProcessN()` methods which
receive one item at a time, thus eliminating the need to write a
suspendable loop over the input items. There is a separate method
specialized for each edge from 0 to 4 (`tryProcess0`..`tryProcess4`) and
there is a catch-all method `tryProcessAny(ordinal, item)`. If the
processor doesn't need to distinguish between inbound edges, the latter
method is a good match; otherwise it is simpler to implement one or more
of the ordinal-specific methods. The catch-all method is also the only
way to access inbound edges beyond ordinal 4, but such cases are very
rare in practice.

A major complication arises from the requirement to observe the outbox
limits during a single processing step. If the processor emits many
items per step, the loop doing this must support being suspended at any
point and resumed later. This need arises in two typical cases:

- when a single input item maps to a multitude of output items;
- when items are emitted in the final step, after having received all
the input.

`AbstractProcessor` provides the method `emitCooperatively` to support
the latter and there is additional support for the former with the
nested class `FlatMapper`. These work with the `Traverser` abstraction
to cooperatively emit a user-provided sequence of items.

### Traverser

`Traverser` is a very simple functional interface whose shape matches
that of a `Supplier`, but with a contract specialized for the traversal
over a sequence of non-null items: each call to its `next()` method
returns another item of the sequence until exhausted, then keeps
returning `null`. The point of this type is the ability to implement
traversal over any kind of dataset or lazy sequence with minimum hassle:
often just by providing a one-liner lambda expression. This makes it
very easy to integrate into Jet's convenience APIs for cooperative
processors.

`Traverser` also sports some `default` methods that facilitate building
a simple transformation layer over the underlying sequence: `map`,
`filter`, and `flatMap`.

### Simple example

This is how a simple flatmapping processor would be implemented:

```java
public class ItemAndSuccessorP extends AbstractProcessor {
    private final FlatMapper<Integer, Integer> flatMapper =
        flatMapper(i -> traverseIterable(asList(i, i + 1)));

    @Override
    protected boolean tryProcess(int ordinal, Object item) {
        return flatMapper.tryProcess((int) item);
    }
}
```

For each received `Integer` item this processor emits the item and its
successor. It doesn't differentiate between inbound edges (treats data
from all edges the same way) and emits each item to all outbound edges
connected to its vertex.

### `Processors` utility class

As a further layer of convenience there are some ready-made Processor
implementations. These are the broad categories:

1. Sources and sinks for Hazelcast `IMap` and `IList`.
2. Processors with `flatMap`-type logic, including `map`, `filter`, and
the most general `flatMap`.
3. Processors that perform a reduction operation after grouping items by
key. These come in two flavors:
    a. _Accumulate:_ reduce by transforming an immutable value;
    b. _Collect:_ reduce by updating a mutable result container.

## Edge

An edge represents a link between two vertices in the DAG. Conceptually,
data flows between two vertices along an edge; practically, each
processor of the upstream vertex contributes to the overall data stream
over the edge and each processor of the downstream vertex receives a
part of that stream. Several properties of the `Edge` control the
routing from upstream to downstream processors.

For any given pair of vertices, there can be at most one edge between
them.

### Priority

In most cases the processor receives items from all inbound edges as
they arrive; however, there are important cases where the reception of
one edge must be delayed until all other edges are consumed in full. A
major example is a highly asymmetric join operation, where one of the
edges stands out with its high data volume. Normally, collating items
from several edges by a common key implies buffering all the data before
emitting the result, but there is also the option to receive and buffer
data from all edges except the large one, then start receiving from it
and immediately emitting data.

Edge consumption order is controlled by the _priority_ property. Edges
are sorted by their priority number (ascending) and consumed in that
order. Edges with the same priority are consumed without particular
ordering (as the data arrives).

### Local and Distributed Edges

A major choice to make in terms of data routing is whether the candidate
set of target processors is unconstrained, encompassing all processors
across the cluster, or constrained to just those running on the same
cluster member. This is controlled by the `distributed` property of the
edge. By default the edge is local and calling the `distributed()`
method removes this restriction.

With appropriate DAG design, network traffic can be minimized by
employing local edges. Local edges are implemented
with the most efficient kind of concurrent queue: single-producer,
single-consumer bounded queue. It employs wait-free algorithms on both
sides and avoids `volatile` writes by using `lazySet`.

### Forwarding Patterns

The forwarding pattern decides which of the processors in the candidate
set to route each particular item to.

#### Variable Unicast

This is the default forwarding pattern. For each item a single
destination processor is chosen with no further restrictions on the
choice. The only guarantee given by this pattern is that the item will
be received by exactly one processor, but typically care will be taken
to "spray" the items equally over all the reception candidates.

This choice makes sense when the data doesn't have to be partitioned,
usually implying a downstream vertex which can compute the result based
on each item in isolation.

#### Broadcast

The item is sent to all candidate receivers. This is useful when some
small amount of data must be broadcast to all downstream vertices.
Usually such vertices will have other inbound edges in addition to the
broadcasting one, and will use the broadcast data as context while
processing the other edges. In such cases the broadcasting edge will
have a raised priority. There are other useful combinations, like a
parallelism-one vertex that produces the same result on each member.

#### Partitioned

Each item is sent to the one processor responsible for the item's
partition ID. On a distributed edge, this processor will be unique
across the whole cluster. On a local edge, each member will have its
own processor for each partition ID.

Each processor can be assigned multiple partitions. The global number of
partitions is controlled by the number of partitions in the underlying
Hazelcast configuration. For more information about Hazelcast
partitioning, see the [Hazelcast reference guide](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#data-partitioning)

To calculate the partition ID for an item, by default Hazelcast
partitioning is used:

1. The partition key is serialized and converted into a byte array.
2. The byte array is hashed using Murmur3.
3. The result of the hash is mod by the number of partitions.

Partitioned edges can take an optional `keyExtractor` function which
 maps an item to its partition key. If no `keyExtractor` is specified,
the whole object as a whole is used as the partition key.

For even more control over how the partition ID is calculated, you can
implement a custom `Partitioner`. Special care must be taken to make
sure that this function returns the same result for same items on all
members in the cluster.

#### All to One

Activates a special-cased _partitioned_ forwarding pattern where all
items are assigned the same partition ID, randomly chosen at job
initialization time. This will direct all items to the same processor.

### Buffered Edges

In some special cases, unbounded data buffering must be allowed on an
edge. Consider the following scenario:

A vertex sends output to two edges, creating a fork in the DAG. The
branches later rejoin at a downstream vertex which assigns different
priorities to its two inbound edges. Since the data for both edges is
generated simultaneously, and since the lower-priority edge will apply
backpressure while waiting for the higher-priority edge to be consumed
in full, the upstream vertex will not be allowed to emit its data and a
deadlock will occur. The deadlock is resolved by activating unbounded
buffering on the lower-priority edge.

### Tuning Edges

Edges have some configuration properties which can be used for tuning
how the items are transmitted. The following options are available:

<table>
  <tr>
    <th style="width: 25%;">Name</th>
    <th>Description</th>
    <th>Default Value</th>
  </tr>
  <tr>
    <td>High Water Mark</td>
    <td>
      A Processor deposits its output items to its Outbox. It is an
      unbounded buffer, but has a "high water mark" which should be
      respected by a well-behaving processor. When its outbox reaches
      the high water mark,the processor should yield control back to its
      caller.
    </td>
    <td>2048</td>
  </tr>
  <tr>
    <td>Queue Size</td>
    <td>
      When data needs to travel between two processors on the same
      cluster member, it is sent over a concurrent single-producer,
      single-consumer (SPSC) queue of fixed size. This options controls
      the size of the queue.
      <br/>
      Since there are several processors executing the logic of each
      vertex, and since the queues are SPSC, there will be a total of
      <code>senderParallelism * receiverParallelism</code> queues
      representing the edge on each member. Care should be taken to
      strike a balance between performance and memory usage.
    </td>
    <td>1024</td>
  </tr>
  <tr>
    <td>Packet Size Limit</td>
    <td>
      For a distributed edge, data is sent to a remote member via
      Hazelcast network packets. Each packet is dedicated to the data of
      a single edge, but may contain any number of data items. This
      setting limits the size of the packet in bytes. Packets should be
      large enough to drown out any fixed overheads, but small enough to
      allow good interleaving with other packets.
      <br/>
      Note that a single item cannot straddle packets, therefore the
      maximum packet size can exceed the value configured here by the
      size of a single data item.
      <br/>
      This setting has no effect on a non-distributed edge.
    </td>
    <td>16384</td>
  </tr>
  <tr>
    <td>Receive Window Multiplier</td>
    <td>
      For each distributed edge the receiving member regularly sends
      flow-control ("ack") packets to its sender which prevent it from
      sending too much data and overflowing the buffers. The sender is
      allowed to send the data one receive window further than the last
      acknowledged byte and the receive window is sized in proportion to
      the rate of processing at the receiver.
      <br/>
      Ack packets are sent in regular intervals and the receive window
      multiplier sets the factor of the linear relationship between the
      amount of data processed within one such interval and the size of
      the receive window.
      <br/>
      To put it another way, let us define an ackworth to be  the amount
      of data processed between two consecutive ack packets. The receive
      window multiplier determines the number of ackworths the sender
      can be ahead of the last acked byte.
      <br/>
      This setting has no effect on a non-distributed edge.
     </td>
     <td>3</td>
  </tr>
</table>
# Internals of Jet

## ExecutionService

At the core of the Jet engine is the `ExecutionService`. This is the
component that drives the cooperatively-multithreaded execution of
Processors as well as other vital components, like network senders and
receivers.

### Tasklet

The execution service maintains a pool of worker threads, each of which
juggles several "green threads" captured by an abstraction called the
`Tasklet`. Each tasklet is given its turn and after it tries to do some
work, it returns two bits of information: whether it made any progress
and whether it is now done. As the worker cycles through its tasklets,
before each new new cycle it checks whether any tasklet made progress;
if not, it bumps its counter of idle cycles and acts according to the
current count: either try again or sleep for a while.

#### Work stealing

When a tasklet is done, the worker removes it from its tasklet pool.
Workers start out with tasklets evenly distributed among them, but as
the tasklets complete, the load on each worker may become imbalanced. To
counter this, a simple _work stealing_ mechanism is put into place: each
time it removes a tasklet from its pool, the worker (let's call it
"thief") will inspect all the other workers, locating the one with the
largest pool (call it "target"). If the target has at least two tasklets
more than the thief, it will pick one of the target's tasklets and mark
it with the instruction "give this one to me". When the target is about
to run the marked tasklet, it will observe the instruction and move the
tasklet to the thief's pool.

### ProcessorTasklet

`ProcessorTasklet` wraps a single processor instance and does the
following:

- drain the incoming concurrent queues into the processor's `Inbox`;
- let it process the inbox and fill its `Outbox`;
- drain the outbox into the outgoing concurrent queues;
- make sure that all of the above conforms to the requirements of
cooperative multithreading, e.g., yielding to other tasklets whenever an
outgoing queue is full.

### SenderTasklet and ReceiverTasklet

A distributed DAG edge is implemented with one `ReceiverTasklet` and as
many `SenderTasklet`s as there are target members (cluster size minus
one). Jet reuses Hazelcast's networking layer and adds its own type of
`Packet`, which can contain several data items traveling over a single
edge. The packet size limit is configurable; to minimize fixed overheads
from packet handling, Jet will try to stuff as many items as can fit
into a single packet. It will keep adding items until it notices the
limit is reached, which means that the actual packet size can exceed
the limit by the size of one item.

#### Network backpressure

A key concern in edge data transfer is _backpressure_: the downstream
vertex may not be able to process the items at the same rate as the
upstream vertex is emitting them. Within a member the concurrent queues
are bounded and naturally provide backpressure by refusing to accept an
item when full. However, over the network no such natural mechanism
exists, especially because the same TCP/IP connectionn is used for all
edges so TCP's own flow control mechanism is not sufficient to guard
an individual edge's limits. For that reason Jet introduces its own
flow-control mechanism based on the _adaptive receive window_.

Each member sends out flow-control packets (_ack packets_ for short) to
all other members at regular intervals, detailing to each individual
`SenderTasklet` how much more data it is allowed to send. A
`ReceiverTasklet` keeps track of how much data received from each member
it processed since the last sending of the ack packet. It uses this to
calculate the current rate of data processing, which then guides the
adaptive sizing of the receive window. The target size for the window is
determined before sending an ack packet: it is three times the data
processed since the last sending of the ack packet, and the receive
window is adjusted from the current size halfway towards the target
size.

# Understanding the Jet Architecture and API

This chapter provides an overview to the Hazelcast Jet's architecture and API units and elements.

## DAG

At the core of Jet is the distributed computation engine based on the
paradigm of a **directed acyclic graph** (DAG). In this graph, vertices
are units of data processing and edges are units of data routing and
transfer.

## Job

`Job` is a handle to the execution of a `DAG`. To create a job,
supply the `DAG` to a previously created `JetInstance` as shown below:

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
the Jet cluster has been contacted and the serialized job is sent to it.
The user gets a `Future` which can be inspected or waited on to find out
the outcome of a computation job. It is also cancelable and can send a
cancelation command to the Jet cluster.

Note that the `Future` only signals the status of the job, it does not
contain the result of the computation. The DAG explicitly models the
storing of results via its **sink** vertices. Typically the results will
be in a Hazelcast map or another structure and have to be accessed by
their own API after the job is done.

### Deploying the Resource

If the Jet cluster has not been started with all the job's computation
code already on the classpath, you have to deploy the code 
together with the Job instance:

```java
JobConfig config = new JobConfig();
config.addJar("..");
jet.newJob(dag, config).execute().get();
```

When persisting and reading data from the underlying Hazelcast IMDG instance, it
is important to be aware that the deployed code is used **only** within
the scope of the executing Jet job.

## Vertex

Vertex is the main unit of work in a Jet computation. Conceptually, it
receives input from its inbound edges and emits data to its outbound
edges. Practically, it is a number of `Processor` instances which
receive each of its own part of the full stream traveling over the inbound
edges, and likewise emits its own part of the full stream going down
the outbound edges.

### Edge Ordinal

An edge is connected to a vertex with a given **ordinal**, which
identifies it to the vertex and its processors. When a processor
receives an item, it knows the ordinal of the edge on which the item
came in. Things are similar on the outbound side: the processor emits an
item to a given ordinal, but also has the option to emit the same item
to all ordinals. This is the most typical case and allows easy
replication of a data stream across several edges.

In the DAG-building API the default value of the ordinal is 0. There
must be no gaps in ordinal assignment, which means a vertex will have
inbound edges with ordinals 0..N and outbound edges with ordinals 0..M.

### Source and Sink

Jet uses only one kind of vertex, but in practice there is an important
distinction between the following:

* **internal** vertex which accepts input and transforms it into output,
* **source** vertex which generates output without receiving anything,
* **sink** vertex which consumes input and does not emit anything.

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

The **global parallelism** of the vertex is also an important value,
especially in terms of the distribution of partitions among processors.
It is equal to local parallelism multiplied by the cluster size.

## Processor

`Processor` is the main type whose implementation is up to the user: it
contains the code of the computation to be performed by a vertex. There
are a number of Processor building blocks in the Jet API which allow you
to just specify the computation logic, while the provided code
handles the processor's cooperative behavior. Please refer to the 
[AbstractProcessor section](#abstractprocessor).

A processor's work can be conceptually described as follows: "receive
data from zero or more input streams and emit data into zero or more
output streams." Each stream maps to a single DAG edge (either inbound
or outbound). There is no requirement on the correspondence between
input and output items; a processor can emit any data it sees fit,
including none at all. The same `Processor` abstraction is used for all
kinds of vertices, including sources and sinks.

### Cooperative Multithreading

Cooperative multithreading is one of the core features of Jet and can be
roughly compared to [green
threads](https://en.wikipedia.org/wiki/Green_threads). It is purely a
library-level feature and does not involve any low-level system or JVM
tricks; the [`Processor`](#processor) API is simply designed in such a
way that the processor can do a small amount of work each time it is
invoked, then yield back to the Jet engine. The engine manages a thread
pool of fixed size and on each thread, the processors take their turn in
a round-robin fashion.

The point of cooperative multithreading is much lower context-switching
cost and precise knowledge of the status of a processor's input and
output buffers, which determines its ability to make progress.

`Processor` instances are cooperative by default. The processor can opt
out of cooperative multithreading by overriding `isCooperative()` to
return `false`. Jet will then start a dedicated thread for it.

#### Requirements for a Cooperative Processor

To maintain an overall good throughput, a cooperative processor must take
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

### Data-processing Callbacks

Three callback methods are involved in data processing: `process()`,
`completeEdge()`, and `complete()`.

Processors can be stateful and do not need to be thread-safe. A single
instance will be called by a single thread at a time, although not
necessarily always the same thread.

#### process()

Jet passes the items received over a given edge by calling
`process(ordinal, inbox)`. All items received since the last `process()`
call are in the inbox, but also all the items the processor has not
removed in a previous `process()` call. There is a separate instance of
`Inbox` for each  inbound edge, so any given `process()` call involves
items from only one edge.

The processor should not remove an item from the inbox until it has
fully processed it. This is important with respect to the cooperative
behavior: the processor may not be allowed to emit all items
corresponding to a given input item and may need to return from the
`process()` call early, saving its state. In such a case the item should
stay in the inbox so Jet knows the processor has more work to do even if
no new items are received.

#### completeEdge()

Eventually each edge will signal that its data stream is exhausted. When this
happens, Jet calls the processor's `completeEdge()` with the ordinal of
the completed edge.

The processor may want to emit any number of items upon this event, and
it may be prevented from emitting all due to a full outbox. In this case
it may return `false` and will be called again later.

#### complete()

Jet calls `complete()` after all the edges are exhausted and all the
`completeEdge()` methods are called. It is the last method to be invoked on
the processor before disposing of it. The semantics of the boolean
return value are the same as in `completeEdge()`.

## Creating and Initializing Jobs

These are the steps taken to create and initialize a Jet job:

1. The user builds the DAG and submits it to the local Jet client instance.
2. The client instance serializes the DAG and sends it to a member of
the Jet cluster. This member becomes the **coordinator** for this Jet job.
3. The coordinator deserializes the DAG and builds an execution plan for
each member.
4. The coordinator serializes the execution plans and distributes each to
its target member.
5. Each member acts upon its execution plan by creating all the needed
tasklets, concurrent queues, network senders/receivers, etc.
6. The coordinator sends the signal to all members to start job execution.

The most visible consequence of the above process is the
`ProcessorMetaSupplier` type: you must provide one for each
`Vertex`. In Step 3, the coordinator deserializes the meta-supplier as a
constituent of the `DAG` and asks it to create `ProcessorSupplier`
instances which go into the execution plans. A separate instance of
`ProcessorSupplier` is created specifically for each member's plan. In
Step 4, the coordinator serializes these and sends each to its member. In
Step 5 each member deserializes its `ProcessorSupplier` and asks it to
create as many `Processor` instances as configured by the vertex's
`localParallelism` property.

This process is so involved because each `Processor` instance may need
to be differently configured. This is especially relevant for processors
driving a source vertex: typically each one will emit only a slice of
the total data stream, as appropriate to the partitions it is in charge
of.

### ProcessorMetaSupplier

This type is designed to be implemented by the user, but the
`Processors` utility class provides implementations covering most cases.
You may need custom meta-suppliers primarily to implement a
custom data source or sink. Instances of this type are serialized and
transferred as a part of each `Vertex` instance in a `DAG`. The
**coordinator** member deserializes it to retrieve `ProcessorSupplier`s.
Before being asked for `ProcessorSupplier`s, the meta-supplier is given
access to the Hazelcast instance so it can find out the parameters of
the cluster the job will run on. Most typically, the meta-supplier in
the source vertex will use the cluster size to control the assignment of
data partitions to each member.

### ProcessorSupplier

Usually this type will be custom-implemented in the same cases where its
meta-supplier is custom-implemented and complete the logic of a
distributed data source's partition assignment. It supplies instances of
`Processor` ready to start executing the vertex's logic.

Please see
the [Implementing Custom Sources and Sinks section](#implementing-custom-sources-and-sinks)
for more guidance on how these interfaces can be implemented.

## Convenience API to Implement a Processor

### AbstractProcessor

`AbstractProcessor` is a convenience class designed to deal with most of
the boilerplate in implementing the full `Processor` API.

The first line of convenience are the `tryProcessN()` methods which
receive one item at a time, thus eliminating the need to write a
suspendable loop over the input items. There is a separate method
specialized for each edge from 0 to 4 (`tryProcess0`..`tryProcess4`) and
there is a catch-all method `tryProcessAny(ordinal, item)`. If the
processor does not need to distinguish between the inbound edges, the latter
method is a good match; otherwise, it is simpler to implement one or more
of the ordinal-specific methods. The catch-all method is also the only
way to access inbound edges beyond ordinal 4, but such cases are very
rare in practice.

A major complication arises from the requirement to observe the outbox
limits during a single processing step. If the processor emits many
items per step, the loop doing this must support being suspended at any
point and resumed later. This need arises in two typical cases:

- when a single input item maps to a multitude of output items,
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

`Traverser` also supports some `default` methods that facilitate building
a simple transformation layer over the underlying sequence: `map`,
`filter`, and `flatMap`.

### Simple Example

The following example shows how you can implement a simple flatmapping processor:

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
successor. It does not differentiate between inbound edges (treats data
from all edges the same way) and emits each item to all outbound edges
connected to its vertex.

### `Processors` Utility Class

As a further layer of convenience, there are some ready-made Processor
implementations. These are the broad categories:

1. Sources and sinks for Hazelcast `IMap` and `IList`.
2. Processors with `flatMap`-type logic, including `map`, `filter`, and
the most general `flatMap`.
3. Processors that perform a reduction operation after grouping items by
key. These come in two flavors:
    a. **Accumulate**: reduce by transforming an immutable value.
    b. **Collect**: reduce by updating a mutable result container.

Please refer to the [`Processors` Javadoc](https://github.com/hazelcast/hazelcast-jet/blob/master/hazelcast-jet-core/src/main/java/com/hazelcast/jet/Processor.java) for further details.

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

By default the processor receives items from all inbound edges as they
arrive. However, there are important cases where the reception of one
edge must be delayed until all other edges are consumed in full. A major
example is a join operation. Collating items from several edges by a
common key implies buffering the data from all edges except one before
emitting any results. Often there is one edge with much more data than
the others and this one does not need to be buffered if all the other
data is ready.

Edge consumption order is controlled by the **priority** property. Edges
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
employing local edges. Local edges are implemented with the most
efficient kind of concurrent queue: single-producer, single-consumer
bounded queue. It employs wait-free algorithms on both sides and avoids
`volatile` writes by using `lazySet`.

### Forwarding Patterns

The forwarding pattern decides which of the processors in the candidate
set to route each particular item to.

#### Variable Unicast

This is the default forwarding pattern. For each item a single
destination processor is chosen with no further restrictions on the
choice. The only guarantee given by this pattern is that the item will
be received by exactly one processor, but typically care will be taken
to "spray" the items equally over all the reception candidates.

This choice makes sense when the data does not have to be partitioned,
usually implying a downstream vertex which can compute the result based
on each item in isolation.

#### Broadcast

A broadcasting edge sends each item to all candidate receivers. This is
useful when some small amount of data must be broadcast to all
downstream vertices. Usually such vertices will have other inbound edges
in addition to the broadcasting one, and will use the broadcast data as
context while processing the other edges. In such cases the broadcasting
edge will have a raised priority. There are other useful combinations,
like a parallelism-one vertex that produces the same result on each
member.

#### Partitioned

A partitioned edge sends each item to the one processor responsible for
the item's partition ID. On a distributed edge, this processor will be
unique across the whole cluster. On a local edge, each member will have
its own processor for each partition ID.

Each processor can be assigned to multiple partitions. The global number of
partitions is controlled by the number of partitions in the underlying
Hazelcast IMDG configuration. Please refer to [Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#data-partitioning) for more information about Hazelcast IMDG
partitioning.

This is the default algorithm to determine the partition ID of an item:

1. Apply the `keyExtractor` function defined on the edge to retrieve the
partitioning key.
2. Serialize the partitioning key to a byte array using Hazelcast
serialization.
3. Apply Hazelcast's standard `MurmurHash3`-based algorithm to get the
key's hash value.
4. Partition ID is the hash value modulo the number of partitions.

The above procedure is quite CPU-intensive, but has the essential
property of giving repeatable results across all cluster members, which
may be running on disparate JVM implementations.

Another common choice is to use Java's standard `Object.hashCode()`. It 
is often significantly faster. However, it is not a safe strategy in
general because `hashCode()`'s contract does not require repeatable
results across JVMs, or even different instances of the same JVM
version.

You can provide your own implementation of `Partitioner` to gain full
control over the partitioning strategy.

#### All to One

The all-to-one forwarding pattern is a special-case of the **partitioned**
pattern where all items are assigned to the same partition ID, randomly
chosen at the job initialization time. This will direct all items to the
same processor.

### Buffered Edges

In some special cases, unbounded data buffering must be allowed on an
edge. Consider the following scenario:

A vertex sends output to two edges, creating a fork in the DAG. The
branches later rejoin at a downstream vertex which assigns different
priorities to its two inbound edges. Since the data for both edges is
generated simultaneously, and since the lower-priority edge will apply
backpressure while waiting for the higher-priority edge to be consumed
in full, the upstream vertex will not be allowed to emit its data and a
deadlock will occur. The deadlock is resolved by activating the unbounded
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
      To put it another way, let us define an ackworth to be the amount
      of data processed between two consecutive ack packets. The receive
      window multiplier determines the number of ackworths the sender
      can be ahead of the last acked byte.
      <br/>
      This setting has no effect on a non-distributed edge.
     </td>
     <td>3</td>
  </tr>
</table>

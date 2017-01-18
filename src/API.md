# API Reference

## DAG

`DAG` stands for the _directed acyclic graph_ which models the
computation to be performed by a Jet job. Vertices are units of data
processing and edges are units of data routing and transfer. A `DAG`
instance is serializable and the client sends it over the network
when submitting a job for execution.

## Job

`Job` is a handle to the execution of a DAG. The same `Job` instance can
be submitted for execution many times. It also holds additional
information, such as the resources that need to be deployed along with
the DAG.

See also: [Resource Deployment](#resource-deployment)

## Vertex

Vertex is the main unit of work in a Jet computation. Conceptually, it
receives input from its inbound edges and emits data to its outbound
edges. Practically, it is a number of `Processor` instances which
receive each its own part of the full stream traveling over the inbound
edges, and likewise emits its own part of the full stream going down
the outbound edges.

### Source and sink

Jet uses only one kind of vertex, but in practice there is an important
distinction between:

* _internal_ vertex which accepts input and transforms it to output;
* _source_ vertex which generates output without receiving anything;
* _sink_ vertex which consumes input and doesn't emit anything.

Sources and sinks must interact with the environment to store/load data,
making their implementation more involved compared to the internal
vertices, whose logic is self-contained.


### Local and Global Parallelism

The vertex is implemented by one or more instances of `Processor` on
each member. This number is configurable for each vertex individually:
it is its `localParallelism` property. A new `Vertex` instance has this
property set to `-1`, which requests to use the default value equal to
the configured size of the cooperative thread pool. The latter defaults
to `Runtime.availableProcessors()`.

The _global parallelism_ of the vertex is also an important value,
especially in terms of the distribution of partitions among processors.
It is equal to local parallelism multiplied by cluster size.


## Processor

`Processor` is the main type whose implementation is up to the user: it
contains the code of the computation to be performed by a vertex. As
already mentioned, many processors for the same vertex run in parallel
on each member of the cluster, receiving a part of the input dataset
and emitting a part of the output dataset.

The same `Processor` abstraction is used for all kinds of vertices,
including the terminal ones which act as data sources and sinks. In
general, a processor receives data from zero or more input streams and
emits data into zero or more output streams.

### isCooperative()

To maintain good overall throughput, a cooperative processor must take
care not to hog the thread for too long (a rule of thumb is up to a
millisecond at a time). The processor can opt out of [cooperative
multithreading](#cooperative-multithreading) by overriding
`isCooperative()` to return `false`. Jet will then start a dedicated
thread for it.

### Outbox

The processor deposits the items it wants to emit to an instance of
`Outbox`, which has a separate bucket for each outbound edge. The
buckets are unbounded, but each has a defined "high water mark" that
says when the bucket should be considered full. When the processor
realizes it has hit the high water mark, it should return from the
current processing callback and let the execution engine drain the
outbox.


### Data-processing callbacks

Three callback methods are involved in data processing: `process()`,
`completeEdge()`, and `complete()`.

#### process()

Jet passes the items received over a given edge by calling
`process(ordinal, inbox)`. All items received since the last `process()`
call are in the inbox, but also all the items the processor hasn't
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

Eventually each edge will signal its data stream is exhausted. When this
happens, Jet calls the processor's `completeEdge()` with the ordinal of
the completed edge.

The processor may want to emit any number of items upon this event, and
it may be prevented from emitting all due to a full outbox. In this case
it may return `false` and will be called again later.

#### complete()

Jet calls `complete()` after all the edges are exhausted and all the
`completeEdge()` methods called. It is the last method to be invoked on
the processor before disposing of it. The semantics of the boolean
return value are the same as in `completeEdge()`.


### Creation and initialization of Processor instances

As described in the [Architecture](create-and-execute-a-job) section,
job submission is a multistage process which gives rise to the concept
of the _processor meta-supplier_.

#### ProcessorMetaSupplier

This type is designed to be implemented by the user, but the
`Processors` utility class provides implementations covering most cases.
Custom meta-suppliers are expected to be needed primarily to implement a
custom data source or sink. Instances of this type are serialized and
transferred as a part of each `Vertex` instance in a `DAG`. The
_coordinator_ member deserializes it to retrieve `ProcessorSupplier`s.
Before being asked for `ProcessorSupplier`s, the meta-supplier is given
access to the Hazelcast instance so it can find out the parameters of
the cluster the job will run on. Most typically, the meta-supplier in
the source vertex will use the cluster size to control the assignment of
data partitions to each member.

#### ProcessorSupplier

Usually this type will be custom-implemented in the same cases where its
meta-supplier is custom-implemented and complete the logic of a
distributed data source's partition assignment. It supplies instances of
`Processor` ready to start executing the vertex's logic.

For more guidance on how these interfaces can be implemented, see
the section [Implementing Custom Sources and Sinks](#implementing-custom-sources-and-sinks).

#### SimpleProcessorSupplier

This is a simple functional interface, a serializable specialization of
`Supplier<Processor>`. It allows the user a means of implementing the
processor meta-supplier in the simplest, but also the most common case
where processor instances can be created without reference to any
context parameters. Typically, only the sources and sinks will require
context-sensitive configuration and the user will most likely not have
to implement them.

The user can pass in a `SimpleProcessorSupplier` instance as a lambda
expression and Jet does all the boilerplate of building a full
meta-supplier from it.

### AbstractProcessor

`AbstractProcessor` is a convenience class designed to deal with most of
the boilerplate in implementing the full `Processor` API. The main
complication arises from the requirement to observe the output buffer
limits during a single processing step. If the processor emits many
items per step, the loop doing this must support being suspended at any
point and resumed later. This need arises in two typical cases:

- when a single input item maps to a multitude of output items;
- when items are emitted in the final step, after having received all
the input.

`AbstractProcessor` provides the method `emitCooperatively` to support
the latter and there is additional support for the former with the
nested class `TryProcessor`. These work with the `Traverser` abstraction
to cooperatively emit a user-provided sequence of items.

### Traverser

`Traverser` is a very simple functional interface whose shape matches
that of a `Supplier`, but with a contract specialized for the traversal
over a sequence of non-null items: each call to its `next()` method
returns another item of the sequence until exhausted, then keeps
returning `null`. `Traverser` also sports some `default` methods that
facilitate building a simple transformation layer over the underlying
sequence: `map`, `filter`, and `flatMap`.

## Edge

An edge represents a link between two vertices in the DAG. Conceptually,
data flows between two vertices along an edge; practically, each
processor of the upstream vertex contributes to the overall data stream
over the edge and each processor of the downstream vertex receives a
part of that stream. Several properties of the `Edge` control the
routing from upstream to downstream processors.

There can only be a single edge between two vertices.

### Ordinals

An edge is connected to a vertex with a given _ordinal_, which
identifies it to the vertex and its processors. When a processor
receives an item, it knows the ordinal of the edge on which the item
came in. Things are similar on the outbound side: the processor emits an
item to a given ordinal, but also has the option to emit the same item
to all ordinals. This is the most typical case and allows easy
replication of a data stream across several edges.

Edges by default will have ordinal 0. Two outgoing edges from or
incoming edges to the same vertex must have different ordinals, and gaps
in ordinal sequence are not allowed.

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

Each processor can be assigned multiple partitions. The global umber of
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
deadlock will occur. The deadlock is resolved by  activating unbounded
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

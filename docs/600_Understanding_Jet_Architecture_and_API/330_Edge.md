An edge represents a connection between two vertices in the DAG.
Conceptually, data flows between two vertices along an edge;
practically, each processor of the upstream vertex contributes to the
overall data stream over the edge and each processor of the downstream
vertex receives a part of that stream. For any given pair of vertices,
there can be at most one edge between them.

Several properties of the `Edge` control the routing from upstream to
downstream processors.

## Local and Distributed Edge

A major choice to make in terms of data routing is whether the candidate
set of target processors is unconstrained, encompassing all processors
across the cluster, or constrained to just those running on the same
cluster member. This is controlled by the `distributed` property of the
edge. By default the edge is local and calling the `distributed()`
method removes this restriction.

With appropriate DAG design, network traffic can be minimized by
employing local edges. Local edges are implemented with the most
efficient kind of concurrent queue: single-producer, single-consumer
array-backed queue. It employs wait-free algorithms on both sides and
avoids the latency of `volatile` writes by using `lazySet`.

## Routing Policies

The routing policy decides which of the processors in the candidate
set to route each particular item to.

### Unicast

This is the default routing policy. For each item a single destination
processor is chosen with no further restrictions on the choice. The only
guarantee given by this pattern is that the item will be received by
exactly one processor, but typically care will be taken to "spray" the
items equally over all the reception candidates.

This choice makes sense when the data does not have to be partitioned,
usually implying a downstream vertex which can compute the result based
on each item in isolation.

### Broadcast

A broadcasting edge sends each item to all candidate receivers. This is
useful when some small amount of data must be broadcast to all
downstream vertices. Usually such vertices will have other inbound edges
in addition to the broadcasting one, and will use the broadcast data as
context while processing the other edges. In such cases the broadcasting
edge will have a raised priority. There are other useful combinations,
like a parallelism-one vertex that produces the same result on each
member.

### Partitioned

A partitioned edge sends each item to the one processor responsible for
the item's partition ID. On a distributed edge, this processor will be
unique across the whole cluster. On a local edge, each member will have
its own processor for each partition ID.

Multiple partitions can be assigned to each processor. The global number
of partitions is controlled by the number of partitions in the
underlying Hazelcast IMDG configuration. Please refer to the
[Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#data-partitioning)
for more information about Hazelcast IMDG partitioning.

This is the default algorithm to determine the partition ID of an item:

1. Apply the key extractor function defined on the edge to retrieve the
partitioning key.
2. Serialize the partitioning key to a byte array using Hazelcast
serialization.
3. Apply Hazelcast's standard `MurmurHash3`-based algorithm to get the
key's hash value.
4. Partition ID is the hash value modulo the number of partitions.

The above procedure is quite CPU-intensive, but has the crucial
property of giving repeatable results across all cluster members, which
may be running on disparate JVM implementations.

Another common choice is to use Java's standard `Object.hashCode()`. It
is often significantly faster. However, it is not a safe strategy in
general because `hashCode()`'s contract does not require repeatable
results across JVMs, or even different instances of the same JVM
version. If a given class's Javadoc explicitly specifies the hashing
function used, then its instances are safe to partition with
`hashCode()`.

You can provide your own implementation of `Partitioner` to gain full
control over the partitioning strategy.

### All-To-One

The all-to-one routing policy is a special-case of the `partitioned`
policy which assigns the same partition ID to all items. The partition
ID is randomly chosen at job initialization time. This policy makes
sense on a distributed edge when all the items from all the members must
be routed to the same member and the same processor instance running on
it. Local parallelism of the target vertex should be set to 1, otherwise
there will be idle processors that never get any items.

On a local edge this policy doesn't make sense since just setting the
local parallelism of the target vertex to 1 constrains the local choice
to just one processor instance.

## Priority

By default the processor receives items from all inbound edges as they
arrive. However, there are important cases where an edge must be
consumed in full to make the processor ready to accept data from other
edges. A major example is a "hash join" which enriches the data stream
with data from a lookup table. This can be modeled as a join of two data
streams where the "left" one delivers the contents of the lookup table
and the "right" one is the main data stream that will be enriched from
the lookup table.

The `priority` property controls the order of consuming the edges. Edges
are sorted by their priority number (ascending) and consumed in that
order. Edges with the same priority are consumed without particular
ordering (as the data arrives).

## Buffered Edge

In some special cases, unbounded data buffering must be allowed on an
edge. Consider the following scenario:

A vertex sends output to two edges, creating a fork in the DAG. The
branches later rejoin at a downstream vertex which assigns different
priorities to its two inbound edges. Since the data for both edges is
generated simultaneously, and since the lower-priority edge will apply
backpressure while waiting for the higher-priority edge to be consumed
in full, the upstream vertex will not be allowed to emit its data and a
deadlock will occur. The deadlock is resolved by activating the
unbounded buffering on the lower-priority edge.

## Fine-Tuning

Edges have some configuration properties which can be used for tuning
how the items are transmitted. The following options are available:

<table>
  <tr>
    <th style="width: 25%;">Name</th>
    <th>Description</th>
    <th>Default Value</th>
  </tr>
  <tr>
    <td>Outbox capacity</td>
    <td>
        A cooperative processor's outbox will contain a bucket dedicated
        to this edge. When the bucket reaches the configured capacity, it will
        refuse further items. At that time the processor must yield control back
        to its caller.
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

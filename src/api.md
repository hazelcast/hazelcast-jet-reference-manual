# API Reference

## DAG

The _directed acyclic graph_ defines the computation to be performed
in a declerative manner - by listing all the different vertices and how 
they are connected via edges. The DAG itself is portable between Jet 
instances.

## Job

A `Job` could be thought of as an executable version of a DAG. Where 
as a DAG describes the computation, once a `Job` is created, it can be
executed one or more times.

A Job also holds additional information, such as the resources that 
need to be deployed along with the DAG.

Also see: [Resource Deployment](#resource-deployment)

## Vertex

Vertex is the main unit of work in a Jet computation. A vertex receives
input from its inbound edges, and pushes data out through it's outbound 
edges in the graph. Each Vertex has corresponding `Processor` 
instances, which are responsible fortransforming zero or more inputs to 
zero or more outputs.

### Local and Global Parallelism

The vertex is implemented by one or more instances of `Processor`. The
 number of `Processor` instances for each vertex is implemented by 
 the `parallelism` option. The number of instances on a single node are 
 by default equal to the number of Jet execution threads, which in 
 turn default to number of OS threads on the machine. 

Since the whole DAG is distributed on each node, there will also be a 
_global parallelism_, which is the total number of processor 
instances across the whole cluster.

## Processor

The Processor is where input streams are transformed into output streams. 
Each Vertex will have one ore more corresponding `Processor` instances. 

A processor, when combined with the topology of the vertex it is part of, 
can act as a data source, data sink or an intermediate, or any combination 
of these. It can join multiple data streams into one or split a single 
input into multiple outputs. 

###  Instantiation

Some processors are either stateless or are not dependent on some initial 
value, but in some cases it is necessary to have fine  possible to control 
exactly how these `Processor` instances are generated

#### ProcessorMetaSupplier

#### ProcessorSupplier

### AbstractProcessor

`AbstractProcessor` is a convenience class designed to take away some of the 
complexity of writing cooperative processors, and 
provides some utility methods for this purpose.

## Edge

An edge represents a link between two vertices in the DAG. Data flows 
from between two vertices along an edge, and this flow can be controlled
by various methods on the Edge API.

### Ordinals

Each edge has an ordinal at the source and one at the destination. If a
vertex will only have a single input or output, the ordinal will 
always be 0. For vertices with multiple inputs or outputs, then the 
ordinals for the additional vertices  will need to be set explicitly.

## Priority

Incoming edges will be processed by a vertex in the order of 
priority: edges with a lower priority number will be processed first.
Lower priority edges will not be processed until all higher priority 
edges have been exhausted.

This is useful for example when implementing a hash join - where you have
multiple inputs: one or more smaller inputs and one very large input. 
The edge with the large input would be lower priority than the others, 
so that all of the small inputs can be buffered in memory before 
starting to stream the larger input.

### Local and Distributed Edges

All edges are local by default: the items are only forwarded to `Processor`s 
on the same on the same node. If an edge is specified as `distributed`, then
it might be forwarded to `Processor` instances running on other nodes. This 
option can be combinedwith [Forwarding Patterns](forwarding-patterns) for 
various forwarding patterns.

### Forwarding Patterns

Forwarding patterns control how data is forwarded along an edge. Since
there can be multiple processor instances on the destination vertex, 
a choice needs to be made about which processor(s) will receive the items.

#### Variable Unicast

This is the default forwarding pattern. For each item, a single destination 
processor is chosen, with no specific restrictions on the choice. The only 
guarantee given by this method is that the item will be
 received by exactly one processor.

#### Broadcast

The item is sent to all candidate processors. In a local edge, this 
will only be local processors. In a distributed edge, all processors on 
all nodes will receive the item.

Broadcast edges are typically used with _hash join_ operations, where each 
`Processor` instance will hold the whole of the "small" side of the join in 
memory, and will join it against a "large" side, which is typically retrieved
in a streaming fashion.

#### Partitioned

Each item is sent to the one processor responsible for the item's 
partition ID. On a distributed edge, the processor is unique across the 
cluster; on a non-distributed edge the processor is unique only within 
a member.

#### All to One

Activates a special-cased {@link ForwardingPattern#PARTITIONED PARTITIONED} 
forwarding pattern where all items will be assigned the same, randomly
chosen partition ID. Therefore all items will be directed to the same
processor.

### Buffered Edges

A buffered edge is to enable some special-case edges to be able to buffer 
unlimited amount of data. Imagine the following scenario:

A vertex sends output to two edges, creating a fork in the DAG. The 
branches later rejoin at a downstream vertex which assigns different 
priorities to its two inbound edges. The one with the lower priority 
won't be consumed until the higher-priority one is consumed in full. 
However, since the data for both edges is generated simultaneously, 
and since the lower-priority input will apply backpressure while 
waiting for the 
higher-priority input to be consumed, this will result in a deadlock. 
The deadlock is resolved by activating unbounded buffering on the 
lower-priority edge.

### Tuning Edges

Edges have some configuration properties which can be used for tuning how 
the items are transmitted. The following options
are available:

<table>
    <tr>
      <th>Name</th>
      <th>Description</th>
      <th>Default Value</th>
    </tr>
    <tr>
        <td>High Water Mark</td>
        <td>
            A Processor deposits its output items to its Outbox. It is an 
            unbounded buffer, but has a "high water mark" which 
            should be respected by a well-behaving processor. When its outbox reaches 
            the high water mark, the processor should yield control back to its caller.
        <td>2048</td>
    </tr>
    <tr>
        <td>Queue Size</td>
        <td>
            When data needs to travel between two processors on the  
            same cluster member, it is sent over a concurrent
            single-producer, single-consumer (SPSC) queue of fixed 
            size. This options controls the size of the queue.
            <p/>
            Since there are several processors executing the logic of each vertex,
            and since the queues are SPSC, there will be 
            senderParallelism * receiverParallelism queues 
            representing the edge on each member. Care should be taken
            to strike a balance between performance and memory usage.
        <td>1024</td>
    </tr>
    <tr>
        <td>Packet Size Limit</td>
        <td>
            For a distributed edge, data is sent to a remote member via
            Hazelcast network packets. Each packet is dedicated to the
            data of a single edge, but may contain any number of 
            data items. This setting limits 
            the size of the packet in bytes. Packets should be large 
            enough to drown out any fixed overheads, 
            but small enough to allow good interleaving with other packets.
            <p/>
            Note that a single item cannot straddle packets, 
            therefore  the maximum packet size can exceed the value 
            configured here by the size of a single data item.
            <p/>
            This setting has no effect on a non-distributed edge.
        <td>16384</td>
    </tr>
    <tr>
            <td>Receive Window Multiplier</td>
            <td>
                For each distributed edge the receiving member regularly sends 
                flow-control ("ack") packets to its sender which 
                prevent it from sending too much data and overflowing the buffers.
                The sender is allowed to send the data one receive 
                window further than the last acknowledged byte and the 
                receive window is sized in proportion to the rate of
                processing at the receiver.
                <p/>
                Ack packets are sent in regular intervals and the 
                receive window multiplier sets the factor of the linear 
                relationship between the amount of data processed 
                within one such interval and the size of the receive window.
                <p/>
                To put it another way, let us define an ackworth to 
                be  the amount of data processed between two consecutive 
                ack packets. The receive window multiplier determines
                the number of ackworths the sender can be ahead of
                the last acked byte.
                <p/>
                This setting has no effect on a non-distributed edge.
             </td>
            <td>3</td>
        </tr>
</table>


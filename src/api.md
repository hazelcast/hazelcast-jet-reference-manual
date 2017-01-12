# API Reference

## Processor

## AbstractProcessor

`AbstractProcessor` is a convenience class designed to take away some of the complexity of writing cooperative processors, and 
provides some utility methods for this purpose.

### Processor instantiation

#### ProcessorMetaSupplier

#### ProcessorSupplier

## Edge

### Ordinals

### Local and Distributed Edges

All edges are local by default: the items are only forwarded to `Processor`s on the same on the same node. If an edge is specified 
as `distributed`, then it might be forwarded to `Processor` instances running on other nodes.

### Forwarding Patterns

Forwarding patterns control how data is forwarded along an edge. Since there can be multiple processor instances on the 
destination vertex, a choice needs to be made about which processor(s) will receive the items.

#### Variable Unicast

This is the default forwarding pattern. For each item, a single destination processor is chosed, with no specific restrictions on
the choice. The only guarantee given by this method is that the item will be received by exactly one processor.

#### Broadcast

The item is sent to all candidate processors. In a local edge, this will only be local processors. In a distributed edge, 
all processors on all nodes will receive the item.

#### Partitioned

Each item is sent to the one processor responsible for the item's partition ID. On a distributed edge, the processor 
is unique across the cluster; on a non-distributed edge the processor is unique only within a member.

#### All to One

Activates a special-cased {@link ForwardingPattern#PARTITIONED PARTITIONED} forwarding pattern where all items will 
be assigned the same, randomly chosen partition ID. Therefore all items will be directed to the same processor.

### Buffered Edges


### Tuning Edges

Edges have some configuration properties which can be used for tuning how the items are transmitted. The following options
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
            A Processor deposits its output items to its Outbox. It is an unbounded buffer, 
            but has a "high water mark" which should be respected by a well-behaving processor. When its outbox reaches 
            the high water mark, the processor should yield control back to its caller.
        <td>2048</td>
    </tr>
    <tr>
        <td>Queue Size</td>
        <td>
            When data needs to travel between two processors on the same cluster member, it is sent over a concurrent
            single-producer, single-consumer (SPSC) queue of fixed size. This options controls the size of the queue.
            <p/>
            Since there are several processors executing the logic of each vertex, and since the queues are SPSC, there will be
            senderParallelism * receiverParallelism queues representing the edge on each member. Care should be taken 
            to strike a balance between performance and memory usage.
        <td>1024</td>
    </tr>
    <tr>
        <td>Packet Size Limit</td>
        <td>
            For a distributed edge, data is sent to a remote member via Hazelcast network packets. Each packet is
            dedicated to the data of a single edge, but may contain any number of data items. This setting limits 
            the size of the packet in bytes. Packets should be large enough to drown out any fixed overheads, 
            but small enough to allow good interleaving with other packets.
            <p/>
            Note that a single item cannot straddle packets, therefore the maximum packet size can exceed the value 
            configured here by the size of a single data item.
            <p/>
            This setting has no effect on a non-distributed edge.
        <td>16384</td>
    </tr>
    <tr>
            <td>Receive Window Multiplier</td>
            <td>
                For each distributed edge the receiving member regularly sends flow-control ("ack") packets to its sender
                which prevent it from sending too much data and overflowing the buffers. The sender is allowed to send
                the data one receive window further than the last acknowledged byte and the receive window is sized 
                in proportion to the rate of processing at the receiver.
                <p/>
                Ack packets are sent in regular intervals and the receive window multiplier sets the factor of the linear 
                relationship between the amount of data processed within one such interval and the size of the receive window.
                <p/>
                To put it another way, let us define an ackworth to be the amount of data processed between two consecutive 
                ack packets. The receive window multiplier determines the number of ackworths the sender can be ahead of
                the last acked byte.
                <p/>
                This setting has no effect on a non-distributed edge.
             </td>
            <td>3</td>
        </tr>
</table>


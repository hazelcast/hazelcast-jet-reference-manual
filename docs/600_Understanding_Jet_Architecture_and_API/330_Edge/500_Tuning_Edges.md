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

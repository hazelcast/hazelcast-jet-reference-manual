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

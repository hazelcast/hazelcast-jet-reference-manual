Like a source, a sink is just another kind of processor. It accepts
items from the inbox and pushes them into some system external to the
Jet job (Hazelcast IMap, files, databases, distributed queues, etc.). A
simple way to implement it is to extend `AbstractProcessor` and override
`tryProcess`, which deals with items one at a time. However, sink
processors must often explicitly deal with batching. In this case
directly implementing `Processor` is better because its `process()`
method gets the entire `Inbox` which can be drained to a buffer and
flushed out.

Two callback methods are involved in data processing: `process()` and
`complete()`. Implementations of these methods can be stateful and do
not need to be thread-safe. A single instance will be called by a single
thread at a time, although not necessarily always the same thread.

### process(ordinal, inbox)

Jet passes the items received over a given edge by calling
`process(ordinal, inbox)`. All items received since the last `process()`
call are in the inbox, but also all the items the processor hasn't
removed in a previous `process()` call. There is a separate instance of
`Inbox` for each  inbound edge, so any given `process()` call involves
items from only one edge.

The processor must not remove an item from the inbox until it has
fully processed it. This is important with respect to the cooperative
behavior: the processor may not be allowed to emit all items
corresponding to a given input item and may need to return from the
`process()` call early, saving its state. In such a case the item should
stay in the inbox so Jet knows the processor has more work to do even if
no new items are received.

### tryProcess()

A job that processes an infinite data stream may experience occasional lulls &mdash; periods with no items arriving. On the other hand, a windowing processor is not allowed to act upon each item immediately due to event skew; it must wait for the punctuation to arrive. During a stream lull this becomes problematic because punctuation itself is primarily data-driven and advances in response to the observation of event timestamps. The punctuation-inserting processor must be able to advance punctuation even during a stream lull, based on the passage of wall-clock time. This is the primary use case for the `tryProcess()` callback, which takes no arguments. It does have a `boolean` return value, and if it returns `false`, then it will be called again before any other methods are called. This way it can retry emitting its output until the outbox accepts it.


### complete()

Jet calls `complete()` after all the input edges are exhausted. It is
the last method to be invoked on the processor before disposing of it.
Typically this is where a batch processor emits the results of an
accumulating operation. If it can't emit everything in a given call, it
should return `false` and will be called again later.

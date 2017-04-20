Two callback methods are involved in data processing: `process()` and
`complete()`. Implementations of these methods can be stateful and do
not need to be thread-safe. A single instance will be called by a single
thread at a time, although not necessarily always the same thread.

#### process()

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

#### completeEdge()

Eventually each edge will signal that its data stream is exhausted. When this
happens, Jet calls the processor's `completeEdge()` with the ordinal of
the completed edge.

The processor may want to emit any number of items upon this event, and
it may be prevented from emitting all due to a full outbox. In this case
it may return `false` and will be called again later.

#### complete()

Jet calls `complete()` after all the input edges are exhausted. It is
the last method to be invoked on the processor before disposing of it.
Typically this is where the processor emits the results of an
accumulating operation. If it can't emit everything in a given call, it
should return `false` and will be called again later.

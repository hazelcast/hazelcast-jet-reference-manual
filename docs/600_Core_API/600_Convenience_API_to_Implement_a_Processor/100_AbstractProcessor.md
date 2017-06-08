`AbstractProcessor` is a convenience class designed to deal with most of
the boilerplate in implementing the full `Processor` API.

## Receiving items

On the reception side the first line of convenience are the `tryProcessN()` methods. While in the inbox the punctuation and data items are interleaved, these methods take care of the boilerplate needed to filter out the punctuation. Additionally, they get one item at a time, eliminating the need to write a suspendable loop over the input items.

There is a separate method specialized for each edge from 0 to 4 (`tryProcess0`..`tryProcess4`) and a catch-all method `tryProcess(ordinal, item)`. If the processor doesn't need to distinguish between the inbound edges, the latter method is a good match; otherwise, it is simpler to implement one or more of the ordinal-specific methods. The catch-all method is also the only way to access inbound edges beyond ordinal 4, but such cases are very rare in practice.

Paralleling the above there are `tryProcessPunc(ordinal, punc)` and `tryProcessPuncN(punc)` methods that get just the punctuation items.

## Emitting items

`AbstractProcessor` declares two method variants that emit items: `tryEmit()` for cooperative processors and `emit()` for non-cooperative ones. Whereas `tryEmit()` returns a `boolean` that tells you whether the outbox accepted the item, `emit()` fails when the outbox refuses it.

A major complication arises from the fact that the outbox has limited
capacity and can refuse an item at any time. The processor must be
implemented to expect this, and when it happens it must save its state
and return from the current invocation. Things get especially tricky
when there are several items to emit, such as:

- when a single input item maps to many output items
- when the processor performs a group-by-key operation and emits the
groups as separate items

`AbstractProcessor` provides the method `emitFromTraverser` to support
the latter and there is additional support for the former with the
nested class `FlatMapper`. These work with the `Traverser` abstraction
to cooperatively emit a user-provided sequence of items.

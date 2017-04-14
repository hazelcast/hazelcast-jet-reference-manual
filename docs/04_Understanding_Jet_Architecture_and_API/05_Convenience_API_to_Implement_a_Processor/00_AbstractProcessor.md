`AbstractProcessor` is a convenience class designed to deal with most of
the boilerplate in implementing the full `Processor` API.

The first line of convenience are the `tryProcessN()` methods which
receive one item at a time, thus eliminating the need to write a
suspendable loop over the input items. There is a separate method
specialized for each edge from 0 to 4 (`tryProcess0`..`tryProcess4`) and
a catch-all method `tryProcessAny(ordinal, item)`. If the processor
doesn't need to distinguish between the inbound edges, the latter method
is a good match; otherwise, it is simpler to implement one or more
of the ordinal-specific methods. The catch-all method is also the only
way to access inbound edges beyond ordinal 4, but such cases are very
rare in practice.

A major complication arises from the requirement to observe the outbox
limits during a single processing step. If the processor emits many
items per step, the loop doing this must support being suspended at any
point and resumed later. This need arises in two typical cases:

- when a single input item maps to many output items,
- when the processor performs an accumulating operation and emits its
results in the final step.

`AbstractProcessor` provides the method `emitFromTraverser` to support
the latter and there is additional support for the former with the
nested class `FlatMapper`. These work with the `Traverser` abstraction
to cooperatively emit a user-provided sequence of items.

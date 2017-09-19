`AggregateOperation` is a holder of five functional primitives Jet uses
to evaluate an aggregate function over a set of data items:

- `create` a new accumulator object.
- `accumulate` the data of an item by mutating the accumulator's state.
- `combine` the contents of the right-hand accumulator into the
left-hand one.
- `deduct` the contents of the right-hand accumulator from the left-hand
one (undo the effects of `combine`).
- `finish` accumulation by transforming the accumulator object into the
final result.

If you are familiar with `java.util.stream.Collector`, you may get a
déjà vu here: except for some naming differences, `AggregateOperation`
is a very similar object. The devil is in the detail, however, and these
primitives are tailor-made for Jet's aggregation style. We assume that
both accumulation and combining are destructive operations, mutating the
left-hand operand. Mutability means lower GC pressure and we always use
it in our implementations. We also define the `deduct` primitive, absent
from `Collector` and specifically targetted at the optimization of
sliding window aggregation.

As a simple example we can define an aggregate operation that collects
items to a set:

```java
AggregateOperation.of(
        HashSet::new,
        Set::add,
        Set::addAll,
        Set::removeAll,
        Set::toString
);
```

`of()` is a factory method that takes the primitives in the order we
gave above. Let's have a brief look at this definition. Our accumulator
object is a `HashSet` instance. We accumulate an item with `add()`; we
combine it with another accumulator with `addAll()`; we undo this
operation with `removeAll()`. Our final output is a string
representation of the set.

This example was easy and familiar because `Set` is a mutable
abstraction so the fit is natural. It gets less natural when your
accumulated value is something the JDK doesn't provide in mutable form.
If you want to accumulate a `long` value, you'll need a mutable
container object for it. We do provide convenience that will create an
`AggregateOperation` from immutable reduction primitives, so you could
use that:

```java
AggregateOperations.reducing(
        0L,
        (Long x) -> x,
        (sum1, sum2) -> sum1 + sum2,
        (sum1, sum2) -> sum1 - sum2
);
```

Under the hood Jet will instantiate a mutable holder that keeps a
reference to the immutable objects returned from the user-supplied
primitives. `reducing()` takes these arguments: 

1. the "empty" accumulated value
2. the function that takes a stream item and computes its accumulated
value
3. the `combine` primitive
4. the `deduct` primitive

Note that `combine` and `deduct` here are pure functions acting on
immutable arguments and have appropriately different lambda shapes than
those in `AggregateOperation`.

While simple, the above definition of summing will create a lot of
garbage `Long` objects. Jet's own summing operation looks like this:

```java
return AggregateOperation.of(
        LongAccumulator::new,
        (a, item) -> a.addExact(mapToLongF.applyAsLong(item)),
        LongAccumulator::addExact,
        LongAccumulator::subtractExact,
        LongAccumulator::get
);
```

Instead of one immutable `Long` per input item we create just a single
`LongAccumulator` instance for the whole operation. There's no
intermediate step of first computing the accumulated value of an item
and then combining it with the running state (this would force us to
create an object); here the `accumulate` primitive takes the whole item
and works it out internally how to update the accumulator.
`LongAccumulator` declares methods for "exact" addition/subtraction
(result is checked for integer overflow) and we use them here,
preferring fail-fast behavior to emitting incorrect results.

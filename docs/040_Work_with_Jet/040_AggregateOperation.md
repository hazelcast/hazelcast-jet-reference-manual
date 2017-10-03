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

The most important primitives are `accumulate` and `finish`: they
specify your business logic in the narrow sense. `create`, `combine` and
`deduct` give Jet the support it needs to manage the accumulators.
`deduct` is the most specialized one: it's only used in sliding
window aggregation and even there it's optional. However, its presence
makes the computation significantly cheaper.

Let's see how this works on a basic example: counting the items. We need
a mutable object that holds the count. Jet's library contains the
`com.hazelcast.jet.accumulator` package with some object designed to be
used as accumulators and one of them is `LongAccumulator`. Our
`accumulate` primitive would be `(longAcc, x) -> longAcc.add(1)` and,
since we want the standard `Long` as the aggregation result, we can
define the `finish` primitive as `LongAccumulator::get`.

Now we have to define the other three primitives to match our main
logic. For `create` we just refer to the constructor:
`LongAccumulator::new`. Combining means adding the value of the
right-hand accumulator to the left-hand one: `(acc1, acc2) ->
acc1.add(acc2.get())` or, since `LongAccumulator` has a convenience
method for this, just `LongAccumulator::add`. Deducting must undo the
effect of a previous `combine`: `(acc1, acc2) -> acc1.subtract(acc2)` or
just `LongAccumulator::subtract`.

All put together, we can define our counting operation as follows:

```java
AggregateOperation1<Object, LongAccumulator, Long> aggrOp = AggregateOperation
    .withCreate(LongAccumulator::new)
    .andAccumulate((acc, x) -> acc.add(1))
    .andCombine(LongAccumulator::add)
    .andDeduct(LongAccumulator::subtract)
    .andFinish(LongAccumulator::get);
```

Let's stop for a second to look at the type we got:
`AggregateOperation1<Object, LongAccumulator, Long>`. As opposed to the
general `AggregateOperation`, `AggregateOperation1` statically encodes
the fact that it accepts only one input stream, for example in a
`groupBy` transform. In an
[earlier section](Build_Your_Computation_Pipeline#page_coGroup)
we said you can co-group two or three streams with full type safety, by
calling `andAccumulate0()`, `andAccumulate1()`, and `andAccumulate2()`
on the aggregate operation builder object. In such cases you'd get
`AggregateOperation2` or `AggregateOperation3` as the static type. If
you use the co-group builder object, then you'll construct the aggregate
operation by calling `andAccumulate(tag, accFn)` with all the tags you
got from the co-group builder. In that case the static type will be just
`AggrgegateOperation`.

If you are familiar with `java.util.stream.Collector`, you may recognize
some similarities. It is also a holder of functional primitives to
perform an aggregate operation, but there are several important
differences:

- it supports only one input stream
- it doesn't define the `deduct` primitive, which is important for
  the sliding window computation
- its `combiner` primitive is defined as potentially "destructive" to
  both accumulators passed to it so the engine can't reuse them


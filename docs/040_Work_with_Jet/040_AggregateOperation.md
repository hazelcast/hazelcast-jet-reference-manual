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
`com.hazelcast.jet.accumulator` package with objects designed to be used
as accumulators and one of them is `LongAccumulator`. Using it we can
express our `accumulate` primitive as `(longAcc, x) -> longAcc.add(1)`.
Since we want the standard `Long` as the aggregation result, we can
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
`AggregateOperation1<Object, LongAccumulator, Long>`. Its type
parameters are:
1. `Object`: the type of the input item (since we just count them, we
   can take any object here)
2. `LongAccumulator`: the type of the accumulator
3. `Long`: the type of the result

If you compare this signature to that of the general
`AggregateOperation`, you'll see that one only captures the latter two,
so it lacks type safety with respect to the input type. This is because
it can be constructed to work with an arbitrary number of input streams.
`AggregateOperation1` is a specialization that strictly works with one
input stream and captures its static type. It is used in the `groupBy`
transform. In an
[earlier section](Build_Your_Computation_Pipeline#page_coGroup)
we mentioned that you can co-group two or three streams with full type
safety. Let's study a similar example where we're interested in the
behavior of users in an online shop application. We want to compare the
number of product page visits vs. number of items added to the shopping
cart vs. number of purchases made by each user. This data is dispersed
among separate datasets: `PageVisit`, `AddToCart` and `Payment`. We can
perform a co-group transform with the following aggregate operation:

```java
AggregateOperation3<PageVisit, AddToCart, Payment, LongAccumulator[], long[]> aggrOp = 
        AggregateOperation
                .withCreate(() -> Stream.generate(LongAccumulator::new)
                                        .limit(3)
                                        .toArray(LongAccumulator[]::new))
                .<PageVisit>andAccumulate0((accs, x) -> accs[0].add(1))
                .<AddToCart>andAccumulate1((accs, x) -> accs[1].add(1))
                .<Payment>andAccumulate2((accs, x) -> accs[2].add(1))
                .andCombine((accs1, accs2) -> IntStream.range(0, 2)
                                                       .forEach(i -> accs1[i].add(accs2[i])))
                .andFinish(accs -> Stream.of(accs)
                                         .mapToLong(LongAccumulator::get)
                                 .toArray());
```

Note how we got an `AggregateOperation3` and how it captured each input
type. When we use it as an argument to a co-group transform, the
compiler will ensure that the `ComputeStage`s we attach it to have the
correct type and are in the correct order.

On the other hand, if you use the co-group builder object, you'll
construct the aggregate operation by calling `andAccumulate(tag, accFn)`
with all the tags you got from the co-group builder, and the static type
will be just `AggregateOperation`. The compiler won't be able to match
up the inputs to their treatment in the aggregate operation.

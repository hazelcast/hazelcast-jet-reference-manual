The single most important kind of processing Jet does is aggregation. In
general it is a transformation of a set of input values into a single
output value. The function that does this transformation is called the
"aggregate function". A basic example is `sum` applied to a set of
integer numbers, but the result can also be a complex value, for example
a list of all the input items.

Jet exposes an abstraction, called
[`AggregateOperation`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/aggregate/AggregateOperation.html),
that allows you to plug your own aggregate function into Hazelcast Jet.
Since Jet does the aggregation in a parallelized and distributed way,
you can't simply supply a piece of Java code that does it; we need you
to break it down into several smaller pieces that fit into Jet's
processing engine.

The ability to compute the aggregate function in parallel comes at a
cost: Jet must be able to give a slice of the total data set to each
processing unit and then combine the partial results from all the units.
The combining step is crucial: it will only make sense if we're
combining the partial results of a _commutative associative_ function
(CA for short). On the example of `sum` this is trivial: we know from
elementary school that `+` is a CA operation. If you have a stream of
numbers: `{17, 37, 5, 11, 42}`, you can sum up `{17, 5}` separately from
`{42, 11, 37}` and then combine the partial sums (also note the
reordering of the elements).

Something just slightly more complex, like `average`, doesn't by itself
have this property, however if you add one more ingredient, the `finish`
function, you can express it easily. Jet allows you to first compute
some CA function, whose partial results can be combined, and then at the
very end apply the `finish` function on the fully combined result. To
compute the `average`, your CA function will output a pair of `(sum,
count)`. Two such pairs are trivial to combine by summing each
component. The `finish` function will be `sum / count`.

In addition to the mathematical side, there is also the practical one:
you have to provide Jet with a specific mutable object, called the
`accumulator`, which will keep the "running score" of the operation in
progress. For the `average` example, it would be something like

```java
public class AvgAccumulator {
    private long sum;
    private long count;
    
    public void accumulate(long value) {
        sum += value;
        count++;
    }
    
    public void combine(AvgAccumulator that) {
        this.sum += that.sum;
        this.count += that.sum;
    }
    
    public double finish() {
        return (double) sum / count;
    }
    
    public long getSum() { 
        return sum; 
    }
    
    public long getCount() { 
        return count; 
    }
}
```

Instead of requiring you to write a complete class from scratch, Jet
instead requires you to provide a set of five functions (we call them
"primitives") that achieve the above process. Therefore our
`AggregateOperation` is actually a holder of five primitives:

- `create` a new accumulator object.
- `accumulate` the data of an item by mutating the accumulator's state.
- `combine` the contents of the right-hand accumulator into the
left-hand one.
- `deduct` the contents of the right-hand accumulator from the left-hand
one (undo the effects of `combine`).
- `finish` accumulation by transforming the accumulator object into the
final result.

So far we mentioned all of these except for `deduct`. This one is
optional and Jet can manage without it, but if you are computing a
sliding window over an infinite stream, this primitive can give a
significant performance boost because it allows Jet to reuse the results
of the previous calculations.

Let's see how this works on a basic example: counting the items. We need
a mutable object that holds the count. Jet's library contains the
[`com.hazelcast.jet.accumulator`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/accumulator/package-summary.html)
package with objects designed to be used as accumulators and one of them
is 
[`LongAccumulator`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/accumulator/LongAccumulator.html).
Using it we can express our `accumulate` primitive as `(longAcc, x) ->
longAcc.add(1)`. Since we want the standard `Long` as the aggregation
result, we can define the `finish` primitive as `LongAccumulator::get`.

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
we mentioned that you can also co-group two or three streams with full
type safety. Let's study an example where we're interested in the
behavior of users in an online shop application. We want to gather the
following statistics for each user:

1. total load time of the visited product pages
2. quantity of items added to the shopping cart
3. amount payed for bought items

This data is dispersed among separate datasets: `PageVisit`, `AddToCart`
and `Payment`. We can perform a co-group transform with the following
aggregate operation:

```java
AggregateOperation3<PageVisit, AddToCart, Payment, LongAccumulator[], long[]> aggrOp =
    AggregateOperation
        .withCreate(() -> Stream.generate(LongAccumulator::new)
                                .limit(3)
                                .toArray(LongAccumulator[]::new))
        .<PageVisit>andAccumulate0((accs, pv) -> accs[0].add(pv.loadTime()))
        .<AddToCart>andAccumulate1((accs, atc) -> accs[1].add(atc.quantity()))
        .<Payment>andAccumulate2((accs, pm) -> accs[2].add(pm.amount()))
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

[TOC]

## The Shape of a Pipeline

The general shape of any data processing pipeline is `drawFromSource ->
transform -> drainToSink` and the natural way to build it is from source
to sink. The 
[Pipeline](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Pipeline.html)
API follows this pattern. For example,

```java
Pipeline p = Pipeline.create();
p.drawFrom(Sources.<String>readList("input"))
 .map(String::toUpperCase)
 .drainTo(Sinks.writeList("result");
```

In each step, such as `drawFrom` or `drainTo`, you create a pipeline
_stage_. The stage resulting from a `drainTo` operation is called a
_sink stage_ and you can't attach more stages to it. All others are
called _compute stages_ and expect you to attach stages to them.

In a more complex scenario you'll have several sources, each starting
its own pipeline branch. Then you can merge them in a multi-input
transformation such as co-grouping:

```java
Pipeline p = Pipeline.create();
ComputeStage<String> src1 = p.drawFrom(Sources.readList("src1"));
ComputeStage<String> src2 = p.drawFrom(Sources.readList("src2"));
src1.coGroup(wholeItem(), src2, wholeItem(), counting2())
    .drainTo(Sinks.writeMap("result"));
```

For further details on `coGroup` please refer to the [dedicated
section](#page_coGroup) below.

Symmetrically, the output of a stage can be sent to more than one
destination:

```java
Pipeline p = Pipeline.create();
ComputeStage<String> src = p.drawFrom(Sources.readList("src"));
src.map(String::toUpperCase)
   .drainTo(Sinks.writeList("uppercase"));
src.map(String::toLowerCase)
   .drainTo(Sinks.writeList("lowercase"));
```


## Choose Your Data Sources and Sinks

Hazelcast Jet has support for these data sources and sinks:

- Hazelcast `IMap` and `ICache`, both as a batch source of just their
contents and their event journal as an infinite source
- Hazelcast `IList` (batch)
- Hadoop Distributed File System (HDFS) (batch)
- Kafka topic (infinite stream)
- TCP socket (infinite stream)
- a directory on the filesystem, both as a batch source of the current
  file contents and an infinite source of append events to the files

You can access most of them via the
[`Sources`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sources.html)
and
[`Sinks`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sinks.html)
utility classes. 
[Kafka](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/KafkaSources.html)
and
[HDFS](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/HdfsSources.html)
connectors are in their separate modules.

There's a [dedicated section](Source_and_Sink_Connectors) that discusses
the topic of data sources and sinks in more detail.

## Compose the Pipeline Transforms

The simplest kind of transformation is one that can be done on each item
individually and independent of other items. The major examples are

[`map`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#map-com.hazelcast.jet.function.DistributedFunction-),
[`filter`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#filter-com.hazelcast.jet.function.DistributedPredicate-)
and
[`flatMap`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#flatMap-com.hazelcast.jet.function.DistributedFunction-).
We already saw them in use in the previous examples. `map` transforms
each item to another item; `filter` discards items that don't match its
predicate; and `flatMap` transforms each item into zero or more output
items.

### groupBy

Stepping up from the simplest transforms we come to
[`groupBy`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#groupBy-com.hazelcast.jet.function.DistributedFunction-com.hazelcast.jet.aggregate.AggregateOperation1-),
the quintessential finite stream transform. It groups the data items by
a key computed for each item and performs an aggregate operation over
all the items in a group. The output of this transform is one
aggregation result per distinct grouping key. We saw this one used in
the introductory Hello World code with a word count pipeline:

```java
Pipeline p = Pipeline.create();
p.drawFrom(Sources.<String>readList("text"))
 .flatMap(word -> traverseArray(word.split("\\W+")))
 .groupBy(wholeItem(), counting());
```

Let's take a moment to analyze the last line. The `groupBy()` method
takes two parameters: the function to compute the grouping key and the
aggregate operation. In this case the key function is a trivial identity
because we use the word itself as the grouping key and the definition of
the aggregate operation hides behind the `counting()` method call. This
is a static method in our
[`AggregateOperations`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/aggregate/AggregateOperations.html)
utility class and its simplified definition would look like this:

```java
AggregateOperation
    .withCreate(LongAccumulator::new)
    .<String>andAccumulate((count, item) -> count.add(1))
    .andCombine(LongAccumulator::add)
    .andFinish(LongAccumulator::get);
```

This expression constructs an aggregate operation from its primitives:
`create`, `accumulate`, `combine`, and `finish`. They are similar to,
but also different from Java Streams'
[`Collector`](https://docs.oracle.com/javase/9/docs/api/java/util/stream/Collector.html).
The central primitive is `accumulate`: it contains the main business 
logic of our aggregation. In this example it simply increments the count
in the accumulator object. The other primitives must be specified to
allow Jet's fully generic aggregation engine to implement the operation.

The `AggregateOperations` class contains quite a few ready-made
aggregate operations, but you should be able to specify your custom ones
without too much trouble. You should study the Javadoc of
[`AggregateOperation`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/aggregate/AggregateOperation.html)
if you need to write one.

A more complex variety of pipeline transforms are those that merge
several input stages into a single resulting stage. In Hazelcast Jet
there are two such transforms of special interest: `coGroup` and `hashJoin`.

### coGroup

[`coGroup`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#coGroup-com.hazelcast.jet.function.DistributedFunction-com.hazelcast.jet.ComputeStage-com.hazelcast.jet.function.DistributedFunction-com.hazelcast.jet.aggregate.AggregateOperation2-)
is a generalization of `groupBy` to more than one contributing
data stream. Instead of a single `accumulate` primitive you provide one
for each input stream so the operation can discriminate between them. In
SQL terms it can be interpreted as JOIN coupled with GROUP BY. The JOIN
condition is constrained to matching on the grouping key.

Here is the example we already used earlier on this page:

```java
Pipeline p = Pipeline.create();
ComputeStage<String> src1 = p.drawFrom(Sources.readList("src1"));
ComputeStage<String> src2 = p.drawFrom(Sources.readList("src2"));
ComputeStage<Tuple2<String, Long>> coGrouped =
        src1.coGroup(wholeItem(), src2, wholeItem(), counting2());
```

These are the arguments:

1. `wholeItem()`: the key extractor function for `this` stage's items
2. `src2`: the other stage to co-group with this one
3. `wholeItem()`: the key extractor function for `src2` items
4. `counting2()`: the aggregate operation

`counting2()` is a factory method returning a 2-way aggregate operation
which may be defined as follows:

```java
private static AggregateOperation2<Object, Object, LongAccumulator, Long> counting2() {
    return AggregateOperation
            .withCreate(LongAccumulator::new)
            .andAccumulate0((count, item) -> count.add(1))
            .andAccumulate1((count, item) -> count.add(10))
            .andCombine(LongAccumulator::add)
            .andFinish(LongAccumulator::get);
}
```

This demonstrates the individual treatment of input streams: stream 1 is
weighted so that each of its items is worth ten items from stream 0.

#### coGroup Builder

If you need to co-group more than three streams, you'll have to use the
[co-group builder](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#coGroupBuilder-com.hazelcast.jet.function.DistributedFunction-)
object. For example, your goal may be correlating events coming from
different systems, where all the systems serve the same user base. In an
online store you may have separate streams for product page visits,
adding to shopping cart, payments, and deliveries. You want to correlate
all the events associated with the same user. The example below
calculates statistics per category for each user:

```java
Pipeline p = Pipeline.create();
ComputeStage<PageVisit> pageVisit = p.drawFrom(readList("pageVisit"));
ComputeStage<AddToCart> addToCart = p.drawFrom(readList("addToCart"));
ComputeStage<Payment> payment = p.drawFrom(readList("payment"));
ComputeStage<Delivery> delivery = p.drawFrom(readList("delivery"));

CoGroupBuilder<Long, PageVisit> b = pageVisit.coGroupBuilder(PageVisit::userId);
Tag<PageVisit> pvTag = b.tag0();
Tag<AddToCart> atcTag = b.add(addToCart, AddToCart::userId);
Tag<Payment> pmtTag = b.add(payment, Payment::userId);
Tag<Delivery> delTag = b.add(delivery, Delivery::userId);

ComputeStage<Tuple2<Long, long[]>> coGrouped = b.build(AggregateOperation
        .withCreate(() -> Stream.generate(LongAccumulator::new)
                                .limit(4)
                                .toArray(LongAccumulator[]::new))
        .andAccumulate(pvTag, (accs, pv) -> accs[0].add(pv.loadTime()))
        .andAccumulate(atcTag, (accs, atc) -> accs[1].add(atc.quantity()))
        .andAccumulate(pmtTag, (accs, pm) -> accs[2].add(pm.amount()))
        .andAccumulate(delTag, (accs, d) -> accs[3].add(d.days()))
        .andCombine((accs1, accs2) -> IntStream.range(0, 3)
                                               .forEach(i -> accs1[i].add(accs2[i])))
        .andFinish(accs -> Stream.of(accs)
                                 .mapToLong(LongAccumulator::get)
                                 .toArray())
);
```

### hashJoin

[`hashJoin`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#hashJoin-com.hazelcast.jet.ComputeStage-com.hazelcast.jet.JoinClause-com.hazelcast.jet.ComputeStage-com.hazelcast.jet.JoinClause-)
is a specialization of a general "join" operation, optimized for the use
case of _data enrichment_. In this scenario there is a single,
potentially infinite data stream (the _primary_ stream), that goes
through a mapping transformation which attaches to each item some more
items it found by hashtable lookup. The hashtables have been populated
from all the other streams (the _enriching_ streams) before the
consumption of the primary stream started.

For each enriching stream you can specify a pair of key-extracting
functions: one for the enriching item and one for the primary item. This
means that you can define a different join key for each of the enriching
streams. The following example shows a three-way hash-join between the
primary stream of stock trade events and two enriching streams:
_products_ and _brokers_.

```java
Pipeline p = Pipeline.create();

// The primary stream: trades
ComputeStage<Trade> trades = p.drawFrom(Sources.<Trade>readList("trades"));

// The enriching streams: products and brokers
ComputeStage<Entry<Integer, Product>> prodEntries =
        p.drawFrom(Sources.<Integer, Product>readMap("products"));
ComputeStage<Entry<Integer, Broker>> brokEntries =
        p.drawFrom(Sources.<Integer, Broker>readMap("brokers"));

// Join the trade stream with the product and broker streams
ComputeStage<Tuple3<Trade, Product, Broker>> joined = trades.hashJoin(
        prodEntries, joinMapEntries(Trade::productId),
        brokEntries, joinMapEntries(Trade::brokerId)
);
```

Products are joined on `Trade.productId` and brokers on
`Trade.brokerId`. `joinMapEntries()` returns a `JoinClause`, which is a
holder of the three functions that specify how to perform a join: 

1. the key extractor for the primary stream's item
2. the key extractor for the enriching stream's item
3. the projection function that transforms the enriching stream's item
into the item that will be used for enrichment. 

Typically the enriching streams will be `Map.Entry`s coming from a 
key-value store, but you want just the entry value to appear as the
enriching item. In that case you'll specify `Map.Entry::getValue` as the
projection function. This is what `joinMapEntries()` does for you. It
takes just one function, primary stream's key extractor, and fills in
`Entry::getKey` and `Entry::getValue` for the enriching stream key
extractor and the projection function, respectively.

In the interest of performance the entire enriching dataset resides on
each cluster member. That's why this operation is also known as a
_replicated_ join. This is something to keep in mind when estimating
the RAM requirements for a hash-join operation.

#### hashJoin Builder

You can hash-join a stream with up to two enriching streams using the
API we demonstrated above. If you have more than two enriching streams,
you'll use the
[hash-join builder](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#hashJoinBuilder--).
For example, you may want to enrich a trade with its associated product,
broker, and market:

```java
Pipeline p = Pipeline.create();

// The primary stream: trades
ComputeStage<Trade> trades = p.drawFrom(Sources.<Trade>readList("trades"));

// The enriching streams: products and brokers
ComputeStage<Entry<Integer, Product>> prodEntries =
        p.drawFrom(Sources.<Integer, Product>readMap("products"));
ComputeStage<Entry<Integer, Broker>> brokEntries =
        p.drawFrom(Sources.<Integer, Broker>readMap("brokers"));
ComputeStage<Entry<Integer, Market>> marketEntries =
        p.drawFrom(Sources.<Integer, Market>readMap("markets"));

HashJoinBuilder<Trade> b = trades.hashJoinBuilder();
Tag<Product> prodTag = b.add(prodEntries, joinMapEntries(Trade::productId));
Tag<Broker> brokTag = b.add(brokEntries, joinMapEntries(Trade::brokerId));
Tag<Market> marketTag = b.add(marketEntries, joinMapEntries(Trade::marketId));
ComputeStage<Tuple2<Trade, ItemsByTag>> joined = b.build();
```

The data type on the hash-joined stage is `Tuple2<Trade, ItemsByTag>`.
The next snippet shows how to use it to access the primary and enriching
items:

```java
ComputeStage<String> mapped = joined.map(
        (Tuple2<Trade, ItemsByTag> t) -> {
            Trade trade = t.f0();
            ItemsByTag ibt = t.f1();
            Product product = ibt.get(prodTag);
            Broker broker = ibt.get(brokTag);
            Market market = ibt.get(marketTag);
            return trade + ": " + product + ", " + broker + ", " + market;
        });
```


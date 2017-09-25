[TOC]

## Start Jet and Submit Jobs to it

To create a Jet cluster, we simply start some Jet instances. Normally
these would be started on separate machines, but for simple practice
we can use the same JVM for both instances. Even though they are in the
same JVM, they'll communicate over the network interface.

```java
public class WordCount {
    public static void main(String[] args) {
        JetInstance jet = Jet.newJetInstance();
        Jet.newJetInstance();
    }
}
```

These two instances should automatically discover each other using IP
multicast and form a cluster. You should see a log output similar to the
following:

```
Members [2] {
  Member [10.0.1.3]:5701 - f1e30062-e87e-4e97-83bc-6b4756ef6ea3
  Member [10.0.1.3]:5702 - d7b66a8c-5bc1-4476-a528-795a8a2d9d97 this
}
```

This means the members successfully formed a cluster. Since the Jet
instances start their own threads, it is important to explicitly shut
them down at the end of your program; otherwise the Java process will
remain alive after the `main()` method completes:

```java
public class WordCount {
    public static void main(String[] args) {
        try {
            JetInstance jet = Jet.newJetInstance();
            Jet.newJetInstance();

            ... work with Jet ...

        } finally {
            Jet.shutdownAll();
        }
    }
}
```

This is how you submit a Jet pipeline for execution:

```java
pipeline.execute(jet).get();
```

Alternatively, you can submit a Core API DAG:

```java
jet.newJob(dag).execute().get();
```

Code samples with
[the Core API DAG](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/core-api/batch/wordcount-core-api/src/main/java/refman/WordCountRefMan.java) 
and
[the pipeline](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/wordcount-pipeline-api/src/main/java/WordCountPipelineApi.java)
are available at our Code Samples repo.

## Build a Pipeline

The general shape of any data processing pipeline is `drawFromSource -> transform -> drainToSink` and the natural way to build it is from source
to sink. The Pipeline API follows that pattern. For example,

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

- Hazelcast `IMap`
- Hazelcast `ICache`
- Hazelcast `IList`
- Hadoop Distributed File System (HDFS)
- Kafka topic
- TCP socket
- a directory on the filesystem

You can access them via the `Sources` and `Sinks` utility classes.

## Basic Pipeline Transforms

The simplest kind of transformation is one that can be done on each item
individually and independent of other items. The major examples are
`map`, `filter` and `flatMap`. We already saw them in use in the
previous examples. `map` transforms each item to another item; `filter`
discards items that don't match its predicate; and `flatMap` transforms
each item into zero or more output items.

### groupBy

Stepping up from the simplest transforms we come to `groupBy`: it groups the data items by a key computed for each item and performs an aggregate operation over all the items in a group. The output of this transform is one aggregation result per distinct grouping key. We saw this one used in the introductory Hello World code with a word count pipeline:

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
is a static method in our `AggregateOperations` utility class and its
simplified definition would look much like `counting2()` given above,
but for just one input stream:

```java
AggregateOperation
    .withCreate(LongAccumulator::new)
    .<String>andAccumulate((count, item) -> count.add(1))
    .andCombine(LongAccumulator::add)
    .andFinish(LongAccumulator::get);
```

This expression constructs an aggregate operation from its primitives:
`create`, `accumulate`, `combine`, and `finish`. They are similar to,
but also different from Java Streams' `Collector`. The central primitive
is `accumulate`: it contains the main business logic of our aggregation.
In this example it simply increments the count in the accumulator
object. The other primitives must be specified to allow Jet's fully
generic aggregation engine to implement the operation.

The `AggregateOperations` class contains quite a few ready-made
aggregate operations, but you should be able to specify your custom ones
without too much trouble. You should study the Javadoc of
`AggregateOperation` if you need to write one.


## Multi-Input Transforms

A more complex variety of pipeline transforms are those that merge
several input stages into a single resulting stage. In Hazelcast Jet
there are two such transforms of special interest: `coGroup` and `hashJoin`.

### coGroup

`coGroup` is a generalization of `groupBy` to more than one contributing
data stream. Instead of a single `accumulate` primitive you provide one
for each input stream so the operation can discriminate between them.
Here is the example we already used earlier on this page:

```java
Pipeline p = Pipeline.create();
ComputeStage<String> src1 = p.drawFrom(Sources.readList("src1"));
ComputeStage<String> src2 = p.drawFrom(Sources.readList("src2"));
src1.coGroup(wholeItem(), src2, wholeItem(), counting2())
    .drainTo(Sinks.writeMap("result"));
```

These are the arguments:

1. `wholeItem()`: the key extractor function for `this` stage's items
2. `src2`: the other stage to co-group with this one
3. `wholeItem()`: the key extractor function for `src2` items
4. `counting2()`: the aggregate operation

`counting2()` is a factory method returning a 2-way aggregate operation
which may be defined as follows:

```java
private static AggregateOperation2<String, String, LongAccumulator, Long> counting2() {
    return AggregateOperation
            .withCreate(LongAccumulator::new)
            .<String>andAccumulate0((count, item) -> count.add(1))
            .<String>andAccumulate1((count, item) -> count.add(10))
            .andCombine(LongAccumulator::add)
            .andFinish(LongAccumulator::get);
}
```

This demonstrates the individual treatment of input streams: stream 1 is
weighted so that each of its items is worth ten items from stream 0.

If you need to co-group more than three streams, you'll have to use the
co-group builder object. For example, your goal may be correlating
events coming from different systems, where all the systems serve the
same user base. In an online store you may have separate streams for
product page visits, adding to shopping cart, payments and deliveries.
You want to see all the actions by user.

```java
CoGroupBuilder<Integer, Trade> builder = 
    trades.coGroupBuilder(Trade::classId);
```

### hashJoin

`hashJoin` is a specialization of a general "join" operation, optimized
for the use case of _data enrichment_. In this scenario there is a
single, potentially infinite data stream (the _primary_ stream), that
goes through a mapping transformation which attaches to each item some
more items it found by hashtable lookup. The hashtables have been
populated from all the other streams (the _enriching_ streams) before
the consumption of the primary stream started.

For each enriching stream you can specify a pair of key-extracting
functions: one for the enriching item and one for the primary item. This
means that you can define a different join key for each of the enriching
streams. The following example shows a three-way hash-join between the
primary stream of stock trade events and two enriching streams:
_products_ and _brokers_.

```java
Pipeline p = Pipeline.create();

// The primary stream: trades
ComputeStage<Trade> trades = p.drawFrom(Sources.<Trade>readList(TRADES));

// The enriching streams: products and brokers
ComputeStage<Entry<Integer, Product>> prodEntries = 
    p.drawFrom(Sources.<Integer, Product>readMap(PRODUCTS));
ComputeStage<Entry<Integer, Broker>> brokEntries = 
    p.drawFrom(Sources.<Integer, Broker>readMap(BROKERS));

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

Typically the enriching streams will be `Map.Entry`s coming from a key-value store, but you want just the entry value to appear as the
enriching item. In that case you'll specify `Map.Entry::getValue` as the
projection function. This is what `joinMapEntries()` does for you. It
takes just one function, primary stream's key extractor, and fills in
`Entry::getKey` and `Entry::getValue` for the enriching stream key
extractor and the projection function, respectively.

In the interest of performance the entire enriching dataset resides on
each cluster member. That's why this operation is also known as a
_replicated_ join. This is something to keep in mind when estimating
the RAM requirements for a hash-join operation.


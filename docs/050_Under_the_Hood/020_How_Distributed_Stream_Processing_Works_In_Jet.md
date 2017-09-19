Continuing our story we shall now move on to infinite stream processing.
The major challenge in batch jobs was properly
parallelizing/distributing a "group by key" operation. To solve it we
introduced the idea of partitioning the data based on a formula that
takes just the grouping key as input and can be computed independently
on any member, always yielding the same result. In the context of
infinite stream processing we have the same concern and solve it with
the same means, but we also face some new challenges.

## The Importance of "Right Now"

In batch jobs the data we process represents a point-in-time snapshot of
our state of knowledge (for example, warehouse inventory where
individual data items represent items on stock). We can recapitulate
each business day by setting up regular snapshots and batch jobs.
However, there is more value hiding in the freshest data &mdash; our
business can win by reacting to minute-old or even second-old updates.
To get there we must make a shift from the finite to the infinite: from
the snapshot to a continuous influx of events that update our state of
knowledge. For example, an event could pop up in our stream every time
an item is checked in or out of the warehouse.

A single word that captures the above story is _latency_: we want our
system to minimize the latency from observing an event to acting upon
it.

## The Sliding Time Window

We saw how the grouping processor keeps accumulating the data until the
input is exhausted and then emits the final result. In our new context
the input will never be exhausted, so we'll need some new formalization
of what it is that we want to compute. One useful concept is a _sliding
window_ over our stream. It will compute some aggregate value, like
average or linear trend, over a period of given size extending from now
into the recent past. This is the one we'll use in our upcoming example.

## Time Ordering

Usually the time of observing the event is written as a data field in
the stream item. There is no guarantee that items will occur in the
stream ordered by the value of that field; in fact in many cases it is
certain that they won't. Consider events gathered from users of a mobile
app: for all kinds of reasons the items will arrive to our datacenter
out of order, even with significant delays due to connectivity issues.

This complicates the definition of the sliding window: if we had an
ordered stream, we could simply keep a queue of recent items, evicting
those whose timestamp is a defined amount behind the newest item's
timestamp. To achieve the same with a disordered stream, we have to (at
least partially) sort the items by timestamp, which is computationally
expensive. Furthermore, the latest received item no longer coincides
with the notion of the "most recent event". A previously received item
may have a higher timestamp value. We can't just keep a sliding window's
worth of items and evict everything older; we have to wait some more
time for the data to "settle down" before acting upon it.

## Watermark

To solve these issues we introduce the concept of the _watermark_.
It is a timestamped item inserted into the stream that tells us "from
this point on there will be no more items with timestamp less than
this". Computing the watermark is a matter of educated guessing and
there is always a chance some items will arrive that violate its claim.
If we do observe such an offending item, we categorize it as "too late"
and just filter it out.

**Terminology note**: in this and other places you'll notice that we use
the term "watermark" in two distinct, but closely related meanings:

- As a property of a given location in the DAG pipeline: _the current
value of the watermark_.
- As a data item: _a processor received a watermark_.

The watermark can be considered as a "clock telling the event time" as
opposed to the wall-clock time. The processor's watermark value advances
when it receives a watermark item. In this analogy a processor only
receives data about "present" and "future" events.

## Stream Skew

Items arriving out of order aren't our only challenge; modern stream
sources like Kafka are partitioned and distributed so "the stream" is
actually a set of independent substreams, moving on in parallel.
Substantial time difference may arise between events being processed on
each one, but our system must produce coherent output as if there was
only one stream. We meet this challenge by coalescing watermarks: as
the data travels over a partitioned/distributed edge, we make sure the
downstream processor observes the correct watermark value, which is the
least of watermarks received from the contributing substreams.

## The Stream-Processing DAG and Code

For this example we'll build a simple Jet job that monitors trading
events on a stock market, categorizes the events by stock ticker, and
reports the number of trades per time unit (the time window). In terms
of DAG design, not much changes going from batch to streaming. This is
how it looks:

<img alt="Trade monitoring DAG"
     src="../images/stock-exchange-dag.png"
     width="300"/>

We have the same cascade of source, two-stage aggregation, and sink. The
source part consists of `ticker-source` that loads stock names
(tickers) from a Hazelcast IMap and `generate-trades` that retains this
list and randomly generates an infinite stream of trade events. A
separate vertex is inserting watermark items needed by the aggregation
stage and on the sink side there's another mapping vertex,
`format-output`, that transforms the window result items into lines of
text. The `sink` vertex writes these lines to a file.

Before we go on, let us point out that in the 0.5 release of Hazelcast
Jet, the Pipeline API is still in infancy and doesn't support stream
processing. Therefore the following example is given only in the Core
API; with the next release we'll be able to present the much simpler
code to do it in the Pipelines API.

If you studied the DAG-building code for the Word Count job, this code
should look generally familiar:

```java
WindowDefinition windowDef = slidingWindowDef(
        SLIDING_WINDOW_LENGTH_MILLIS, SLIDE_STEP_MILLIS);
Vertex tickerSource = dag.newVertex("ticker-source",
        Sources.readMap(TICKER_MAP_NAME));
Vertex generateTrades = dag.newVertex("generate-trades",
        generateTrades(TRADES_PER_SEC_PER_MEMBER));
Vertex insertWatermarks = dag.newVertex("insert-watermarks",
        Processors.insertWatermarks(
                Trade::getTime,
                withFixedLag(MAX_LAG),
                emitByFrame(windowDef)));
Vertex slidingStage1 = dag.newVertex("sliding-stage-1",
        Processors.accumulateByFrame(
                Trade::getTicker,
                Trade::getTime, TimestampKind.EVENT,
                windowDef,
                counting()));
Vertex slidingStage2 = dag.newVertex("sliding-stage-2",
        Processors.combineToSlidingWindow(windowDef, counting()));
Vertex formatOutput = dag.newVertex("format-output",
        formatOutput());
Vertex sink = dag.newVertex("sink",
        Sinks.writeFile(OUTPUT_DIR_NAME));

tickerSource.localParallelism(1);
generateTrades.localParallelism(1);

return dag
        .edge(between(tickerSource, generateTrades)
                .distributed().broadcast())
        .edge(between(generateTrades, insertWatermarks)
                .oneToMany())
        .edge(between(insertWatermarks, slidingStage1)
                .partitioned(Trade::getTicker, HASH_CODE))
        .edge(between(slidingStage1, slidingStage2)
                .partitioned(Entry<String, Long>::getKey, HASH_CODE)
                .distributed())
        .edge(between(slidingStage2, formatOutput)
                .oneToMany())
        .edge(between(formatOutput, sink)
                .oneToMany());
```

The source vertex reads a Hazelcast IMap, just like it did in the word
counting example. Trade generating vertex uses a custom processor that
generates mock trades. It can be reviewed
[here](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/streaming/trade-generator/src/main/java/com/hazelcast/jet/sample/tradegenerator/GenerateTradesP.java).
The implementation of `complete()` is non-trivial, but most of the
complexity just deals with precision timing of events. For simplicity's
sake the processor must be configured with a local parallelism of 1;
generating a precise amount of mock traffic from parallel processors
would take more code and coordination.

The major novelty is the watermark-inserting vertex. It must be added
in front of the windowing vertex and will insert watermark items
according to the configured [policy](/Core_API/WatermarkPolicy).
In this case we use the simplest one, `withFixedLag`, which will make
the watermark lag behind the top observed event timestamp by a fixed
amount. Emission of watermarks is additionally throttled, so that only
one watermark item per frame is emitted. The windowing processors emit
data only when the watermark reaches the next frame, so inserting it
more often than that would be just overhead.

The edge from `insertWatermarks` to `slidingStage1` is partitioned; you
may wonder how that works with watermark items, since

1. their type is different from the "main" stream item type and they
don't have a partitioning key
2. each of them must reach all downstream processors.

It turns out that Jet must treat them as a special case: regardless of
the configured edge type, watermarks are routed using the broadcast
policy.

The stage-1 processor will just forward the watermark it receives,
along with any aggregation results whose emission it triggers, to stage
2.

The full code of this sample is in
[StockExchange.java](
https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/core-api/streaming/stock-exchange/src/main/java/StockExchange.java)
and running it will get an endless stream of data accumulating on the
disk. To spare your filesystem we've limited the execution time to 10
seconds.

Continuing our story from the previous chapter we shall now move on to infinite stream processing. The major challenge in batch jobs was properly parallelizing/distributing a "group by key" operation. To solve it we introduced the idea of partitioning the data based on a formula that takes just the grouping key as input and can be computed independently on any member, always yielding the same result. In the context of infinite stream processing, we have the same concern and solve it with the same means, but we also face some new challenges.

## The Importance of "Right Now"

In batch jobs the data we process represents a point-in-time snapshot of our state of knowledge (for example, warehouse inventory where individual data items represent items on stock). We can recapitulate each business day by setting up regular snapshots and batch jobs. However, there is more value hiding in the freshest data &mdash; our business can win by reacting to minute-old or even second-old updates. To get there we must make a shift from the finite to the infinite: from the snapshot to a continuous influx of events that update our state of knowledge. For example, an event could pop up in our stream every time an item is checked in to or out of the warehouse.

A single word that captures the above story is _latency_: we want our system to minimize the latency from observing an event to acting upon it.


## The Sliding Time Window

We saw how the grouping processor keeps accumulating the data until the input is exhausted and then emits the final result. In our new context the input will never be exhausted, so we'll need some new formalization of what it is that we want to compute. One useful concept is a _sliding window_ over our stream. It will compute some aggregate value, like average or linear trend, over a period of given size extending from now into the recent past. This is the one we'll use in our upcoming example.


## Time Ordering

Usually the time of observing the event is written as a data field in the stream item. There is no guarantee that items will occur in the stream ordered by the value of that field; in fact in many cases it is certain that they won't. Consider events gathered from users of a mobile app: for all kinds of reasons the items will arrive to our datacenter out of order, even with significant delays due to connectivity issues. 

This complicates the definition of the sliding window: if we had an ordered stream, we could simply keep a queue of recent items, evicting those whose timestamp is a defined amount behind the newest item's timestamp. To achieve the same with a disordered stream, we have to sort the items by timestamp, which is computationally expensive. Furthermore, the latest received item no longer coincides with the notion of the "most recent event". A previously received item may have a higher timestamp value. We can't just keep a sliding window's worth of items and evict everything older; we have to wait some more time for the data to "settle down" before acting upon it. 

## Punctuation

To solve these issues we introduce the concept of _stream punctuation_. It is a timestamped item inserted into the stream that tells us "from this point on there will be no more items with timestamp less than this". Computing the punctuation is a matter of educated guessing and there is always a chance some items will arrive that violate its claim. If we do observe such an offending item, we categorize it as "too late" and just filter it out.

In analogy to batch processing, punctuation is like an end-of-stream marker, only in this case it marks the end of a substream. Our reaction to it is analogous as well: we emit the aggregated results for items whose timestamp is less than punctuation.

## Stream Skew

Items arriving out of order aren't our only challenge; modern stream sources like Kafka are partitioned and distributed so "the stream" is actually a set of independent substreams, moving on in parallel. Substantial time difference may arise between events being processed on each one, but our system must produce coherent output as if there was only one stream. We meet this challenge by coalescing punctuation: as the data travels over a partitioned/distributed edge, we make sure the downstream processor observes the correct punctuation, which is the least of punctuations received from the contributing substreams.


## Jet's Approach to Optimized Aggregation

We'll now focus on the computation of the sliding window. We said earlier that the items must be sorted by timestamp as a prerequisite to applying a windowed operation. In practice we don't literally sort them; instead we divide the timestamp axis into _frames_ of equal length and assign each item to its frame. To compute a sliding window, we just take all the frames covered by it and combine them. This means that our sliding window doesn't smoothly slide over the axis, it "hops" in frame-sized steps. This way we provide a configurable tradeoff between the smoothness of sliding and the cost of storage/computation.

Furthermore, we provide support to optimize a very important class of aggregate operations: those that can be expressed in terms of a pair of functions where one is commutative/associative and maintains the running state of computation, and the other one is unrestricted and transforms the running state into the final result. We call the former one _combine_ and the latter one _finish_. Examples include minimum, maximum, average, variance, standard deviation, linear regression, etc. The point of the commutative/associative property is that you can apply the function to the running state of two frames and get the same result as if you applied it to each item in them. This means we don't have to hold on to each individual item: we just maintain one instance of running state per frame.

A slightly narrower class of aggregate operations also supports the inverse of _combine_, which we call _deduct_: it undoes a previous combine (i.e., _deduct_(_combine_(x, y), x) = y). When this is provided, we can compute the entire sliding window in just two fixed steps: _deduct_ the trailing frame and _combine_ the leading frame into the constantly maintained sliding window result.

Note that these techniques are non-restrictive: you can always provide a trivial combining function that just merges sets of individual items, and provide an arbitrary finishing function that acts upon them.

### Example: 30-second Window Sliding by 10 Seconds

The diagram below summarizes the process of constructing a 30-second window which slides by 10 seconds, and computing the number of events that occurred within it. For brevity we label the events as _minutes:seconds_.

1. Throw each event into its "bucket" (the frame whose time interval it belongs to).
2. Instead of keeping the items in the frame, just keep the item count.
3. Combine the frames into three different positions of the sliding window, yielding the final result: the number of events that occurred within the window's timespan.

<img alt="Grouping disordered events by frame and then to sliding window" 
    src="../images/windowing-frames.png"
    width="800"/>

This would be a useful interpretation of the results: "At the time 1:30, the 30-second running average was 8/30 = 0.27 events per second. Over the next 20 seconds it increased to 10/30 = 0.33 events per second."

### Some More Twists

If you review the [AggregateOperation](
https://github.com/hazelcast/hazelcast-jet/blob/master/hazelcast-jet-core/src/main/java/com/hazelcast/jet/AggregateOperation.java)
interface in Jet's library, you'll notice that the story as we presented it doesn't fully match what you see; there are some more twists that we apply, but they are pretty standard:

- `createAccumulatorF` creates a fresh instance of the running state (which we call the _accumulator_), with zero items accumulated.
- `accumulateItemF` has an asymmetric signature, accepting an accumulator on the left and a stream item on the right, and returns the updated accumulator.

It would be equivalent to have just one function: `itemToAccumulatorF`, which would create an accumulator that accounts for just one item; this could then be passed to the combining function. However, this would create more garbage because the single-item accumulator would be thrown right away.

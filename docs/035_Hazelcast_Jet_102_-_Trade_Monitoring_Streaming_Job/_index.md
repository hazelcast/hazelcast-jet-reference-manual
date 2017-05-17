Continuing our story from the previous chapter we shall now move on to infinite stream processing. The major challenge in batch jobs was properly parallelizing/distributing a "group by key" operation. To solve it we introduced the idea of partitioning the data based on a formula that takes just the grouping key as input and can be computed independently on any member, always yielding the same result. In the context of infinite stream processing we have the same concern and solve it with the same means, but we also face some new challenges.

## The importance of "right now"

In batch jobs the data we process represents a point-in-time snapshot of our state of knowledge (for example, warehouse inventory where individual data items represent items on stock). We can recapitulate a business day by setting up regular snapshots and batch jobs. However, there is more value hiding in the freshest data &mdash; our business can win by reacting to minute-old or even second-old updates. To get there we must make a shift from the finite to the infinite: from the snapshot to a continuous influx of individual events that update our state of knowledge. For example, an event could pop up in our stream every time an item is checked in or out of the warehouse.

A single word that captures the above story is _latency_: we want our system to minimize the time from observing an event to being able to act upon it.


## The sliding time window

We saw how the grouping processor keeps accumulating the data until the input is exhausted and then emits the final result. In our new context the input will never be exhausted, so we'll need some new formalization of what it is that we want to compute. One useful concept is a _sliding window_ over our stream. It will compute some aggregate value, like average or linear trend, over a period of given size extending from now into the recent past. This is the one we'll use in our upcoming example.


## Time ordering

Usually the time of observing the event is a data field in the stream item. There is no guarantee that items will occur in the stream ordered by the value of that field; in fact in many cases it is certain that they won't. Consider events gathered from users of a mobile app: for all kinds of reasons the items will arrive to our datacenter out of order, even with significant delays due to connectivity issues. 

This complicates the definition of the sliding window: if we had an ordered stream, we could simply keep a queue of recent items, evicting those whose timestamp is a defined amount behind the newest item's timestamp. To achieve the same with a disordered stream, we have to sort the items by timestamp, which is computationally expensive. Furthermore, the latest received item no longer coincides with the notion of the "most recent event". A previously received item may have a higher timestamp value. We can't just keep a sliding window's worth of items and evict everything older; we have to wait some more time for the data to "settle down" before acting upon it. 

## Punctuation

To solve these issues we introduce the concept of _stream punctuation_. It is a timestamped item inserted into the stream that tells us "from this point on there will be no more items with timestamp less than this". Computing the punctuation is a matter of educated guessing and there is always a chance some items will arrive that violate its claim. If we do observe such an offending item, we categorize it as a "late item" and just filter it out.

Compared to batch processing, punctuation is like an end-of-stream marker, only in this case it marks the end of a substream. Our reaction to it is analogous as well: we emit the aggregated results for items whose timestamp is less than punctuation.


Distributed stream processing has two major variants: finite and
infinite. Before proceeding to the details of pipeline transforms, let's
discuss this difference and some concerns specific to infinite streams.

### Finite aka. Batch Processing

Finite (batch) processing is the simpler variant where you provide one
or more pre-existing datasets and order Jet to mine them for interesting
information. The most important workhorse in this area is the "group and
aggregate" operation: you define a classifying function that computes a
grouping key for each item of the dataset and an aggregate operation
that will be performed on all the items in each group, yielding one
result item per distinct key.

### The Importance of "Right Now"

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

### Windowing

In an infinite stream, the dimension of time is always there.  Consider
a batch job: it may process a dataset labeled "Wednesday", but the
computation itself doesn't have to know this. Its results will be
understood from the outside to be "about Wednesday". An infinite stream,
on the other hand, delivers information about the reality as it is
unfolding, in near-real time, and the computation itself must deal with
time explicitly.

Furthermore, in a batch it is obvious when to stop aggregating and emit
the results: when we have exhausted the whole dataset, but with infinite streams we need a policy on how to select finite chunks whose aggregate
results we are interested in. This is called _windowing_.

An example of a very useful policy is the _sliding time window_: it
aggregates over a period of given size extending from now into the
recent past. As the time moves on, the fresh data displaces the old and
the length of time covered by the window stays the same.

Another popular policy is called the _session window_ and it's used to
detect bursts of continuous activity by correlating events bunched
together on the time axis. In the analogy to a user's session with a
web application, the session window "closes" at the point where two
consecutive events are spaced by more than the defined session timeout.

### Time Ordering

Usually the time of observing an event is explicitly written in the
stream item. There is no guarantee that items will occur in the stream
ordered by the value of that field; in fact in many cases it is certain
that they won't. Consider events gathered from users of a mobile app:
for all kinds of reasons the items will arrive to our datacenter out of
order, even with significant delays due to connectivity issues.

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

### Watermark

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
opposed to the wall-clock time. The processing unit's watermark value advances when it receives a watermark item. In this analogy a processor only receives data about "present" and "future" events.

### Stream Skew

Items arriving out of order aren't our only challenge; modern stream
sources like Kafka are partitioned and distributed so "the stream" is
actually a set of independent substreams, moving on in parallel.
Substantial time difference may arise between events being processed on
each one, but our system must produce coherent output as if there was
only one stream. We meet this challenge by coalescing watermarks: as
the data travels over a partitioned/distributed edge, we make sure the
downstream processor observes the correct watermark value, which is the
least of watermarks received from the contributing substreams.

## Note for Hazelcast Jet version 0.5

Hazelcast Jet's version 0.5 was released with the Pipeline API still under construction. We started from the simple case of batch jobs and we support two major batch operations: (co)group-and-aggregate and data enrichment (hash-joins). The next release will feature a fully developed API that supports infinite streams.

At the Core API level, Jet has had full-fledged infinite stream support since version 0.4, and you can refer to the [Under the Hood](Under_the_Hood) chapter for details on how to create a Core API DAG that does infinite stream processing.

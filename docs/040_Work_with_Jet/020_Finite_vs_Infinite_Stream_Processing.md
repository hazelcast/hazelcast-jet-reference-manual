Distributed stream processing has two major variants: finite and
infinite. Before proceeding to the details of pipeline transforms, let's
discuss this difference and some concerns specific to infinite streams.

## Finite aka. Batch Processing

Finite stream (batch) processing is the simpler variant where you
provide one or more pre-existing datasets and order Jet to mine them for
interesting information. The most important workhorse in this area is
the "group and aggregate" operation: you define a classifying function
that computes a grouping key for each item of the dataset and an
aggregate operation that will be performed on all the items in each
group, yielding one result item per distinct key.

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

## Windowing

In an infinite stream, the dimension of time is always there.  Consider
a batch job: it may process a dataset labeled "Wednesday", but the
computation itself doesn't have to know this. Its results will be
understood from the outside to be "about Wednesday". An infinite stream,
on the other hand, delivers information about the reality as it is
unfolding, in near-real time, and the computation itself must deal with
time explicitly.

Furthermore, in a batch it is obvious when to stop aggregating and emit
the results: when we have exhausted the whole dataset, but with infinite 
streams we need a policy on how to select finite chunks whose aggregate
results we are interested in. This is called _windowing_.

A very basic type of windowing policy is the _tumbling window_, which
splits the data into batches based on event time. Each event gets
assigned to the batch whose time interval covers the event's timestamp.
The result of this is very similar to running a sequence of batch jobs,
one per time interval.

A more useful and powerful policy is the _sliding window_: it aggregates
over a period of given size extending from now into the recent past. As
the time passes, the window (pseudo-)continuously slides forward and the
fresh data gradually displaces the old.

Another popular policy is called the _session window_ and it's used to
detect bursts of activity by correlating events bunched together on the
time axis. In an analogy to a user's session with a web application,
the session window "closes" when the specified session timeout elapses
with no further events.

## Time Ordering and the Watermark

Usually the time of observing an event is explicitly written in the
stream item. There is no guarantee that items will occur in the stream
ordered by the value of that field; in fact in many cases it is certain
that they won't. Consider events gathered from users of a mobile app:
for all kinds of reasons the items will arrive to our datacenter out of
order, even with significant delays due to connectivity issues.

This disorder in the event stream makes it more difficult to formally
specify a rule that tells us when all the data for a given window has
been gathered, allowing us to emit the aggregated result.

To approach these challenges we use the concept of the _watermark_. It
is a timestamped item inserted into the stream that tells us "from this
point on there will be no more items with timestamp less than this".
Unfortunately, we almost never know for sure when such a statement
becomes true and there is always a chance some events will arrive even
later. If we do observe such an offending item, we must categorize it as
"too late" and just filter it out.

Note the tension in defining the "perfect" watermark for a given use
case: it is bad both the more we wait and the less we wait to emit a
given watermark. The more we wait, the higher the latency of getting the
results of the computation; the less we wait, the worse their accuracy
due to missed events.

For the above reasons the policy to compute the watermark is not
hardcoded and you as the user must decide which one to use. Hazelcast
Jet comes with some predefined policies which you can tune with a few
configurable parameters. You can also write your own policy from
scratch.


## Note for Hazelcast Jet version 0.5

Hazelcast Jet's version 0.5 was released with the Pipeline API still
under construction. We started from the simple case of batch jobs and we
support the major batch operation of (co)group-and-aggregate, but still
lack the API to define the windowing and watermark policies. Other,
non-aggregating operations aren't sensitive to the difference between
finite and infinite streams and are ready to use. The major example here
is data enrichment
([hash join](Build_Your_Computation_Pipeline#page_hashJoin)),
which is essentially a mapping stream transformation. We also provide
data sources of infinite streams such as Kafka, TCP sockets, and a
filesystem directory monitored for changes. The next release of Jet will
feature a fully developed API that supports windowed aggregation of
infinite streams.

On the other hand, Jet's core has had full-fledged infinite stream
support since version 0.4. You can refer to the [Under the Hood](Under_the_Hood) chapter for details on how to create a Core API DAG
that does infinite stream aggregation.

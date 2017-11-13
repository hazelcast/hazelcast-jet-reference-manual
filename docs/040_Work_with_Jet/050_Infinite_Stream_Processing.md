[TOC]

Distributed stream processing has two major variants: finite and
infinite. Let's discuss this difference and some concerns specific to
infinite streams.

## Finite aka. Batch Processing

Finite stream (batch) processing is the simpler variant where you
provide one or more pre-existing datasets and order Jet to mine them for
interesting information. The most important workhorse in this area is
the "join, group and aggregate" operation: you define a classifying
function that computes a grouping key for each of the datasets and
an aggregate operation that will be performed on all the items in each
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
the results: when we have exhausted the whole dataset. However, with infinite 
streams we need a policy on how to select finite chunks whose aggregate
results we are interested in. This is called _windowing_. We imagine the
window as a time interval laid over the time axis. A given window
contains only the events that belong to that interval.

A very basic type of window is the _tumbling window_, which can be
imagined to advance by tumbling over each time. There is no overlap
between the successive positions of the window. In other words, it
splits the time-series data into batches delimited by points on the time
axis. The result of this is very similar to running a sequence of batch
jobs, one per time interval.

A more useful and powerful policy is the _sliding window_: instead of
splitting the data at fixed boundaries, it lets it roll in
incrementally, new data gradually displacing the old. The window
(pseudo)continuously slides along the time axis.

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
specify a rule that tells us at which point all the data for a given
window has been gathered, allowing us to emit the aggregated result.

To approach these challenges we use the concept of the 
[_watermark_](https://hazelcast-l337.ci.cloudbees.com/view/Jet/job/Jet-javadoc/javadoc/com/hazelcast/jet/core/Watermark.html). 
It is a timestamped item inserted into the stream that tells us "from
this point on there will be no more items with timestamp less than
this". Unfortunately, we almost never know for sure when such a
statement becomes true and there is always a chance some events will
arrive even later. If we do observe such an offending item, we must
categorize it as "too late" and just filter it out.

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
which is essentially a mapping stream transformation. The next release
of Jet will feature a fully developed API that supports windowed
aggregation of infinite streams and we also plan to add more batch
transforms (`sort` and `distinct` for example).

On the other hand,Jet's core has had full-fledged support for all of the
windows described above since version 0.4. You can refer to the
[Under the Hood](Under_the_Hood) chapter for details on how to create a
Core API DAG that does infinite stream aggregation.

## Fault Tolerance and Processing Guarantees

One less-than-obvious consequence of stepping up from finite to infinite
streams is the difficulty of forever maintaining the continuity of the
output, even in the face of changing cluster topology. A member may
leave the cluster due to an internal error, loss of networking, or
deliberate shutdown for maintenance. This will cause the computation job
to be suspended. Except for the obvious problem of new data pouring in
while we're down, we have a much more fiddly issue of restarting the
computation in a differently laid-out cluster exactly where it left off
and neither miss anything nor process it twice. The technical term for
this is the "exactly-once processing guarantee".

### Snapshotting the State of Computation

To achieve fault tolerance, Jet takes snapshots of the entire state of 
the computation at regular intervals. The snapshot is coordinated across 
the cluster and synchronized with a checkpoint on the data source. The 
source must ensure that, in the case of a restart, it will be able to 
replay all the data it emitted after the last checkpoint. Every other 
component in the computation must ensure it will be able to restore its 
processing state to exactly what it was at the last snapshot. If a 
cluster member goes away, Jet will restart the job on the remaining 
members, rewind the sources to the last checkpoint, restore the state of 
processing from the last snapshot, and then seamlessly continue from 
that point.

### Exactly-Once

As always when guarantees are involved, the principle of the weakest
link applies: if any part of the system is unable to provide it, the
system as a whole fails to provide it. The critical points are the
sources and sinks because they are the boundary between the domain under
Jet's control and the environment. A source must be able to consistently
replay data to Jet from a point it asks for, and the sink must either
support transactions or be _idempotent_, tolerating duplicate submission
of data.

As of version 0.5, Hazelcast Jet supports exactly-once with the source
being either a Hazelcast `IMap` or a Kafka topic, and the sink being a
Hazelcast `IMap`.

### At-Least-Once

A lesser, but still useful guarantee you can configure Jet for is
"at-least-once". In this case no stream item will be missed, but some
items may get processed again after a restart, as if they represented
new events. Jet can provide this guarantee at a higher throughput and
lower latency than exactly-once, and some kinds of data processing can
gracefully tolerate it. In some other cases, however, duplicate
processing of data items can have quite surprising consequences. There
is more information about this in our
[Under the Hood](/Under_the_Hood/How_Infinite_Stream_Processing_Works_In_Jet#page_The+Pitfalls+of+At-Least-Once+Processing)
chapter.

We also have an in-between case: if you configure Jet for exactly-once
but use Kafka as the sink, after a job restart you may get duplicates in
the output. As opposed to duplicating an input item, this is much more
benign because it just means getting the exact same result twice.

### Enable Fault Tolerance

Fault tolerance is off by default. To activate it for a job, create a
`JobConfig` object and set the
[_processing guarantee_](https://hazelcast-l337.ci.cloudbees.com/view/Jet/job/Jet-javadoc/javadoc/com/hazelcast/jet/config/JobConfig.html#setProcessingGuarantee-com.hazelcast.jet.config.ProcessingGuarantee-).
You can also configure
[_snapshot interval_](https://hazelcast-l337.ci.cloudbees.com/view/Jet/job/Jet-javadoc/javadoc/com/hazelcast/jet/config/JobConfig.html#setSnapshotIntervalMillis-long-).

```java
JobConfig jobConfig = new JobConfig();
jobConfig.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
jobConfig.setSnapshotIntervalMillis(SECONDS.toMillis(10));
```

Using less frequent snapshots, more data will have to be replayed
and the temporary spike in the latency of the output will be greater.
More frequent snapshots will reduce the throughput and introduce more
latency variation during regular processing.

### Level of Safety

Jet stores the snapshots into Hazelcast `IMap`s, which means that you
don't have to install any other system for it to work. It also means
that the mechanism is at most as safe as the `IMap` itself so it is
important to configure its level of safety. `IMap` is a replicated
in-memory data structure, storing each key-value pair on a configurable
number of cluster members. By default it will store one master value
plus one backup, resulting in a system that tolerates the failure of a
single member at a time. You can tweak this setting when starting Jet,
for example increase the backup count to two:

```java
JetConfig config = new JetConfig();
config.getInstanceConfig().setBackupCount(2);
JetInstance = Jet.newJetInstance(config);
```

### Split-Brain Protection

A particularly nasty kind of failure is the "split brain": due to a very
specific pattern in the loss of network connectivity the cluster splits
into two parts, where within each part the members see each other, but
none of those in the other part(s). Each part by itself lives on
thinking the other members left the cluster. Now we have two
fully-functioning Jet clusters where there was supposed to be one. Each
one will recover and restart the same Jet job, making a mess in our
application.

Hazelcast Jet offers a mechanism to fight off this hazard: 
[_split-brain protection_](https://hazelcast-l337.ci.cloudbees.com/view/Jet/job/Jet-javadoc/javadoc/com/hazelcast/jet/config/JobConfig.html#setSplitBrainProtection-boolean-).
It works by ensuring that a job cannot be restarted in a
cluster whose size isn't more than half of what it was before the job
was suspended. Enable split-brain protection like this:

```java
jobConfig.setSplitBrainProtection(true);
```

A loophole here is that, after the split brain has occurred, you could
add more members to any of the sub-clusters and have them both grow to
more than half the previous size. Since the job will keep trying to
restart itself and by definition one cluster has no idea of the other's
existence, it will restart as soon as the quorum value is reached.

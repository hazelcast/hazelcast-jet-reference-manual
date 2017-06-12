As mentioned in the
[Hazelcast Jet 102](/Getting_Started/Hazelcast_Jet_102_-_Trade_Monitoring_Streaming_Job)
section, determining the watermark is somewhat of a black art; it's about
superimposing order over a disordered stream of events. We must decide
at which point it stops making sense to wait even longer for data about
past events to arrive. There's a tension between two opposing forces
here:

- wait as long as possible to account for all the data;
- get results as soon as possible.

While there are ways to (kind of) achieve both, there's a significant
associated cost in terms of complexity and overal performance. Hazelcast
Jet takes a simple approach and strictly triages stream items into
"still on time" and "late", discarding the latter.

`WatermarkPolicy` is the abstraction that computes the value of
the watermark for a (sub)stream of disordered data items. It takes as
input the timestamp of each observed item and outputs the current
watermark value.

### Predefined watermark policies

 We provide some general, data-agnostic watermark policies in the
 `WatermarkPolicies` class:

#### "With Fixed Lag"

The `withFixedLag()` policy will emit watermarks that lag behind the
highest observed event timestamp by a configured amount. In other words,
each time an event with the highest timestamp so far is encountered,
this policy emits a watermark item with `eventTimestamp - lag`. This
puts a limit on the spread between timestamps in the stream: all events
whose timestamp is more than the configured `lag` behind the highest
timestamp are considered late.


#### "Limiting Lag and Delay"

The `limitingLagAndDelay()` policy applies the same fixed-lag logic as
above and adds another limit: maximum delay from observing any item and
emitting a watermark at least as large as its timestamp. A stream may
experience a lull (no items arriving) and this added limit will ensure
that the watermark doesn't stay behind the highest timestamp observed
before the onset of the lull. However, the skew between substreams may
still cause the watermark that reaches the downstream vertex to stay
behind some timestamps. This is because the downstream will only get the
lowest of all substream watermarks.

The advantage of this policy is that it doesn't assume anything about
the unit of measurement used for event timestamps.

#### "Limiting Lag and Lull"

The `limitingLagAndLull()` policy is similar to `limitingLagAndDelay` in
adressing the stream lull problem and goes a step further by addressing
the issues of lull combined with skew. To achieve this it must introduce
an assumption, though: that the time unit used for event timestamps is
milliseconds. After a given period passes with the watermark not being
advanced by the arriving data (i.e., a lull happens), it will start
advancing it in lockstep with the passage of the local system time. The
watermark isn't adjusted _towards_ the local time; the policy just
ensures the difference between local time and the watermark stays the
same during a lull. Since the system time advances equally on all
substream processors, the watermark propagated to downstream is now
guaranteed to advance regardless of the lull.

There is, however, a subtle issue with `limitingLagAndLull()`: if there
is any substream that never observes an item, that substream's policy
instance won't be able to initialize its "last seen timestamp" and will
cause the watermark sent to the downstream to forever lag behind all
the actual data.

#### "Limiting Timestamp and Wall-Clock Lag"

The `limitingTimestampAndWallClockLag()` policy makes a stronger
assumption: that the event timestamps are in milliseconds since the Unix
epoch and that they are synchronized with the local time on the
processing machine. It puts a limit on how much the watermark can lag
behind the local time. As long as its assumption holds, this policy
gives straightforward results. It also doesn't suffer from the subtle
issue with `limitingLagAndLull()`.

### Watermark Throttling

The policy objects presented above will return the "ideal" watermark
value according to their logic; however it would be too much overhead to
insert a watermark item each time the ideal watermark advances. This
is why a throttling layer should always be added on top of the baseline
policy. For the purpose of sliding windows, there is an easy answer:
suppress all watermark items that belong to the same frame as the
already emitted one. Such items would have no effect since the
watermark must advance beyond a frame's end for the aggregating vertex
to consider the frame completed and act upon its results. The method
`WatermarkPolicy#throttleByFrame()` will return a policy with this
kind of throttling applied. For other cases there is
`throttleByMinStep()` which suppresses watermark items until the
watermark has advanced more than `minStep` ahead of the previously
emitted one.

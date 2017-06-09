## Sliding and Tumbling Window

The sliding window has a simple definition: it covers a fixed-size
interval of event time which slides along as the event time advances. In
order to provide better performance, in Jet we don't literally implement
it this way: instead of smoothly sliding, the window advances in discrete
steps. The user defines both the window and the step size, with the
restriction that the window must contain an integer number of steps. With
this kind of definition, the _tumbling_ window is just a special case of
a sliding window with step size equal to window size.

The reason for the above definition of the sliding window lies in the way
we implement the computation. We divide the timestamp axis into _frames_
of length equal to the step size and assign each event to its frame. To
compute a sliding window, we just take all the frames covered by it and
combine them. This way we provide a configurable tradeoff between the
smoothness of sliding and the cost of storage/computation. 

Furthermore, we provide support to optimize a very important class of
aggregate operations: those that can be expressed in terms of a pair of
functions where one is commutative/associative and maintains the running
state of computation, and the other one is unrestricted and transforms
the running state into the final result. We call the former one
_combine_ and the latter one _finish_. Examples include minimum,
maximum, average, variance, standard deviation, linear regression, etc.
The point of the commutative/associative property is that you can apply
the function to the running state of two frames and get the same result
as if you applied it to each item in them. This means we don't have to
hold on to each individual item: we just maintain one instance of
running state per frame.

A slightly narrower class of aggregate operations also supports the
inverse of _combine_, which we call _deduct_: it undoes a previous
combine (i.e., _deduct_(_combine_(x, y), x) = y). When this is provided,
we can compute the entire sliding window in just two fixed steps:
_deduct_ the trailing frame and _combine_ the leading frame into the
constantly maintained sliding window result.

Note that these techniques are non-restrictive: you can always provide a
trivial combining function that just merges sets of individual items,
and provide an arbitrary finishing function that acts upon them.

### Example: 30-second Window Sliding by 10 Seconds

The diagram below summarizes the process of constructing a 30-second
window which slides by 10 seconds, and computing the number of events
that occurred within it. For brevity we label the events as
_minutes:seconds_. This is the outline of the process:

1. Throw each event into its "bucket" (the frame whose time interval it
belongs to).
2. Instead of keeping the items in the frame, just keep the item count.
3. Combine the frames into three different positions of the sliding 
window, yielding the final result: the number of events that occurred 
within the window's timespan.

<img alt="Grouping disordered events by frame and then to sliding window" 
    src="/images/windowing-frames.png"
    width="800"/>

This would be a useful interpretation of the results: "At the time 1:30,
the 30-second running average was 8/30 = 0.27 events per second. Over
the next 20 seconds it increased to 10/30 = 0.33 events per second."

Keep in mind that the whole diagram represents what happens on just one
cluster member and for just one grouping key. The same process is going
on simultaneously for all the keys on all the members.

### Two-stage aggregation

The concept of frame combining helps us implement two-stage aggregation as well. In the first stage the individual members come up with their partial results by frame and send them over a distributed edge to the second stage, which combines the frames with the same timestamp. After having combined all the partial frames from members, it combines the results along the event time axis into the sliding window.

<img alt="Combining partial frames in two-stage aggregation" 
    src="/images/combining-frames.png"
    width="800"/>

## Session Window

In the abstract sense, the session window is a quite intuitive concept:
it simply captures a burst of events. As soon as the gap between two
events exceeds the configured session timeout, the window closes.
However, because the Jet processor encounters events out of their
original order, this kind of window becomes quite tricky to compute.

The way Jet computes the session windows is easiest to explain in terms
of the _event interval_: the range
`[eventTimestamp, eventTimestamp + sessionTimeout]`.
Initially an event causes a new session window to be created, covering
exactly the event interval. 

<img alt="Session window: single event" 
    src="/images/session-window-1.png"
    width="200"/>

A following event under the same key belongs to this window iff its
interval overlaps it. The window is extended to cover the entire
interval of the new event. 

<img alt="Session window: extend with another event" 
    src="/images/session-window-2.png"
    width="110"/>
    
If the event interval don't overlap, a new session window is created for
the new event.

<img alt="Session window: create a new window after session timeout" 
    src="/images/session-window-3.png"
    width="240"/>

The event may happen to belong to two existing windows if its interval
bridges the gap between them; in that case they are combined into one.

<img alt="Session window: an event may merge two existing windows" 
    src="/images/session-window-4.png"
    width="240"/>


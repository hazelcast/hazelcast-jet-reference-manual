[TOC]

`Processor` is the main type whose implementation is up to the user: it
contains the code of the computation to be performed by a vertex. There
are a number of Processor building blocks in the Jet API which allow you
to just specify the computation logic, while the provided code
handles the processor's cooperative behavior. Please refer to the
[AbstractProcessor section](Convenience_API_to_Implement_a_Processor).

A processor's work can be conceptually described as follows: "receive
data from zero or more input streams and emit data into zero or more
output streams." Each stream maps to a single DAG edge (either inbound
or outbound). There is no requirement on the correspondence between
input and output items; a processor can emit any data it sees fit,
including none at all. The same `Processor` abstraction is used for all
kinds of vertices, including sources and sinks.

## Cooperative Multithreading

Cooperative multithreading is one of the core features of Jet and can be
roughly compared to [green
threads](https://en.wikipedia.org/wiki/Green_threads). It is purely a
library-level feature and does not involve any low-level system or JVM
tricks; the `Processor` API is simply designed in such a way that the
processor can do a small amount of work each time it is invoked, then
yield back to the Jet engine. The engine manages a thread pool of fixed
size and on each thread, the processors take their turn in a round-robin
fashion.

The point of cooperative multithreading is better performance. Several
factors contribute to this:

- The overhead of context switching between processors is much lower
since the operating system's thread scheduler is not involved.
- The worker thread driving the processors stays on the same core for
longer periods, preserving the CPU cache lines.
- The worker thread has direct knowledge of the ability of a processor to
make progress (by inspecting its input/output buffers).

`Processor` instances are cooperative by default. The processor can opt
out of cooperative multithreading by overriding `isCooperative()` to
return `false`. Jet will then start a dedicated thread for it.

### Requirements

To maintain an overall good throughput, a cooperative processor must
take care not to hog the thread for too long (a rule of thumb is up to a
millisecond at a time). Jet's design strongly favors cooperative
processors and most processors can and should be implemented to fit
these requirements. The major exception are sources and sinks because
they often have no choice but calling into blocking I/O APIs.

## The Outbox

The processor sends its output items to its `Outbox`, which has a
separate bucket for each outbound edge. The buckets have limited
capacity and will refuse an item when full. A cooperative processor
should be implemented such that when its item is rejected by the outbox,
it saves its processing state and returns from the processing method.
The execution engine will then drain the outbox buckets.

By contrast, a non-cooperative processor gets an auto-flushing, blocking
outbox that never rejects an item. This can be leveraged to simplify the
processor's implementation; however the simplification alone should
never be the reason to declare a processor non-cooperative.

## Rules of Watermark Propagation

Jet's class `ConcurrentInboundEdgeStream` (CIES for short) manages a
processor's input streams belonging to a single edge. As it receives
watermark items from each of them, its duty is to sort out when to
present the watermark item to the processor. 

Let's start our analysis from the vertex upstream to CIES. There are N
parallel processors determining each its own watermark value and
emitting a watermark item when the value advances. All these items
travel downstream to M processors of the next vertex, but if they were
all let through, the downstream processors would experience a wildly
erratic watermark value. This is why CIES contains logic that coalesces
the watermark items before letting its processor observe them. These are
the rules:

* The value of the watermark a processor emits must be strictly
increasing. CIES will throw an exception if it detects a non-increasing
watermark in any input stream.

* The watermark item is always broadcast, regardless of the edge type.
This means that all N upstream processors send their watermark to all M
downstream processors.

* The processor will observe a watermark value only once it has received
a value at least that high from all upstream processors.

## Data Processing Callbacks

Two callback methods are involved in data processing: `process()` and
`complete()`. Implementations of these methods can be stateful and do
not need to be thread-safe because Jet guarantees to use the processor
instances from one thread at a time, although not necessarily always the
same thread.

### process(ordinal, inbox)

Jet passes the items received over a given edge to the processor by
calling `process(ordinal, inbox)`. All items received since the last
`process()` call are in the inbox, but also all the items the processor
hasn't removed in a previous `process()` call. There is a separate
instance of `Inbox` for each  inbound edge, so any given `process()`
call involves items from only one edge.

The processor must not remove an item from the inbox until it has
fully processed it. This is important with respect to the cooperative
behavior: the processor may not be allowed to emit all items
corresponding to a given input item and may need to return from the
`process()` call early, saving its state. In such a case the item should
stay in the inbox so Jet knows the processor has more work to do even if
no new items are received.

### tryProcess()

If a processor's inbox is empty, Jet will call its `tryProcess()`
method instead. This allows the processor to perform work that is not
input data-driven. The method has a `boolean` return value and if it
returns `false`, it will be called again before any other methods are
called. This way it can retry emitting its output until the outbox
accepts it.

An important use case for this method is the emission of watermark
items. A job that processes an infinite data stream may experience
occasional lulls &mdash; periods with no items arriving. On the other
hand, a windowing processor is not allowed to act upon each item
immediately due to event skew; it must wait for a watermark item to
arrive. During a stream lull this becomes problematic because the
watermark itself is primarily data-driven and advances in response to
the observation of event timestamps. The watermark-inserting processor
must be able to advance the watermark even during a stream lull, based
on the passage of wall-clock time, and it can do it inside the
`tryProcess()` method.

### complete()

Jet calls `complete()` after all the input edges are exhausted. It is
the last method to be invoked on the processor before disposing of it.
Typically this is where a batch processor emits the results of an
aggregating operation. If it can't emit everything in a given call, it
should return `false` and will be called again later.

## Snapshotting Callbacks

Hazelcast Jet supports fault-tolerant processing jobs by taking
distributed snapshots. In regular time intervals each of the source
vertices will perform a snapshot of its own state and then emit a
special item to its output stream: a _barrier_. The downstream vertex
that receives the barrier item makes its own snapshot and then forwards
the barrier to its outbound edges, and so on towards the sinks.

At the level of the `Processor` API the barrier items are not visible;
`ProcessorTasklet` handles them internally and invokes the snapshotting
callback methods described below.

### saveToSnapshot()

Jet will call this method when it determines it's time for the processor
to save its state to the current snapshot. Except for source vertices,
this happens when the processor has received the barrier item from all
its inbound streams and processed all the data items preceding it. The
method must emit all its state to the special _snapshotting bucket_ in
the Outbox, by calling `outbox.offerToSnapshot()`. If the outbox doesn't
accept all the data, it must return `false` to be called again later,
after the outbox has been flushed.

When this method returns `true`, `ProcessorTasklet` will forward the
barrier item to all the outbound edges.

### restoreFromSnapshot()

When a Jet job is restarting after having been suspended, it will first
reload all the state from the last successful snapshot. Each processor
will get its data through the invocations of this method. Its parameter
is the `Inbox` filled with a batch of snapshot data. The method will be
called repeatedly until it consumes all the snapshot data.

### finishSnapshotRestore()

Jet will call this method after it has delivered all the snapshot data
to `restoreFromSnapshot()`. The processor may use it to initialize some
transient state from the restored state.

## Issues in At-Least-Once Jobs

In a job configured for _exactly once_ processing, as soon as a
processor receives a barrier item from an input stream, it will stop
consuming from that input stream until it has received the same barrier
from all input streams. Then it will take a snapshot. With
_at-least-once_ it will take a snapshot at the same point, but won't
stop consuming any input streams. After a restart, the state it restores
will have accounted for all those items received past the barrier, but
it will also receive these items again. The consequences of this can be
far-reaching to quite an unexpected degree, as we discuss next.

### Data Loss

Imagine a very simple kind of processor: it matches up the items that
belong to a _pair_ based on some rule. If it receives item A first, it
remembers it. Later on, when it receives item B, it emits that fact
to its outbound edge and forgets about the two items. It may also first
receive B and wait for A.

Now imagine this sequence: `A -- barrier -- B`. In at-least-once it
processes both A and B, emits that to the downstream, and forgets about
them. After restart, however, the item B will be replayed because it
occurred after the last snapshot, but item A won't. Now the processor is
stuck forever in a state where it's expecting A and has no idea it
already got both and emitted that fact.

Problems similar to this will happen with any state the processor keeps
until it has got enough information to emit the results and then forgets
it. By the time it takes a snapshot, the post-barrier items will have
caused it to forget facts about pre-barrier items. After a restart it
will behave as though it has never observed the pre-barrier items,
resulting in behavior equivalent to data loss.

### Non-Monotonic Watermark

One special case of the above story concerns watermark items. Thanks to
watermark coalescing, processors are typically implemented against the
invariant that the watermark value always increases. However, in
_at-least-once_ the post-barrier watermark items will advance the
processor's watermark value. After the job restarts and the state gets
restored to the snapshotted point, the watermark will appear to have
gone back, breaking the invariant. This can again lead to data loss.

### Best Practice: Document At-Least-Once Behavior

Given how extremely non-trivial the failure modes can be in
_at-least-once_, the processor should always document its possible
behaviors for that case.


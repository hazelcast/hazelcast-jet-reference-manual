At the heart of Jet is the 
[`TaskletExecutionService`](https://github.com/hazelcast//hazelcast-jet/blob/0.5-maintenance/hazelcast-jet-core/src/main/java/com/hazelcast/jet/impl/execution/TaskletExecutionService.java).
It manages the threads that perform all the computation in a Jet job.
Although this class is not formally a part of Jet's public API,
understanding how it schedules code for execution is essential if you
want to implement a cooperative processor.

## Cooperative Multithreading

Cooperative multithreading is one of the core features of Jet and can be
roughly compared to
[green threads](https://en.wikipedia.org/wiki/Green_threads). 
It is purely a library-level feature and does not involve any low-level
system or JVM tricks; the `Processor` API is simply designed in such a
way that the processor can do a small amount of work each time it is
invoked, then yield back to the Jet engine. The engine manages a thread
pool of fixed size and on each thread, the processors take their turn in
a round-robin fashion.

The point of cooperative multithreading is better performance. Several
factors contribute to this:

- The overhead of context switching between processors is much lower
since the operating system's thread scheduler is not involved.
- The worker thread driving the processors stays on the same core for
longer periods, preserving the CPU cache lines.
- The worker thread has direct knowledge of the ability of a processor
to make progress (by inspecting its input/output buffers).

## Tasklet

The execution service doesn't deal with processors directly; instead it
deals with _tasklets_.
[`Tasklet`](https://github.com/hazelcast//hazelcast-jet/blob/0.5-maintenance/hazelcast-jet-core/src/main/java/com/hazelcast/jet/impl/execution/Tasklet.java)
is a very simple functional interface derived from the standard Java
`Callable<ProgressState>`. The execution service manages a pool of
worker threads, each being responsible for a list of tasklets. The
worker thread simply invokes the `call()` methods on its tasklets in a
round-robin fashion. The method's return value tells whether the tasklet
made progress and whether it is now done.

The most important tasklet is the one driving a processor
(`ProcessorTasklet`); there are a few others that deal with network
sending/receiving and taking snapshots.

## Work Stealing

When a tasklet is done, its worker will inspect all the other workers'
tasklet lists to see if any of them has a longer tasklet list than its
own. If it finds such a worker, it will "steal" one of its tasklets to
even out the load per thread.

## Exponential Backoff

If none of the worker's tasklets report having made progress, the worker
will go to a short sleep. If this happens again after it wakes up, it
will sleep for twice as long. Once it reaches 1 ms sleep time, it will
continue retrying once per millisecond to see if any tasklets can make
progress.

## ProcessorTasklet

[`ProcessorTasklet`](https://github.com/hazelcast//hazelcast-jet/blob/0.5-maintenance/hazelcast-jet-core/src/main/java/com/hazelcast/jet/impl/execution/ProcessorTasklet.java)
is the one that drives a processor. It manages its inbox, outbox,
inbound/outbound concurrent queues, and tracks the current processor
state so it knows which of its callback methods to call.

During each `tasklet.call()`, `ProcessorTasklet` makes one call into
one of its processor's callbacks. It determines the processor's progress
status and reports it to the execution service.

## Non-Cooperative Processor

If a processor declares itself as non-cooperative, the execution service
will start a dedicated Java thread for its tasklet to run on.

Even if it's non-cooperative, the processor's callback methods must
still make sure they don't run for longer than a second or so at a time.
Otherwise the tasklet will never be able to initiate a snapshot on the
processor.

## Running a Jet job

When you submit a `Job` to it, Jet replicates the DAG to the whole Jet
cluster and executes a copy of it on each member.

![DAG Distribution](/images/dag-distribution.png)

Jet executes the job on a user-configurable number of threads which use
work stealing to balance the amount of work being done on each thread.
Each worker thread has a list of tasklets it is in charge of and as
tasklets complete at different rates, the remaining ones are moved
between workers to keep the load balanced.

Each instance of a `Processor` is wrapped in one tasklet which the
execution service repeatedly executes until it is done. A vertex with a
parallelism of 8 running on 4 members would have a total of 32 tasklets
running at the same time. Each member has the same number of tasklets
running.

<img alt="Tasklet execution model"
     src="/images/parallelism-model.png"
     width="800"/>

When you make a request to execute a Job, the corresponding DAG and
additional resources are deployed to the Jet cluster. Jet builds an
execution plan for the DAG on each member, which creates the associated
tasklets for each Vertex and connects them to their inputs and outputs.

Jet uses Single Producer/Single Consumer ringbuffers to transfer the
data between processors on the same member. They are data-type agnostic,
so any data type can be used to transfer the data between vertices.

Ringbuffers, being bounded queues, introduce natural backpressure into
the system; if a consumer’s ringbuffer is full, the producer will have
to back off until it can enqueue the next item. When data is sent to
another member over the network, there is no natural backpressure, so
Jet uses explicit signaling in the form of adaptive receive windows.

# Overview of Jet

At the core of Jet is the distributed computation engine. It leans onto
Hazelcast IMDG for cluster forming and maintenance; data partitioning;
and networking. For more information about Hazelcast IMDG, see the
[latest Hazelcast reference
manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html).

## Cluster

Jet is meant to be run on several machines forming a cluster, although
it is also possible to run it on a single node for testing purposes.
There are several ways to configure the nodes for discovery, explained
in detail in the [Hazelcast reference
manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#setting-up-clusters).

## Directed Acyclic Graph

The user describes the computation job in the form of a _directed
acyclic graph_ (DAG). Each vertex performs a step in the computation
and emits data items for the vertices it is connected to. A single
vertex's computation work is performed in parallel by many instances of
the `Processor` type around the cluster.

One of the major reasons to divide the full computation task into
several vertices is _data partitioning_: the ability to split the data
stream traveling over an edge into slices which can be processed
independently of each other. It works by defining a function which
computes the _partitioning key_ for each item and makes all related
items map to the same key. The computation engine can then route all
such items to the same processor instance. This makes it easy to
parallelize the computation: each processor will have the full picture
for its slice of the entire stream.

### Edge

As hinted above, the major purpose of the edge is determining how the
data is routed from individual source processors to individual
destination processors. The most powerful kind of edge is a _distributed
partitioned_ one, which guarantees that for each given partitioning key
there will be only one unique processor in the whole cluster receiving
all the items with that key.

With appropriate DAG design, network traffic can be minimized by
employing _local partitioned_ edges as well. Local edges are implemented
with the most efficient kind of concurrent queue: single-producer,
single-consumer bounded queue. It employs wait-free algorithms on both
sides and avoids `volatile` writes by using `lazySet`.

## Cooperative multithreading

Cooperative multithreading is one of the core features of Jet and can be
roughly compared to [green
threads](https://en.wikipedia.org/wiki/Green_threads). It is purely a
library-level feature and doesn't involve any low-level system or JVM
tricks; the `Processor` API is simply designed in such a way that the
processor can do a small amount of work each time it is invoked, then
yield back to the Jet engine. The engine manages a thread pool of fixed
size (the default is the number reported by
`Runtime.availableProcessors`) and on each thread the processors take
their turn in a round-robin fashion. To maintain good overall
throughput, each processor must take care not to hog the thread for too
long (rule of thumb is up to a millisecond at a time).

The point of cooperative multithreading is much lower context-switching
cost and precise knowledge of the status of a processor's input and
output buffers, which determines its ability to make progress.

### Processor

A processor's work can be conceptually described as follows: receive
data from zero or more input streams and emit data into zero or more
output streams. Each stream maps to a single DAG edge (either inbound
or outbound). There is no requirement on the correspondence between
input and output items; a processor can emit any data it sees fit,
including none at all. This means that it can play the role of a
source, sink, or transformer of data.

Each vertex can specify how many of its processors will run per cluster
member; every member will have the same number of processors.

Jet's library provides the `AbstractProcessor` class which simplifies
the implementation of the fully general `Processor` type. A trivial
implementation might look like this:

```java
public class PlusOneProcessor extends AbstractProcessor {
    @Override
    protected boolean tryProcess(int ordinal, Object item) {
        emit((int)item + 1);
        return true;
    }
}
```

This processor receives `Integer` items and emits each item incremented
by one. It doesn't differentiate between input streams (treats data from
all streams the same way) and emits each item to all output streams
assigned to it.

Processors can be stateful and don't need to be thread-safe. A single
instance will be called by a single thread at a time, although not
necessarily always the same thread.

#### Non-cooperative Processor

For some parts of a Jet job, blocking or otherwise long-running
operations cannot be avoided. Typically this happens on sources and
sinks because they interact with the environment over I/O channels which
don't offer non-blocking APIs. A blocking operation is very likely to
violate the requirements on cooperative processors. To accommodate these
special cases, Jet allows a processor to declare itself
"non-cooperative" and get its own Java thread.

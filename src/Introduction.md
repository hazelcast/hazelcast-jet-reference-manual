# Introduction

Hazelcast Jet is a distributed data processing engine, built for
high-performance batch and stream processing. It is built on top of
[Hazelcast In Memory Data Grid](http://www.hazelcast.org) (IMDG) at
its foundation, but is a separate product, with features not available
in Hazelcast.

Jet also introduces distributed [`java.util.stream`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html) support for Hazelcast IMDG data structures, such as `IMap` and `IList`.

## Data Processing Model

Jet provides high performance in memory data processing by modeling
a computation as a _Directed Acyclic Graph (DAG)_ of processing vertices.
Each _vertex_ performs a step in the computation and emits data items
for the vertices it is connected to. A single vertex's
computation work is performed in parallel by many instances of
the `Processor` type around the cluster. The different vertices are
linked together through _edges_.

One of the major reasons to divide the full computation task into
several vertices is _data partitioning_: the ability to split the data
stream traveling over an edge into slices which can be processed
independently of each other. It works by defining a function which
computes the _partitioning key_ for each item and makes all related
items map to the same key. The computation engine can then route all
such items to the same processor instance. This makes it easy to
parallelize the computation: each processor will have the full picture
for its slice of the entire stream.

Edges determine how the data is routed from individual source
processors to individual destination processors. Different edge properties
offer precise control over the flow of data.

## Clustering and Discovery

Jet is typically run on several machines that form a cluster but
it is also possible to run it on a single JVM for testing purposes.
There are several ways to configure the members for discovery, explained
in detail in the [Hazelcast reference
manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#setting-up-clusters).

## Members and Clients

A Jet _instance_ is a unit where the processing takes place. There can
be multiple instances per JVM, however this only makes sense for
testing. An instance becomes a _member_ of a cluster: it can join
and leave clusters multiple times during its lifetime. Any instance
can be used to access a cluster, giving an appearance that the entire
cluster is available locally.

On the other hand, a _client instance_ is just an accessor to a cluster
and no processing takes place in it.

## Relationship with Hazelcast IMDG

Jet leans onto [Hazelcast IMDG](http://www.hazelcast.org) for cluster
forming and maintenance; data partitioning; and networking.
For more information about Hazelcast IMDG, see the [latest Hazelcast reference
manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html).

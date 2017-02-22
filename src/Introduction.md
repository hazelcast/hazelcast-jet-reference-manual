# Introduction

Hazelcast Jet is a distributed data processing engine, built for
high-performance batch and stream processing. It is built on top of
[Hazelcast In-Memory Data Grid](http://www.hazelcast.org) (IMDG) at
its foundation, but is a separate product with features not available
in Hazelcast.

Hazelcast Jet also introduces the distributed implementation of [`java.util.stream`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html) for Hazelcast IMDG data structures, such as `IMap` and `IList`.

## Data Processing Model

Hazelcast Jet provides high performance in-memory data processing by modeling
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

Hazelcast Jet typically runs on several machines that form a cluster but
it may also run on a single JVM for testing purposes.
There are several ways to configure the members for discovery, explained
in detail in the [Hazelcast IMDG Reference
Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#setting-up-clusters).

## Members and Clients

A Hazelcast Jet _instance_ is a unit where the processing takes place. There can
be multiple instances per JVM, however this only makes sense for
testing. An instance becomes a _member_ of a cluster: it can join
and leave clusters multiple times during its lifetime. Any instance
can be used to access a cluster, giving an appearance that the entire
cluster is available locally.

On the other hand, a _client instance_ is just an accessor to a cluster
and no processing takes place in it.

## Relationship with Hazelcast IMDG

Hazelcast Jet leans on [Hazelcast IMDG](http://www.hazelcast.org) for
cluster formation and maintenance, data partitioning, and networking.
For more information on Hazelcast IMDG, see the [latest Hazelcast
Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html).

## Fault detection

In its current version, Hazelcast Jet can only detect a failure in one
of the cluster members that was running the computation, and abort the
job. A feature planned for the future is _fault tolerance_: the ability
to go back to a saved snapshot of the computation state and resume the
computation without the failed member.

## Elasticity

Hazelcast Jet supports the scenario where a new member joins the cluster
while a job is running. Currently the ongoing job will not be re-planned
to start using the member, though; this is on the roadmap for a future
version. The new member can also leave the cluster while the job is
running and this won't affect its progress.

One caveat is the special kind of member allowed by the Hazelcast IMDG:
a _lite member_. These members don't get any partitions assigned to them
and will malfunction when attempting to run a DAG with partitioned
edges. Lite members should not be allowed to join a Jet cluster.

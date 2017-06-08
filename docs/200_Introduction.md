Hazelcast Jet is a distributed data processing engine, built for
high-performance batch and stream processing. It reuses some features
and services of [Hazelcast In-Memory Data
Grid](http://www.hazelcast.org) (IMDG), but is otherwise a separate
product with features not available in the IMDG.

Jet also enriches the IMDG data structures such as `IMap` and `IList`
with a distributed implementation of
[`java.util.stream`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html).


With Hazelcast’s IMDG providing storage functionality, Hazelcast
Jet performs parallel execution to enable data-intensive applications to
operate in near real-time. Using directed acyclic graphs (DAG) to model
relationships between individual steps in the data processing pipeline,
Hazelcast Jet can execute both batch and stream-based data processing
applications. Jet handles the parallel execution using the _green
thread_ approach to optimize the utilization of the computing resources.

Breakthrough application speed is achieved by keeping both the
computation and data storage in memory. The embedded Hazelcast IMDG
provides elastic in-memory storage and is a great tool for storing the
results of a computation or as a cache for datasets to be used during
the computation. Extremely low end-to-end latencies can be achieved this
way.

It is extremely simple to use - in particular Jet can be fully embedded
for OEMs and for Microservices – making it is easier for manufacturers
to build and maintain next generation systems. Also, Jet uses Hazelcast
discovery for finding the members in the cluster, which can be used in
both on-premise and cloud environments.## The Data Processing Model

Hazelcast Jet provides high performance in-memory data processing by
modeling the computation as a _Directed Acyclic Graph (DAG)_ where
vertices represent computation and edges represent data connections. A
vertex receives data from its inbound edges, performs a step in the
computation, and emits data to its outbound edges. A single vertex's
computation work is performed in parallel by many instances of the
`Processor` type around the cluster.

One of the major reasons to divide the full computation task into
several vertices is _data partitioning_: the ability to split the data
stream traveling over an edge into slices which can be processed
independently of each other. To make this work, a function must be
defined which computes the _partitioning key_ for each item and makes
all related items map to the same key. The computation engine can then
route all such items to the same processor instance. This makes it easy
to parallelize the computation: each processor will have the full
picture for its slice of the entire stream.

Edges determine how the data is routed from individual source processors
to individual destination processors. Different edge properties offer
precise control over the flow of data.

## Clustering and Discovery

Hazelcast Jet typically runs on several machines that form a cluster but
it may also run on a single JVM for testing purposes.
There are several ways to configure the members for discovery, explained
in detail in the [Hazelcast IMDG Reference
Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#setting-up-clusters).

## Members and Clients

A Hazelcast Jet _instance_ is a unit where the processing takes place.
There can be multiple instances per JVM, however this only makes sense
for testing. An instance becomes a _member_ of a cluster: it can join
and leave clusters multiple times during its lifetime. Any instance can
be used to access a cluster, giving an appearance that the entire
cluster is available locally.

On the other hand, a _client instance_ is just an accessor to a cluster
and no processing takes place in it.

## Relationship with Hazelcast IMDG

Hazelcast Jet leans on [Hazelcast IMDG](http://www.hazelcast.org) for
cluster formation and maintenance, data partitioning, and networking.
For more information on Hazelcast IMDG, see the [latest Hazelcast
Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html).

As Jet is built on top of the Hazelcast platform, there is a tight
integration between Jet and IMDG. A Jet job is implemented as a
Hazelcast IMDG proxy, similar to the other services and data structures
in Hazelcast. The Hazelcast Operations are used for different actions
that can be performed on a job. Jet can also be used with the Hazelcast
Client, which uses the Hazelcast Open Binary Protocol to communicate
different actions to the server instance.

### Reading from and Writing to Hazelcast Distributed Data Structures

Jet embeds Hazelcast IMDG. Therefore, Jet can use Hazelcast IMDG maps,
caches and lists on the embedded cluster as sources and sinks of data
and make use of data locality. A Hazelcast `IMap` or `ICache` is
distributed by partitions across the cluster and Jet members are able to
efficiently read from the Map or Cache by having each member read just
its local partitions. Since the whole `IList` is stored on a single
partition, all the data will be read on the single member that owns that
partition. When using a map, cache or list as a Sink, it is not possible
to directly make use of data locality because the emitted key-value pair
may belong to a non-local partition. In this case the pair must be
transmitted over the network to the member which owns that particular
partition.

Jet can also use any remote Hazelcast IMDG instance via Hazelcast IMDG
connector.

## Fault Detection

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

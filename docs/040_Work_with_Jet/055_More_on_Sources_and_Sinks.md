[TOC]

In an [earlier section](Build_Your_Computation_Pipeline) we briefly
listed the resources you can use as sources and sinks of a Jet job's
data and where they fit in the general outline of a pipeline. Now let's
revisit this topic in more detail.

Jet accesses data sources and sinks via its _connectors_. They are a
computation job's point of contact with the outside world. Although the
connectors do their best to unify the various kinds of resources under
the same "data stream" paradigm, there are still many concerns that need
your attention.

## Is it Infinite?

The first decision when building a Jet computation job is whether it
will deal with finite or infinite data. A typical example of a finite
resource is a persistent storage system, whereas an infinite one is
usually like a FIFO queue, discarding old data. This is true both for
sources and sinks.

Finite data is handled by batch jobs and there are less concerns to deal
with. Examples of finite resources are the Hazelcast `IMap`/`ICache` and
the Hadoop Distributed File System (HDFS). In the infinite category the
most popular choice is Kafka, but a Hazelcast `IMap`/`ICache` can also
be used as an infinite source of update events (via the Event Journal
feature). You can also set up an `IMap`/`ICache` as a sink for an
infinite amount of data, either by ensuring that the size of the keyset
will be finite or by allowing the eviction of old entries.

## Is it Replayable?

Most finite data sources are replayable because they come from
persistent storage. You can easily replay the whole dataset. However, an
infinite data source may be of such nature that it can be consumed only
once. An example is the TCP socket connector. Such sources are bad at
fault tolerance: if anything goes wrong during the computation, it
cannot be retried.

### Does it Support Checkpointing?

It would be quite impractical if you could only replay an infinite data
stream from the very beginning. This is why you need _checkpointing_:
the ability of the stream source to replay its data from the point you
choose, discarding everything before it. Both Kafka and the Hazelcast
Event Journal support this.

## Is it Distributed?

A distributed computation engine prefers to work with distributed data
resources. If the resource isn't distributed, all Jet members will have
to contend for access to a single endpoint. Kafka, HDFS, `IMap` and
`ICache` are all distributed. On the other hand, an `IList` isn't
&mdash; it's stored on a single member and all append operations to it
must be serialized. When using it as a source, only one Jet member will
be pulling its data.

A `file` source/sink is another example of a non-distributed data
source, but with a different twist: it's more of a "manually
distributed" resource. Each member will access its own local filesystem,
which means there will be no contention, but there will also be no
global coordination of the data. When used as a sink, you'll have to
manually gather all the pieces from around the cluster, and in the role
of a source you have to prepare the files on each member so it gets its
part of the data. 

## What about Data Locality?

If you're looking to achieve record-breaking throughput for your
application, you'll have to think carefully how close you can deliver
your data to the location where Jet will consume and process it. For
example, if your source is HDFS, you should align the topologies of the
Hadoop and Jet clusters so that each machine that hosts an HDFS member
also hosts a Jet member. Jet will automatically figure this out and
arrange for each member to consume only the slice of data stored
locally.

If you're using `IMap`/`ICache` as data sources, you have two basic
choices: have Jet connect to a Hazelcast IMDG cluster, or use Jet itself
to host the data (since a Jet cluster is at the same time a Hazelcest
IMDG cluster). In the second case Jet will automatically ensure a
data-local access pattern, but there's a caveat: if the Jet job causes
an error of unrestricted scope, such as `OutOfMemoryError` or
`StackOverflowError`, it will have unpredictable consequences for the
state of the whole Jet member, jeopardizing the integrity of the data
stored on it.

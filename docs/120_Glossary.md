<table>
<tr><th>Term</th><th>Definition</th>
</tr><tr><td><b>

Aggregate Operation
</b></td><td>
A set of functional primitives that instructs Jet how to calculate some
aggregate function over one or more data sets. Used in the group-by,
co-group and windowing transforms.
</td></tr><tr><td><b>

At-Least-Once Processing Guarantee
</b></td><td>
The system guarantees to process each item of the input stream(s), but
doesn't guarantee it will process it just once.
</td></tr><tr><td><b>

Batch Processing
</b></td><td>
The act of processing a finite dataset, such as one stored in Hazelcast
IMDG or HDFS.
</td></tr><tr><td><b>

Client Server Topology
</b></td><td>Hazelcast topology where members run outside the user
application and are connected to clients using client libraries. The
client library is installed in the user application.
</td></tr><tr><td><b>

DAG
</b></td><td>
Directed Acyclic Graph which Hazelcast Jet uses to model the
relationships between individual steps of the data processing.
</td></tr><tr><td><b>

Edge
</b></td><td>
A DAG element which holds the logic on how to route the data from one
vertex's processing units to the next one's.
</td></tr><tr><td><b>

Embedded Topology
</b></td><td>
Hazelcast topology where the members are in-process with the user
application and act as both client and server.
</td></tr><tr><td><b>

Event time
</b></td><td>
A data item in an infinite stream typically contains a timestamp data
field. This is its _event time_. As the stream items go by, the event
time passes as the items' timestamps increase. A typical distributed
stream has a certain amount of event time disorder (items aren't
strictly ordered by their timestamp) so the "passage of event time" is a
somewhat fuzzy concept. Jet uses the _watermark_ to superimpose order
over the disordered stream.
</td></tr><tr><td><b>

Exactly-Once Processing Guarantee
</b></td><td>
The system guarantees that it will process each item of the input
stream(s) and will never process an item more than once.
</td></tr><tr><td><b>

Hazelcast Jet Cluster
</b></td><td>
A virtual distributed environment formed by Hazelcast Jet members
communicating with each other in a cluster.
</td></tr><tr><td><b>

Hazelcast IMDG
</b></td><td>
An In-Memory Data grid (IMDG) is a data structure that resides entirely
in memory, and is distributed among many machines in a single location
(and possibly replicated across different locations). IMDGs can support
millions of in-memory data updates per second, and they can be clustered
and scaled in ways that support large quantities of data. Hazelcast IMDG
is the in-memory data grid offered by Hazelcast.
</td></tr><tr><td><b>

HDFS
</b></td><td>
Hadoop Distributed File System. Hazelcast Jet can use it both as a data
source and a sink.
</td></tr><tr><td><b>

Jet Job
</b></td><td>
A unit of distributed computation that Jet executes. One job has one DAG
specifying what to do. A distributed array of Jet processors performs
the computation.
</td></tr><tr><td><b>

Member
</b></td><td>
A Hazelcast Jet instance (node) that is a member of a cluster. A single
JVM can host one or more Jet members, but in production there should be
one member per physical machine.
</td></tr><tr><td><b>

Partition
</b></td><td>
To guarantee that all items with the same grouping key are processed by
the same processor, Hazelcast Jet uses a total surjective function to
map each data item to the ID of its partition and assigns to each
processor its unique subset of all partition IDs. A partitioned edge
then routes all items with the same partition ID to the same processor.
</td></tr><tr><td><b>

Processor
</b></td><td>
The unit which contains the code of the computation to be performed by a
vertex. Each vertex’s computation is implemented by a processor. On each
Jet cluster member there are one or more instances of the processor
running in parallel for a single vertex.
</td></tr><tr><td><b>

Session Window
</b></td><td>
A window that groups an infinite stream's items by their timestamp. It
groups together bursts of events closely spaced in time (by less than
the configured session timeout).
</td></tr><tr><td><b>

Sliding Window
</b></td><td>
A window that groups an infinite stream's items by their timestamp. It
groups together events that belong to a segment of fixed size on the
timeline. As the time passes, the segment slides along, always extending
from the present into the recent past. In Jet, the window doesn't
literally slide, but hops in steps of user-defined size. ("Time" here
refers to the stream's own notion of time, i.e., _event time_.)
</td></tr><tr><td><b>

Source
</b></td><td>
A resource present in a Jet job's environment that delivers a data
stream to it. Hazelcast Jet uses a _source connector_ to access the
resource. Alternatively, _source_ may refer to the DAG vertex that hosts
the connector.
</td></tr><tr><td><b>

Sink
</b></td><td>
A resource present in a Jet job's environment that accepts its output
data. Hazelcast Jet uses a _sink connector_ to access the resource.
Alternatively, _sink_ may refer to the vertex that hosts the connector.
</td></tr><tr><td><b>

Stream Processing
</b></td><td>
The act of processing an infinite stream of data, typically implying
that the data is processed as soon as it appears. Such a processing job
must explicitly deal with the notion of time in order to make sense of
the data. It achieves this with the concept of _windowing_.
</td></tr><tr><td><b>

Tumbling Window
</b></td><td>
A window that groups an infinite stream's items by their timestamp. It
groups together events that belong to a segment of fixed size on the
timeline. As the time passes, the segment "tumbles" along, never
covering the same point in time twice. This means that each event
belongs to just one tumbling window position. ("Time" here refers to the
stream's own notion of time, i.e., _event time_.)
</td></tr><tr><td><b>

Vertex
</b></td><td>
The DAG element that performs a step in the overall computation. It
receives data from its inbound edges and sends the results of its
computation to its outbound edges. There are three kinds of vertices:
source (has only outbound edges), sink (has only inbound edges) and
computational (has both kinds of edges).
</td></tr><tr><td><b>

Watermark
</b></td><td>
A concept that superimposes order over a disordered underlying data
stream. An infinite data stream's items represent timestamped events,
but they don't occur in the stream ordered by the timestamp. The value
of the watermark at a certain location in the processing pipeline
denotes the lowest value of the timestamp that is expected to occur in
the upcoming items. Items that don't meet this criterion are discarded
because they arrived too late to be processed.
</td></tr><tr><td><b>

Windowing
</b></td><td>
The act of splitting an infinite stream's data into _windows_ according
to some rule, most typically one that involves the item's timestamps.
Each window becomes the target of an aggregate function, which outputs
one data item per window (and per grouping key).
</td></tr>
</table>


Term|Definition
:-|:-
**At-Least-Once Processing**| Processing guarantee where no stream item will be missed, however, some items maybe reprocessed after a change in the cluster topology, e.g., a member leaving the cluster. So, there is a chance of duplicate processing of data items.
**Batch Processing**| Act of processing on batch data which is considered as finite. It typically may refer to running a job on a data set which is available in a data center.
**Client Server Topology**|Hazelcast topology where members run outside the user application, and are connected to clients using client libraries. The client library is installed in the user application.
**DAG**| **D**irected **A**cyclic **G**raph is a structure in computer science which is a finite directed graph with no directed cycles. It is formed by a collection of vertices and edges, where the vertices are connected in pairs by edges. Hazelcast Jet's core is a distributed computation engine based on DAG, which is used to model the relationships between individual steps of the data processing.
**Edge**| Unit which transfers data from one vertex to the next.
**Embedded Topology**|Hazelcast topology where the members are in-process with the user application and act as both client and server.
**Exactly-Once Processing**| Processing guarantee where no stream item will be missed or reprocessed after a change in the cluster topology, e.g., a member leaving the cluster. 
**Hazelcast Jet Cluster**|Virtual environment formed by Hazelcast Jet members communicating with each other in a Hazelcast Jet cluster.
**Hazelcast Partitions**|Memory segments containing the data. Hazelcast is built-on the partition concept, it uses partitions to store and process data. Each partition can have hundreds or thousands of data entries depending on your memory capacity. You can think of a partition as a block of data.
**Hazelcast IMDG**|An in-memory data grid (IMDG) is a data structure that resides entirely in memory, and is distributed among many members in a single location or across multiple locations. IMDGs can support thousands of in-memory data updates per second, and they can be clustered and scaled in ways that support large quantities of data. Hazelcast IMDG is the in-memory data grid offered by Hazelcast.
**HDFS**| **H**adoop **D**istributed **F**ile **S**ystem, for which Hazelcast Jet provides a source and sink.
**Jet Job**| Unit of work which is executed; it is composed of processors.
**Member**|A Hazelcast instance. Depending on your Hazelcast usage, it can refer to a server or a Java virtual machine (JVM). Members belong to a Hazelcast cluster. Members are also referred as member nodes, cluster members, or Hazelcast members.
**Processor**| Unit which contains the code of the computation to be performed by a vertex. Each vertex’s computation is implemented by a Processor. On each Jet cluster member there are one or more instances of the processor running in parallel for a single vertex.
**Source**|Vertex which injects data from the environment into the Jet job.
**Sink**| Vertex which drains the result of a Jet job computations into the environment.
**Stream Processing**| Act of processing on streaming data which is considered as infinite. It deals with in-flight data before it is stored. It offers lower latency; data is processed on-the-fly and you do not have to wait for the whole data set to arrive in order to run a computation.
**Vertex**| Main unit of work in a Jet computation. There are three kinds of vertex in Jet: source, computational, and sink.
**Windowing**| Policy to determine how to select finite data chunks in infinite stream processing, whose aggregate results are of an interest. Hazelcast Jet uses tumbling, sliding and session windows.

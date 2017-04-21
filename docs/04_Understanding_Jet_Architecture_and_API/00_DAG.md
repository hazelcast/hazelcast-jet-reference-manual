At the core of Jet is the distributed computation engine based on the
paradigm of a **directed acyclic graph** (DAG). In this graph, vertices
are units of data processing and edges are units of data routing and
transfer.

![DAG](../images/dag.png)

Each vertex's computation is implemented by a subtype of the `Processor`
interface. On each member there are one or more instances of the
processor running in parallel for a single vertex; their number is
configured using its `localParallelism` attribute. Generally the
processor is implemented by the user, but there are some ready-made
implementations in Jet's library for common operations like `flatMap`
and `groupBy`.

Data sources and sinks are implemented as `Processor`s as well and are
used for the terminal vertices of the DAG. A source can be distributed,
which means that on each member of the Jet cluster a different slice of
the full data set will be read. Similarly, a sink can also be
distributed so each member can write a slice of the result data to its
local storage. _Data partitioning_ is used to route each slice to its
target member. Examples of distributed sources supported by Jet are HDFS
files and Hazelcast's `IMap`, `ICache` and `IList`.

_Edges_ transfer data from one vertex to the next and contain the
partitioning logic which ensures that each item is sent to its target
processor.

After a `Job` is created, the DAG is replicated to the whole Jet cluster
and executed in parallel on each member.

![DAG Distribution](../images/dag-distribution.png)

Execution is done on a user-configurable number of threads which use
work stealing to balance the amount of work being done on each thread.
Each worker thread has a list of tasklets it is in charge of and as
tasklets complete at different rates, the remaining ones are moved
between workers to keep the load balanced.

Each instance of a `Processor` is wrapped in one tasklet which is
repeatedly executed until it reports it is done. A vertex with a
parallelism of 8 running on 4 nodes would have a total of 32 tasklets
running at the same time. Each node will have the same number of
tasklets running.

![Parallelism](../images/parallelism-model.png)

When a request to execute a Job is made, the corresponding DAG and
additional resources are deployed to the Jet cluster. An execution plan
for the DAG is built on each node, which creates the associated tasklets
for each Vertex and connects them to their inputs and outputs.

Jet uses Single Producer/Single Consumer ringbuffers to transfer the
data between processors on the same member. They are data-type agnostic,
so any data type can be used to transfer the data between vertices.

Ringbuffers, being bounded queues, introduce natural back pressure into
the system; if a consumer’s ringbuffer is full, the producer will have
to back off until it can enqueue the next item. When data is sent to
another member over the network, there is no natural back pressure, so
Jet uses explicit signaling in the form of adaptive receive windows.

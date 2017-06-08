This and the following sections cover vertex implementations provided in
Jet's library. While formally there's only one kind of vertex, in practice
there is an important distinction between the following:

* A **source** is a vertex with no inbound edges. It injects data from
the environment into the Jet job.
* A **sink** is a vertex with no outbound edges. It drains the output of
the Jet job into the environment.
* An **internal** vertex has both kinds of edges. It accepts some data
from upstream vertices, performs some computation, and emits the results
to downstream vertices. Typically it doesn't interact with the
environment.

The `com.hazelcast.jet.processor` package contains static utility
classes with factory methods that return suppliers of processors, as
required by the `dag.newVertex(name, procSupplier)` calls. There is a
convention in Jet that every module containing vertex implementations
contributes a utility class to the same package. Inspecting the
contents of this package in your IDE should allow you to discover all
vertex implementations available on the projet's classpath.

The main factory class for the source vertices provided by the Jet core
module is `Sources`. It contains sources that ingest data from Hazelcast
IMDG structures like `IMap`, `ICache`, `IList`, etc., as well as some
simple sources that get data from files and TCP sockets (`readFiles`,
`streamSocket` and some more).

Paralleling the sources there's `Sinks` for the sink vertices,
supporting the same range of resources (IMDG, files, sockets). There's
also a general `writeBuffered` method that takes some boilerplate out of
writing custom sink. The user must implement a few primitives: create a
new buffer, add an item to it, flush the buffer. The provided code takes
care of integrating these primitives into the `Processor` API (draining
the inbox into the buffer and handling the general lifecycle).

In addition to these two clasess in Jet's Core module, there are modules
that connect to 3rd party resources like Kafka and Hadoop Distributed
File System (HDFS). Each such module declares a class in the same
package, `com.hazelcast.jet.processor`, exposing the module's source and
sink definitions.

Please refer to [Additional Modules](/090_Additional_Modules/_index.md) 
section for Apache Kafka and Apache Hadoop HDFS sources and sinks. 

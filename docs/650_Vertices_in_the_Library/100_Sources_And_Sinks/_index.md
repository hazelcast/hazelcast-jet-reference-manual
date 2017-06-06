This section documents the sources and sinks provided in Jet's library.
Here's a short overview of the contents.

The main factory class for source vertices is `Sources`. It contains
sources that ingest data from Hazelcast IMDG structures like `IMap`,
`ICache`, `IList`, etc., as well as some simple sources that get data
from files and TCP sockets (`readFiles`, `streamSocket` and some more).

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

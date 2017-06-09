## IMap and ICache readers

IMap and ICache Readers distribute the partitions to 
processors according to ownership of the partitions. 
Thus each processor will access data locally. Processors 
will iterate over entries and emit them as `Map.Entry`. 
The number of Hazelcast partitions should be configured to 
at least `localParallelism * clusterSize`, otherwise some 
processors will have no partitions assigned to them.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readMap(MAP_NAME));
    // ... other vertices
```

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readCache(CACHE_NAME));
    // ... other vertices
```

You can use IMap and ICache readers to fetch the entries
from a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    Vertex source = dag.newVertex("source", Sources.readMap(MAP_NAME, clientConfig));
    // ... other vertices
```

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    Vertex source = dag.newVertex("source", Sources.readCache(CACHE_NAME, clientConfig));
    // ... other vertices
```

If the underlying map or cache is concurrently being modified, 
there are no guarantees given with respect to missing or duplicate items.

## IList reader

Since IList is not a partitioned data structure, 
all elements from the list are emitted on a single 
member &mdash; the one where the entire list is stored.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readList(LIST_NAME));
    // ... other vertices
```

You can use IList reader to fetch the items from 
a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    Vertex source = dag.newVertex("source", Sources.readList(LIST_NAME, clientConfig));
    // ... other vertices
```

## File Reader

File Reader is a source that emits lines from files in a 
directory matching the supplied pattern (but not its subdirectories). 
You can pass `*` as the pattern to read all the files in the directory.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readFiles(DIRECTORY));
    // ... other vertices
```

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readFiles(DIRECTORY, 
        StandardCharsets.UTF_8, PATTERN));
    // ... other vertices
```

The files must not change while being read; if they do, 
the behavior is unspecified. There will be no indication of 
which file a particular line comes from.

The same pathname should be available on all members, 
but it should not contain the same files. For example 
it should not resolve to a directory shared over the network.

Since this processor is file IO-intensive, local parallelism of the 
vertex should be set according to the performance characteristics of the 
underlying storage system. Typical values are in the range of 1 to 4. 
Multiple files are read in parallel; if just a single file is read, it 
is always read by single thread.

See the [Access log analyzer sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/batch/access-log-analyzer)
for a fully working example.

## File Streamer

File Streamer is a source that generates a stream of lines of text coming from 
files in the watched directory matching the supplied pattern 
(but not its subdirectories). You can pass `*` as the pattern 
to read all the files in the directory. It will pick up both newly created 
files and content appended to pre-existing files. It expects the 
file contents not to change once appended. There is no indication of  
which file a particular line comes from.
    
The processor will scan pre-existing files for file sizes on 
startup and process them from that position. It will ignore 
the first line if the starting offset is not immediately after 
a newline character (it is assumed that another process is 
concurrently appending to the file).

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.streamFiles(DIRECTORY));
    // ... other vertices
```
```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.streamFiles(DIRECTORY, 
        StandardCharsets.UTF_8, PATTERN));
    // ... other vertices
```
    
The same pathname should be available on all the members, but it 
should not contain the same files. For example it should not 
resolve to a directory shared over the network.
    
Since this processor is file IO-intensive, local parallelism 
of the vertex should be set according to the performance 
characteristics of the underlying storage system. Typical 
values are in the range of 1 to 4. If just a single file is read, 
it is always read by single thread.

When a change is detected, the file is opened, appended lines are 
read and file is closed. This process is repeated as necessary.

The processor completes when the directory is deleted. However, 
in order to delete the directory, all files in it must be deleted 
and if you delete a file that is currently being read from, 
the job may encounter an `IOException`. The directory must be 
deleted on all members. Any `IOException` will cause the job to fail.
    
**Limitation on Windows**

On Windows OS, the `WatchService` is not notified of the appended lines
until the file is closed. If the writer keeps the file open while
appending (which is typical), the processor may fail to observe the
changes. It will be notified if any process tries to open that file,
such as looking at the file in Explorer. This holds for Windows 10 with
the NTFS file system and might change in future. You are advised to do
your own testing on your target Windows platform.

**Use the latest JRE**

The underlying JDK API `java.nio.file.WatchService` has a history of 
unreliability and this processor may experience infinite blocking, 
missed, or duplicate events as a result. Such problems may be resolved 
by upgrading the JRE to the latest version.

See the [Access stream analyzer sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/streaming/access-stream-analyzer)
for a fully working example.

## Socket Streamer

Socket Streamer is a source which streams text read from 
a socket line by line. You can configure a `Charset` otherwise 
`UTF-8` will be used as the default. Each processor instance will 
create a socket connection to the configured `[host:port]`, 
so there will be `clusterSize * localParallelism` connections. 
The server should do the load-balancing.

The processors will complete when the socket is closed by the server.
No reconnection is attempted.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.streamSocket(HOST, PORT));
    // ... other vertices
```

See the [Socket code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/streaming/socket)
for a fully working example.

## HDFS Reader

`HdfsProcessors.readHdfs()` is used to read items from one or more HDFS
files. The input is split according to the given `InputFormat` and read
in parallel across all processor instances.

`readHdfs()` by default emits items of type `Map.Entry<K,V>`, where `K`
and `V` are the parameters for the given `InputFormat`. It is possible
to transform the records using an optional mapper parameter. For
example, in the above example the output record type of `TextInputFormat`
is  `Map.Entry<LongWritable, Text>`. `LongWritable` represents the line
number and `Text` the contents of the line. If you do not care about
line numbers, and want your output as a plain `String`, you can do as
follows:

```java
Vertex source = dag.newVertex("source", HdfsProcessors.readHdfs(jobConf, (k, v) -> v.toString()));
```

With this change, `readHdfs()` will emit items of type `String` instead.

### Cluster Co-location

The Jet cluster should be run on the same machines as the HDFS nodes for
best read performance. If this is the case, each processor instance will
try to read as much local data as possible. A heuristic algorithm is
used to assign replicated blocks across the cluster to ensure a
well-balanced work distribution between processor instances for maximum
performance.

## Kafka Streamer

`KafkaProcessors.streamKafka()` is used to consume items from one or more [Apache Kafka](https://kafka.apache.org/documentation) topics. It uses the Kafka consumer API and consumer groups to distribute partitions among processors where each partition is consumed by a single processor at any given time. The reader emits items of type `Map.Entry<K,V>` where the key and the value are deserialized using the key/value deserializers configured in Kafka properties.

The partition count in Kafka should be at least as large as the total parallelism in the Jet cluster (`localParallelism * clusterSize`) to make sure that each processor will get some partitions assigned to it.

Internally, a `KafkaConsumer` is created per `Processor` instance using
the supplied properties. All processors must be in the same consumer
group supplied by the `group.id` property. It is required that the
`group.id` is explicitly set by the user to a non-empty value. The
supplied properties will be passed on to each `KafkaConsumer` instance.
These processors are only terminated in case of an error or if the
underlying job is cancelled.

`streamKafka()` forces the `enable.auto.commit` property to `false` and commits the current offsets after they have been fully emitted.

This vertex takes a `Properties` instance which will be forwarded to the underlying Kafka consumers as configuration. For example, this is how we configure it in our
[`ConsumeKafka`](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/streaming/kafka/src/main/java/ConsumeKafka.java)
code sample:

```java
Properties props = props(
        "group.id", "group-" + Math.random(),
        "bootstrap.servers", "localhost:9092",
        "key.deserializer", StringDeserializer.class.getCanonicalName(),
        "value.deserializer", IntegerDeserializer.class.getCanonicalName(),
        "auto.offset.reset", "earliest");

Vertex source = dag.newVertex("source", KafkaProcessors.streamKafka(props, "t1", "t2"));
```

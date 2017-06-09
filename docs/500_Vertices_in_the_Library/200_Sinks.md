##IMap and ICache writers

IMap and ICache Writers drain the entries to a buffer 
and uses `putAll` method for flushing them into 
IMap and ICache respectively. Processors expects 
items of type `Map.Entry`.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeMap(MAP_NAME));
```
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeCache(CACHE_NAME));
```

You can use IMap and ICache writers to write the entries
to a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeMap(MAP_NAME, clientConfig));
```
```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeCache(CACHE_NAME, clientConfig));
```

## IList writer

IList writer adds the items to a Hazelcast IList.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeList(LIST_NAME));
```

You can use IList writer to write the items to a remote 
Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeList(LIST_NAME, clientConfig));
```

## File Writer

File Writer is a sink which writes all items to a local file on each 
member. Result of `toStringF` function will be written to the file 
followed by a platform-specific line separator. Files are named with an 
integer number starting from 0, which is unique cluster-wide.

The same pathname must be available for writing on all members. 
The file on each member will contain the part of the data processed 
on that member.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeFile(DIRECTORY));
```
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeFile(DIRECTORY, Object::toString, 
        StandardCharsets.UTF_8, true));
```

Since this processor is file IO-intensive, local parallelism 
of the vertex should be set according to the performance 
characteristics of the underlying storage system. Typical 
values are in the range of 1 to 4. 

See the [Access log analyzer sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/batch/access-log-analyzer)
for a fully working example.

## Log Writer

Log Writer is a sink which logs all items at the INFO level.
`Punctuation` items are not logged.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", DiagnosticProcessors.writeLogger());
```
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", DiagnosticProcessors.writeLogger(Object::toString));
```

Note that the event will be logged on the cluster members, 
not on the client, so it's primarily meant for testing.
Local parallelism of 1 is recommended for this vertex.

## Socket Writer

Socket Writer is a sink which writes the items to 
a socket as text. Each processor instance will create 
a socket connection to the configured `[host:port]`, 
so there will be `clusterSize * localParallelism` 
connections. The server should do the load balancing.

Processors drain the items to a buffer and flush them 
to the underlying output stream.
 
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeSocket(HOST, PORT));
```

## HDFS Writer

`HdfsProcessors.writeHdfs()` expects items of type `Map.Entry<K,V>` and
writes the key and value parts to HDFS. Each processor instance creates
a single file in the output path identified by the member ID and the
processor ID. Unlike in MapReduce, the output files are not sorted by
key and are written in the order they are received by the processor.

Mapping of the input item can be done for `writeHdfs()` to map the
incoming items into the required format. For example:

```java
Vertex sink = dag.newVertex("sink", HdfsProcessors.writeHdfs(jobConf,
                (String k) -> new Text(k), (Long c) -> new LongWritable(c)));
```

This will transform the key and value to their `Writable` equivalents
which can be required for certain `OutputFormat` implementations.

### Serialization of Writables

Special care must be taken when serializing `Writable` items. The
`hazelcast-jet-hadoop` module implements out-of-the-box serialization support
for some of the primitive types including the following:

* `BooleanWritable`
* `ByteWritable`
* `DoubleWritable`
* `FloatWritable`
* `IntWritable`
* `LongWritable`
* `Text`

Anything outside of these types falls back to a default implementation
for all `Writable` types which write the full class names and the fields
per item. When deserializing, the class name is read first and the
deserialized instance is created using the classloader and reflection.
The explicitly registered types only write a single integer as a type ID
and do not use reflection for deserialization.

To explicitly register your own `Writable` types for fast serialization,
you can extend the provided `WritableSerializerHook` class and register
the hook with Hazelcast.

## WriteKafka

`KafkaProcessors.writeKafka()` is a processor factory for a vertex which acts as an [Apache Kafka](https://kafka.apache.org/documentation) sink. It receives items of type `Map.Entry` and sends a
`ProducerRecord` to the specified topic with key/value parts which will
be serialized according to the Kafka producer configuration. The key and
value serializers set in the properties should be able to handle the
keys and values received by the processor.

Internally, a single `KafkaProducer` is created per member, which is
shared among all `Processor` instances on that member.

This vertex takes a `Properties` instance which will be forwarded to the underlying Kafka producer as configuration, for example:

```java
Properties props = props(
        "bootstrap.servers", "localhost:9092",
        "key.serializer", StringSerializer.class.getName(),
        "value.serializer", IntegerSerializer.class.getName());

Vertex sink = dag.newVertex("sink", KafkaProcessors.writeKafka("topic1", properties));
```

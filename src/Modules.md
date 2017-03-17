# Additional Modules

Hazelcast Jet comes with modules that can be used to connect to additional data sources and sinks. You can also create your own custom connectors.


## hazelcast-jet-hadoop

`hazelcast-jet-hadoop` module provides read and write capabilities to
[Apache Hadoop](http://hadoop.apache.org/).

 The `ReadHdfsP` and `WriteHdfsP` classes provide source and sink processors
 which can be used for reading and writing, respectively. The processors
 take a `JobConf` as parameters which can be used to specify the
 `InputFormat`, `OutputFormat` and their respective paths.

Example:

```java
JobConf jobConf = new JobConf();
jobConf.setInputFormat(TextInputFormat.class);
jobConf.setOutputFormat(TextOutputFormat.class);
TextInputFormat.addInputPath(jobConf, inputPath);
TextOutputFormat.setOutputPath(jobConf, outputPath);

Vertex source = dag.newVertex("source", ReadHdfsP.readHdfs(jobConf));
Vertex sink = dag.newVertex("sink", WriteHdfsP.writeHdfs(jobConf));
...
```

### ReadHdfsP

`ReadHdfsP` is used to read items from one or more HDFS files. The input
is split according to the given `InputFormat` and read in parallel
across all processor instances.

`ReadHdfsP` by default emits items of type `Map.Entry<K,V>`, where `K`
and `V` are the parameters for the given `InputFormat`. It is possible
to transform the records using an optional mapper parameter. For
example, in the above example the output record type of `TextInputFormat`
is  `Map.Entry<LongWritable, Text>`. `LongWritable` represents the line
number and `Text` the contents of the line. If you do not care about
line numbers, and want your output as a plain `String`, you can do as
follows:

```java
Vertex source = dag.newVertex("source", readHdfs(jobConf, (k, v) -> v.toString()));
```

With this change, `ReadHdfsP` will emit items of type `String` instead.

#### Cluster Co-location

Jet cluster should be run on the same nodes as the HDFS nodes for best
read performance. If the hosts are aligned, each processor instance will
try to read as much local data as possible. A heuristic algorithm is used
to assign replicated blocks across the cluster to ensure a
well-balanced work distribution between processor instances.

### WriteHdfsP

`WriteHdfsP` expects items of type `Map.Entry<K,V>` and writes the key
and value parts to HDFS. Each processor instance creates a single file
in the output path identified by the member ID and the processor ID.
Unlike in MapReduce, the output files are not sorted by key and are
written in the order they are received by the processor.

A similar transformation to `ReadHdfsP` can be
done for `WriteHdfsP` to map the incoming items into the required
format. For example:

```java
Vertex sink = dag.newVertex("sink", WriteHdfsP.writeHdfs(jobConf, (String k) -> new Text(k),
                (Long c) -> new LongWritable(c)));
```

This will transform the key and value to their `Writable` equivalents
which can be required for certain `OutputFormat` implementations.

### Serialization of Writables

Special care must be taken when serializing `Writable` items. The
`hazelcast-jet-hadoop` module implements out of the box serialization support
for some of the primitive types including the following:

* `BooleanWritable`
* `ByteWritable`
* `DoubleWritable`
* `FloatWritable`
* `IntWritable`
* `LongWritable`
* `Text`

Anything outside of these types falls back to a default implementation for
all `Writable` types which writes the full class name and the
fields per item. When deserializing, the class name is read first
and the deserialized instance is created using the classloader and reflection.
The explicitly registered types only write a single integer as a type id
and do not use reflection for deserialization.

To explicitly register your own `Writable` types for fast serialization,
you can extend the provided `WritableSerializerHook` class and register
the hook with Hazelcast.

## hazelcast-jet-kafka

`hazelcast-jet-kafka` module provides streaming read and write
capabilities to [Apache Kafka](https://kafka.apache.org/).

`ReadKafkaP` and `WriteKafkaP` classes provide source and sink
processors which can be used respectively for reading and writing. The
processors take a list of properties given by `Properties` as parameters
which can be used to specify the `group.id`, `bootstrap.servers`,
key/value serializer/deserializer and any other configuration parameter
for Kafka.

Example:

```java
Properties properties = new Properties();
properties.setProperty("group.id", "group0");
properties.setProperty("bootstrap.servers", "localhost:9092");
properties.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
properties.setProperty("value.deserializer", IntegerDeserializer.class.getCanonicalName());
properties.setProperty("auto.offset.reset", "earliest");
Vertex source = dag.newVertex("source", ReadKafkaP.readKafka(properties, "topic1", "topic2"));

Properties properties = new Properties();
properties.setProperty("bootstrap.servers", "localhost:9092");
properties.setProperty("key.serializer", StringSerializer.class.getCanonicalName());
properties.setProperty("value.serializer", IntegerSerializer.class.getCanonicalName());
Vertex sink = dag.newVertex("sink", WriteKafkaP.writeKafka("topic1", properties));
```

For more details about configuring Kafka, see [Apache Kafka Documentation](https://kafka.apache.org/documentation/).

### ReadKafkaP

`ReadKafkaP` is used to consume items from one or more Kafka topics. It
uses the Kafka consumer API and consumer groups to distribute partitions
among processors where each partition is consumed by a single processor
at any given time. The reader emits items of type `Map.Entry<K,V>` where
the key and the value are deserialized using the key/value deserializers
configured in Kafka properties.

Ideally, the total partition count in Kafka should be at least as large
as the total parallelism in the Jet cluster
(`localParallelism*clusterSize`) to make sure that each processor will
get some partitions assigned to it.

Internally, a `KafkaConsumer` is created per `Processor` instance using
the supplied properties. All processors must be in the same consumer
group supplied by the `group.id` property. It is required that the
`group.id` is explicitly set by the user to a non-empty value. The
supplied properties will be passed on to each `KafkaConsumer` instance.
These processors are only terminated in case of an error or if the
underlying job is cancelled.

`ReadKafkaP` forces the `enable.auto.commit` property to be set to
`false`, and commits the current offsets after they have been fully
emitted.

### WriteKafkaP

`WriteKafkaP` is a processor which acts as a Kafka sink.  It receives
items of type `Map.Entry<K,V>` and sends a `ProducerRecord` to the
specified topic with key/value parts which will be serialized according
to the Kafka producer configuration.

Internally, a single `KafkaProducer` is created per node, which is
shared among all `Processor` instances on that node.

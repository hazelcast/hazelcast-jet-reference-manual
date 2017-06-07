The `hazelcast-jet-hadoop` module provides read and write capabilities to
[Apache Hadoop](http://hadoop.apache.org/).

The `readHdfs()` and `writeHdfs()` factories provide source and sink
processors which can be used for reading and writing, respectively. The
processors take a `JobConf` as a parameter which can be used to specify
the `InputFormat`, `OutputFormat` and their respective paths.

Example:

```java
JobConf jobConf = new JobConf();
jobConf.setInputFormat(TextInputFormat.class);
jobConf.setOutputFormat(TextOutputFormat.class);
TextInputFormat.addInputPath(jobConf, inputPath);
TextOutputFormat.setOutputPath(jobConf, outputPath);

Vertex source = dag.newVertex("source", HdfsProcessors.readHdfs(jobConf));
Vertex sink = dag.newVertex("sink", HdfsProcessors.writeHdfs(jobConf));
// ...
```

See the [Hadoop Wordcount code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/batch/wordcount-hadoop)
for a fully working example.

### ReadHdfs

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

#### Cluster Co-location

The Jet cluster should be run on the same machines as the HDFS nodes for
best read performance. If this is the case, each processor instance will
try to read as much local data as possible. A heuristic algorithm is
used to assign replicated blocks across the cluster to ensure a
well-balanced work distribution between processor instances for maximum
performance.

### WriteHdfs

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

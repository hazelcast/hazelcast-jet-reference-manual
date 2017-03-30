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

The Jet cluster should be run on the same nodes as the HDFS nodes for
best read performance. If this is the case, each processor instance will
try to read as much local data as possible. A heuristic algorithm is
used to assign replicated blocks across the cluster to ensure a
well-balanced work distribution between processor instances for maximum
performance.
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
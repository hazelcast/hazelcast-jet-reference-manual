The `hazelcast-jet-hadoop` module provides read and write capabilities to
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
See the [HDFS code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/hadoop)
for a fully worked example.
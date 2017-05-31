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
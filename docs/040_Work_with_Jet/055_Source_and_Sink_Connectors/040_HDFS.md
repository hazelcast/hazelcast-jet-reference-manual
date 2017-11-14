Jet provides both a source and sink for HDFS.

The HDFS source and sink require a configuration object of type
[JobConf](https://hadoop.apache.org/docs/r2.7.3/api/org/apache/hadoop/mapred/JobConf.html)
which supplies the input and output paths and formats. No actual
MapReduce job is created, this config is simply used to describe the
required inputs and outputs. The same `JobConf` instance can be shared
between the source and the sink.

```java
JobConf jobConfig = new JobConf();
jobConfig.setInputFormat(TextInputFormat.class);
jobConfig.setOutputFormat(TextOutputFormat.class);
TextOutputFormat.setOutputPath(jobConfig, "output-path");
TextInputFormat.addInputPath(jobConfig, "input-path");
```        

The word count pipeline can then be expressed using HDFS as follows

```Java
Pipeline p = Pipeline.create();
p.drawFrom(HdfsSources.hdfs(jobConfig, (k, v) -> v.toString()))
 .flatMap(line -> traverseArray(delimiter.split(line.toLowerCase())).filter(w -> !w.isEmpty()))
 .groupBy(wholeItem(), counting())
 .drainTo(HdfsSinks.hdfs(jobConfig));
```

### Data Locality When Reading

Jet will split the input data across the cluster, with each processor
instance reading a part of the input. If the Jet nodes are running along
the HDFS datanodes, then Jet can make use of data locality by reading
the blocks locally where possible. This can bring a significant increase
in read speed.

### Output

Each processor will write to a different file in the output folder
identified by the unique processor id. The files will be in a temporary
state until the job is completed and will be committed when the job is
complete. For streaming jobs, they will be committed when the job is
cancelled. We have plans to introduce a rolling sink for HDFS in the future
to have better streaming support.

### Dealing with Writables

Hadoop types implement their own serialization mechanism through the use
of [Writable](https://hadoop.apache.org/docs/stable/api/org/apache/hadoop/io/Writable.html).
Jet provides an adapter to register a `Writable` for
[Hazelcast serialization](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#serialization)
without having to write additional serialization code. To use this
adapter, you can register your own `Writable` types by extending
`WritableSerializerHook` and
[registering the hook](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#serialization-configuration-wrap-up).

### Hadoop JARs and Classpath

When submitting JARs along with a Job, sending Hadoop JARs should be
avoided and instead Hadoop JARs should be present on the classpath of
the running members. Hadoop JARs contain some JVM hooks and can keep
lingering references inside the JVM long after the job has ended,
causing memory leaks.

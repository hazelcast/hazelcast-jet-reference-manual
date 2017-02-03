# Additional Modules

Jet comes with some additional modules that can be used to connect to
additional data sources and sinks.

These modules are currently under active development and are only meant
for testing.

## hazelcast-jet-hadoop

`HdfsReader` and `HdfsWriter` classes can be used to read and write to
[Apache Hadoop](http://hadoop.apache.org/). Currently only the
`TextInputFormat` and `TextOutputFormat` formats are  supported.

The Hadoop module will perform best when the Jet cluster is run on the
same nodes as the datanodes and will take data locality into account when
reading from HDFS.

## hazelcast-jet-kafka

`KafkaReader` and `KafkaWriter` can be used to read a stream of data from
[Apache Kafka](https://kafka.apache.org/). Kafka partitions will be
distributed across Jet processors, with each Jet processors being assigned
one or more partitions.

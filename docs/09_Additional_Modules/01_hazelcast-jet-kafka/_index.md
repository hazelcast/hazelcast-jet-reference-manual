The `hazelcast-jet-kafka` module provides streaming read and write
capabilities to [Apache Kafka](https://kafka.apache.org/).

The `ReadKafkaP` and `WriteKafkaP` classes provide source and sink
processors which can be used for reading and writing, respectively. The
processors take a list of properties given by `Properties` as a parameter
which can be used to specify the `group.id`, `bootstrap.servers`,
key/value serializer/deserializer and any other configuration parameters
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
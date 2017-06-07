The `hazelcast-jet-kafka` module provides streaming read and write
capabilities to [Apache Kafka](https://kafka.apache.org/).

The `streamKafka()` and `writeKafka()` processor factories provide
source and sink processors which can be used for reading and writing,
respectively. The processors take a list of properties given by
`Properties` as a parameter which can be used to specify the `group.id`,
`bootstrap.servers`, key/value serializer/deserializer and any other
configuration parameters for Kafka.

Example:

```java
Properties properties = new Properties();
properties.setProperty("group.id", "group0");
properties.setProperty("bootstrap.servers", "localhost:9092");
properties.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
properties.setProperty("value.deserializer", IntegerDeserializer.class.getCanonicalName());
properties.setProperty("auto.offset.reset", "earliest");
Vertex source = dag.newVertex("source",
    KafkaProcessors.streamKafka(properties, "topic1", "topic2"));

Properties properties = new Properties();
properties.setProperty("bootstrap.servers", "localhost:9092");
properties.setProperty("key.serializer", StringSerializer.class.getCanonicalName());
properties.setProperty("value.serializer", IntegerSerializer.class.getCanonicalName());
Vertex sink = dag.newVertex("sink", KafkaProcessors.writeKafka("topic1", properties));
```

For more details about configuring Kafka, see
[Apache Kafka Documentation](https://kafka.apache.org/documentation/).

See [Apache Kafkasample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/streaming/kafka)
for a fully working example.

## StreamKafka

`KafkaProcessors.streamKafka()` is used to consume items from one or
more Kafka topics. It uses the Kafka consumer API and consumer groups to
distribute partitions among processors where each partition is consumed
by a single processor at any given time. The reader emits items of type
`Map.Entry<K,V>` where the key and the value are deserialized using the
key/value deserializers configured in Kafka properties.

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

`streamKafka()` forces the `enable.auto.commit` property to be set to
`false`, and commits the current offsets after they have been fully
emitted.

## WriteKafka

`KafkaProcessors.writeKafka()` is a factory of processor which acts as a
Kafka sink. It receives items of type `Map.Entry` and sends a
`ProducerRecord` to the specified topic with key/value parts which will
be serialized according to the Kafka producer configuration. The key and
value serializers set in the properties should be able to handle the
keys and values received by the processor.

Internally, a single `KafkaProducer` is created per member, which is
shared among all `Processor` instances on that member.

See the [Kafka code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/kafka)
for a fully worked example.

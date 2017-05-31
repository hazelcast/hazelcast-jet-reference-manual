`KafkaProcessors.writeKafka()` is a factory of processor which acts as a 
Kafka sink. It receives items of type `Map.Entry` and sends a 
`ProducerRecord` to the specified topic with key/value parts which will 
be serialized according to the Kafka producer configuration. The key and 
value serializers set in the properties should be able to handle the 
keys and values received by the processor.

Internally, a single `KafkaProducer` is created per node, which is
shared among all `Processor` instances on that node.

See the [Kafka code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/kafka)
for a fully worked example.
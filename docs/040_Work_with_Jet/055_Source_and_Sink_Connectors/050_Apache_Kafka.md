Apache Kafka can be used as both a streaming source and a sink for Jet.

The following code will consume from topics `t1` and `t2` and then write to
`t3`:

```java
Properties props = new Properties();
props.setProperty("bootstrap.servers", "localhost:9092");
props.setProperty("key.serializer", StringSerializer.class.getCanonicalName());
props.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
props.setProperty("value.serializer", IntegerSerializer.class.getCanonicalName());
props.setProperty("value.deserializer", IntegerDeserializer.class.getCanonicalName());
props.setProperty("auto.offset.reset", "earliest");

p.drawFrom(KafkaSources.kafka(props, "t1", "t2"))
 .drainTo(KafkaSinks.kafka(props, "t3"));
```

### Using Kafka as a Source

When used as a streaming source, the Kafka source emits entries of
type `Map.Entry<Key,Value>` which can be transformed using an optional
mapping function. A Kafka source never terminates until the job is
explicitly cancelled or there is some other failure.

Internally, a `KafkaConsumer` is created per `Processor` instance using the
supplied properties. Jet uses manual partition assignment to allocate
available Kafka partitions among the available processors and the
`group.id` property will be ignored.

Currently there is a requirement that the global parallelism of the Kafka
source should not be more than the number of partitions you are
subscribing to. If the parallelism of the Kafka source is `2` and
you have `4` nodes, this means that a minimum of `8` Kafka partitions
should be available.

If any new partitions are added while the job is running, these will
be automatically assigned to one of the existing processors and consumed
from the beginning of the partition.

### Processing Guarantees

The Kafka source supports snapshots. On each snapshot, the current offset
for each partition is saved. When a job is restarted from a snapshot
the processor can continue reading from the saved offset.

If snapshots are disabled, then the processor will commit the offset on the
Kafka cluster instead, however the offset being committed does not give
any guarantees that the event has been processed through the whole
Jet pipeline, only that it has been read by the processor.

### Using Kafka as a Sink

When used as a sink, a `KafkaProducer` is created per node with the
supplied properties and shared among the processor instances running
on that node.

It's possible to provide a mapping function that creates `ProducerRecord`
based on incoming items or use the other overloads which simply take
a topic name.

`StreamKafkaP` is used to consume items from one or more Kafka topics. It
uses the Kafka consumer API and consumer groups to distribute partitions
among processors where each partition is consumed by a single processor
at any given time. The reader emits items of type `Map.Entry<K,V>` where
the key and the value are deserialized using the key/value deserializers
configured in Kafka properties.

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

`StreamKafkaP` forces the `enable.auto.commit` property to be set to
`false`, and commits the current offsets after they have been fully
emitted.
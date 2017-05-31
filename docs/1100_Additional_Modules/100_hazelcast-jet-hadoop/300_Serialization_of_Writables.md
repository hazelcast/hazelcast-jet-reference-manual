Special care must be taken when serializing `Writable` items. The
`hazelcast-jet-hadoop` module implements out-of-the-box serialization support
for some of the primitive types including the following:

* `BooleanWritable`
* `ByteWritable`
* `DoubleWritable`
* `FloatWritable`
* `IntWritable`
* `LongWritable`
* `Text`

Anything outside of these types falls back to a default implementation
for all `Writable` types which write the full class names and the fields
per item. When deserializing, the class name is read first and the
deserialized instance is created using the classloader and reflection.
The explicitly registered types only write a single integer as a type ID
and do not use reflection for deserialization.

To explicitly register your own `Writable` types for fast serialization,
you can extend the provided `WritableSerializerHook` class and register
the hook with Hazelcast.
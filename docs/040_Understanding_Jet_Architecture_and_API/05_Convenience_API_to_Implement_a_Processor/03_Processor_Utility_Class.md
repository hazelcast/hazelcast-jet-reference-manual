As a further layer of convenience, there are some ready-made Processor
implementations. These are the broad categories:

1. Sources and sinks for Hazelcast `IMap`, `ICache` and `IList`.
2. Processors with `flatMap`-type logic, including `map`, `filter`, and
the most general `flatMap`.
3. Processors that perform a reduction operation after grouping items by
key. These come in two flavors:
    a. **Accumulate**: reduce by transforming an immutable value.
    b. **Collect**: reduce by updating a mutable result container.

Please refer to the [`Processors` Javadoc](https://github.com/hazelcast/hazelcast-jet/blob/master/hazelcast-jet-core/src/main/java/com/hazelcast/jet/Processor.java) for further details.

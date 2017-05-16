The word count example that was described in the
[Hazelcast Jet 101](/03_Hazelcast_Jet_101_-Word_Counting_Batch_Job) chapter can be rewritten
using the `java.util.stream` API as follows:

```java
IMap<String, Long> counts = lines
                .stream()
                .flatMap(m -> Stream.of(PATTERN.split(m.getValue().toLowerCase())))
                .collect(DistributedCollectors.toIMap(w -> w, w -> 1L, (left, right) -> left + right));
```

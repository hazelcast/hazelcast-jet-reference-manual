The word count example that was described in the
[Quickstart - Word Count chapter](/03_Quickstart_-_Word_Count) can be rewritten
using the `java.util.stream` API as follows:

```java
IMap<String, Long> counts = lines
                .stream()
                .flatMap(m -> Stream.of(PATTERN.split(m.getValue().toLowerCase())))
                .collect(DistributedCollectors.toIMap(w -> w, w -> 1L, (left, right) -> left + right));
```
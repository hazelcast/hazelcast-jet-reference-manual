# java.util.stream Support for Hazelcast IMDG

Hazelcast Jet adds distributed `java.util.stream` support for Hazelcast IMap and
IList data structures.

For extensive information about `java.util.stream` API please refer to
the official [javadocs](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html).

## Simple Example

```java
JetInstance jet = Jet.newJetInstance();
IStreamMap<String, Integer> map = jet.getMap("latitudes");
map.put("London", 51);
map.put("Paris", 48);
map.put("NYC", 40);
map.put("Sydney", -34);
map.put("Sao Paulo", -23);
map.put("Jakarta", -6);
```

```java
map.stream().filter(e -> e.getValue() < 0).forEach(System.out::println);
```

## Serializable Lambda Functions

By default, the functional interfaces which were added to
`java.util.function` are not serializable. In a distributed system, the
defined lambdas need to be serialized and sent to the other members. Jet
includes the serializable version of all the interfaces found in the
`java.util.function` which can be accessed using the
`com.hazelcast.jet.stream.Distributed` class.

## Distributed Collectors

Like with the functional interfaces, Jet also includes the distributed
versions of the classes found in `java.util.stream.Collectors`. These
can be reached via `com.hazelcast.jet.stream.DistributedCollectors`.
This class also contains a few of additional collectors worth a special
mention:

Jet distinguishes between standard `java.util.stream` collectors, and  a
Jet specific interface called `Reducer`. `Reducer`s introduces some
powerful  new collector-like constructor, which can't be used as
downstream collectors and have different performance
characteristics than typical collectors.

Some of these are:

* `toIMap()`: A reducer which will write the data directly to a new Hazelcast
`IMap`. Unlike with the standard `toMap()` collector, the whole map does
not need to be transferred to the client.
* `groupingByToIMap()`: A reducer which will perform a grouping operation
and write the results to a Hazelcast `IMap`. This uses
a more efficient implementation than the standard `groupingBy()` collector
and can make use of partitioning.

* `toIList()`: A collector which will write the output to a
new Hazelcast `IList`. Unlike with the standard `toList()` collector,
the list does not need to be transferred as a whole to the client.

Some notes on some of the standard terminal operations:

The likes of `toMap()`, `toCollection()`, `toList()`, `toArray()` treat the whole
stream as chunks, and can't make use of partitioning. These are best
avoided in a distributed environment and their use should be limited to
downstream collectors and cases where the stream size is relatively small.
Instead, the Jet-specific reducers should be used where typically only a
small chunk of data is in flight at a time.

For example the following innocent looking code can easily cause out of
memory exceptions because  the whole distributed map will need to be
held in memory at a single place:

```java
// get a distributed map, which uses 5GB per node, on a 10 node cluster
IStreamMap<String, String> map = jet.getMap("large_map");
// now your result map will need larger than 50GB of memory!  
Map<String, String> result = map.stream()
                                .map(e -> e.getKey() + e.getValue())
                                .collect(toMap(v -> v, v -> v));
```

## Word Count

The word count example that was described in the [Quickstart:Word Count chapter](#quickstart-word-count) can
be rewritten using the `java.util.stream` API as follows:

```java
IMap<String, Long> counts = lines
                .stream()
                .flatMap(m -> Stream.of(PATTERN.split(m.getValue().toLowerCase())))
                .collect(DistributedCollectors.toIMap(w -> w, w -> 1L, (left, right) -> left + right));
```                

## Implementation Notes

Jet's `java.util.stream` implementation will automatically convert a
stream into a `DAG` when one of the terminal methods are called. The DAG
creation is done lazily, and only if a terminal method is called.

The following DAG will be compiled as follows:

```java
IStreamMap<String, Integer> ints = jet.getMap("ints");
int result = ints.stream().map(Entry::getValue)
                 .reduce(0, (l, r) -> l + r);
```

![image](images/jus-dag.jpg)

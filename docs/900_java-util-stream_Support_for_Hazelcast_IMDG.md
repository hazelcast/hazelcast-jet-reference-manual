Hazelcast Jet adds distributed `java.util.stream` support for Hazelcast
IMap and IList data structures.

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
However, keep in mind that the collectors such as `toMap()`,
`toCollection()`, `toList()`, and `toArray()` create a local data
structure and load all the results into it. This works fine with the
regular JDK streams, where everything is local, but usually fails badly
in the context of a distributed computing job.

For example the following innocent-looking code can easily cause
out-of-memory errors because the whole distributed map will need to be
held in the memory at a single place:

```java
// get a distributed map with 5GB per member on a 10-member cluster
IStreamMap<String, String> map = jet.getMap("large_map");
// now try to build a HashMap of 50GB
Map<String, String> result = map.stream()
                                .map(e -> e.getKey() + e.getValue())
                                .collect(toMap(v -> v, v -> v));
```

This is why Jet distinguishes between the standard `java.util.stream`
collectors and the Jet-specific `Reducer`s. A `Reducer` puts the data
into a distributed data structure and knows how to leverage its native
partitioning scheme to optimize the access pattern across the cluster.

These are some of the `Reducer` implementations provided in Jet:

* `toIMap()`: Writes the data to a new Hazelcast `IMap`.
* `groupingByToIMap()`: Performs a grouping operation and then writes
the results to a Hazelcast `IMap`. This uses a more efficient
implementation than the standard `groupingBy()` collector and can make
use of partitioning.
* `toIList()`: Writes the data to a new Hazelcast `IList`.

A distributed data structure is cluster-managed, therefore you can't
just create one and forget about it; it will live on until you
explicitly destroy it. That means it's inappropriate to use as a part of
a data item inside a larger collection, a further consequence being that
a `Reducer` is inappropriate as a downstream collector; that's where
the JDK-standard collectors make sense.

## Word Count

The word count example that was described in the
[Hazelcast Jet 101](Getting_Started/Hazelcast_Jet_101_-_Word_Counting_Batch_Job) chapter can be rewritten
using the `java.util.stream` API as follows:

```java
IMap<String, Long> counts = lines
                .stream()
                .flatMap(m -> Stream.of(PATTERN.split(m.getValue().toLowerCase())))
                .collect(DistributedCollectors.toIMap(w -> w, w -> 1L, (left, right) -> left + right));
```

## Implementation Notes

Jet's `java.util.stream` implementation will automatically convert a
stream into a `DAG` when one of the terminal methods are called. The DAG
creation is done lazily, and only if a terminal method is called.

The following DAG will be compiled as follows:

```java
IStreamMap<String, Integer> ints = jet.getMap("ints");
int result = ints.stream().map(Entry::getValue)
                 .reduce(0, (l, r) -> l + r);
```

![image](../images/jus-dag.jpg)

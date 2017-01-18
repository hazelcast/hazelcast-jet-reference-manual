# java.util.stream Support for Hazelcast IMDG

Jet adds distributed java.util.stream support for Hazelcast IMap and
IList data structures.

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
defined lambdas need to be serialized and sent to other nodes. Jet
includes serializable version of all the interfaces found in
`java.util.function` which can be accessed using the
`com.hazelcast.jet.stream.Distributed` class.

## Special Collectors

Like with the functional interfaces, Jet also include distributed
versions of the classes found in `java.util.stream.Collectors`. These
can be reached via `com.hazelcast.jet.stream.DistributedCollectors`.
This class also contains a few of additional collectors worth a special
mention:

### toIMap

A collector which will write the data directly to a new Hazelcast
`IMap`. Unlike with the standard `toMap` collector, the whole map does
not need to be transferred to the client.

### groupingByToIMap

A collector which will perform a grouping operation and write the
results to a Hazelcast `IMap`. This uses a more efficient implementation
than the standard `groupingBy` collector.

### toIList

A collector which will write the output to a new Hazelcast `IList`.
Unlike with the standard `toList` collector, the list does not need to
be transferred as a whole to the client.

## Word Count

The word count example that was described in the Quickstart can be also
be written using the java.util.stream API:

```java
IMap<String, Long> counts = lines
                .stream()
                .flatMap(m -> Stream.of(PATTERN.split(m.getValue().toLowerCase())))
                .collect(DistributedCollectors.toIMap(w -> w, w -> 1L, (left, right) -> left + right));
```                

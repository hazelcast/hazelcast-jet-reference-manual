Like with the functional interfaces, Jet also includes the distributed
versions of the classes found in `java.util.stream.Collectors`. These
can be reached via `com.hazelcast.jet.stream.DistributedCollectors`.
However, keep in mind that collectors such as `toMap()`,
`toCollection()`, `toList()`, and `toArray()` create a local data
structure and load all the results into it. This works fine with the
regular JDK streams, where everything is local, but usually fails badly
in the context of a distributed computing job.

For, example the following innocent-looking code can easily cause
out-of-memory errors because the whole distributed map will need to be
held in memory at a single place:

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

* `toIMap()`: writes the data to a new Hazelcast `IMap`.
* `groupingByToIMap()`: performs a grouping operation and then writes
the results to a Hazelcast `IMap`. This uses a more efficient
implementation than the standard `groupingBy()` collector and can make
use of partitioning.
* `toIList()`: writes the data to a new Hazelcast `IList`.

A distributed data structure is cluster-managed, therefore you can't
just create one and forget about it; it will live on until you
explicitly destroy it. That means it's inappropriate to use as a part of
a data item inside a larger collection, a further consequence being that
a `Reducer` is inappropriate as a downstream collector; that's where
the JDK-standard collectors make sense.
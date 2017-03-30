The concern of coming up with a partition ID for an item has two
aspects:

1. extract the partitioning key from the item;
2. calculate the partition ID from the key.

The first point is covered by the _key extractor_ function and the
second by the `Partitioner` type. In most cases the choice of
partitioner boils down to two types provided out of the box:

1. Default Hazelcast partitioner: safe but slower;
1. `Object.hashCode()`-based partitioner: typically faster, but not safe
in general.

The trouble with `Object.hashCode()` is that its contract is only
concerned with instances that live within the same JVM. It says nothing
about the correspondence of hash codes on two separate JVM processes,
but for distributed edges it is essential that the hashcode of the
deserialized object stays the same as the original. Some clasess, like
`String` or `Integer`, specify exactly how they calculate the hashcode;
these types are safe to be partitioned by hashcode. When these
guarantees don't exist, the default partitioner can be used. It will
serialize the object and use Hazelcast's standard `MurmurHash3`
algorithm to get the partition ID.

Both aspects of partitioning are specified as arguments to the
edge's `partitioned()` method. This example specifies default Hazelcast
partitioner:

```java
edge.partitioned(wholeItem());
```

and this one specifies the `Object.hashCode()` strategy:

```java
edge.partitioned(wholeItem(), HASH_CODE));
```
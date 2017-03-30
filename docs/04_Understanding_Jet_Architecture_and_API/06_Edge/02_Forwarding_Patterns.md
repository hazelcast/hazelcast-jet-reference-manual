The forwarding pattern decides which of the processors in the candidate
set to route each particular item to.

#### Variable Unicast

This is the default forwarding pattern. For each item a single
destination processor is chosen with no further restrictions on the
choice. The only guarantee given by this pattern is that the item will
be received by exactly one processor, but typically care will be taken
to "spray" the items equally over all the reception candidates.

This choice makes sense when the data does not have to be partitioned,
usually implying a downstream vertex which can compute the result based
on each item in isolation.

#### Broadcast

A broadcasting edge sends each item to all candidate receivers. This is
useful when some small amount of data must be broadcast to all
downstream vertices. Usually such vertices will have other inbound edges
in addition to the broadcasting one, and will use the broadcast data as
context while processing the other edges. In such cases the broadcasting
edge will have a raised priority. There are other useful combinations,
like a parallelism-one vertex that produces the same result on each
member.

#### Partitioned

A partitioned edge sends each item to the one processor responsible for
the item's partition ID. On a distributed edge, this processor will be
unique across the whole cluster. On a local edge, each member will have
its own processor for each partition ID.

Each processor can be assigned to multiple partitions. The global number of
partitions is controlled by the number of partitions in the underlying
Hazelcast IMDG configuration. Please refer to [Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#data-partitioning) for more information about Hazelcast IMDG
partitioning.

This is the default algorithm to determine the partition ID of an item:

1. Apply the `keyExtractor` function defined on the edge to retrieve the
partitioning key.
2. Serialize the partitioning key to a byte array using Hazelcast
serialization.
3. Apply Hazelcast's standard `MurmurHash3`-based algorithm to get the
key's hash value.
4. Partition ID is the hash value modulo the number of partitions.

The above procedure is quite CPU-intensive, but has the essential
property of giving repeatable results across all cluster members, which
may be running on disparate JVM implementations.

Another common choice is to use Java's standard `Object.hashCode()`. It
is often significantly faster. However, it is not a safe strategy in
general because `hashCode()`'s contract does not require repeatable
results across JVMs, or even different instances of the same JVM
version.

You can provide your own implementation of `Partitioner` to gain full
control over the partitioning strategy.

#### All to One

The all-to-one forwarding pattern is a special-case of the **partitioned**
pattern where all items are assigned to the same partition ID, randomly
chosen at the job initialization time. This will direct all items to the
same processor.
We want as much isolation as possible between the computing nodes: each
one should ideally read its own slice of the dataset, process it
locally, and only send the aggregated data across the network for
combining into the final result. The major point of leverage is the
concept of _partitioning_: the ability to tell for a data item which
processing unit it belongs to, just by looking at the item. This means
that nodes need no coordination to sort this out.

To be effective, partitioning must be applied right at the source: each
node must read a non-overlapping slice of the data. In our case we
achieve it by putting the filenames into an `IMap` in order to exploit
Hazelcast's built-in partitioning. The map reader vertex will read just
the locally-stored partitions on each cluster member.

The next major point of partitioning is any edge going into a vertex
that groups the items by key: all items with the same key must be routed
to the same processing unit. (As a reminder, a single vertex is
implemented by many processing units distributed across the cluster.)
There are two variations here: we can partition the data but let it stay
within the same machine (local parallelization only), or we can
partition and distribute it, so that for each key there is only one
processing unit in the whole cluster that gets all the items regardless
of where they were emitted.

In a well-designed DAG the data will first be grouped and aggregated
locally, and then only the aggregated partial results will be sent over
a distributed edge, to be combined key-by-key into the complete result.
In the case of TF-IDF, the TF part is calculated in the context of a
single document. Since the data source is partitioned by document, we
can calculate TF locally without sharing anything across the cluster.
Then, to get the complete TF-IDF, we have to send just one item per
distinct document-word combination over the network to the processing
unit that will group them by word.
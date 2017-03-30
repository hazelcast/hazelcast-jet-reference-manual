Let us now focus on the data routing aspect.

1. `doc-lines` is a flatmapping vertex so the edge towards it doesn't
need partitioning. Also, since the vertex does file I/O, we usually
won't profit from parallelization. We set its `localParallelism` to 1,
so all the items (filenames) emitted from the source go to the same
file I/O processor.
    ```java
    dag.edge(from(docSource, 1).to(docLines.localParallelism(1)));
    ```    
1. `tokenize` is another flatmapping vertex so it doesn't need
partitioning, either. However, since this is a purely computational
vertex, there's exploitable parallelism. The combination of a "plain"
edge and a vertex with a higher `localParallelism` results in a
round-robin dissemination of items from `doc-lines` to all `tokenize`
processors: each item is sent to one processor, but a different one each
time.
    ```java
    dag.edge(from(docLines).to(tokenize, 1));
    ```
1. `tf` groups the items; therefore the edge towards it must be
partitioned and the partitioning key must match the grouping key. In
this case it's the item as a whole. The edge can be local because the
data is already naturally partitioned by document such that for any
given `docId`, all tuples involving it will occur on the same cluster
member.
    ```java
    dag.edge(between(tokenize, tf).partitioned(wholeItem(), HASH_CODE));
    ```
1. `tf-idf` groups the items by _word_ alone. Since the same word can
occur on any member, we need a distributed partitioned edge from `tf` to
`tf-idf`. This will ensure that for any given word, there is a total of
one processor in the whole cluster that receives tuples involving it.
    ```java
    Distributed.Function<Entry<Entry<?, String>, ?>, String> byWord =
        item -> item.getKey().getValue();
    dag.edge(from(tf).to(tfidf, 1).distributed().partitioned(byWord, HASH_CODE));
    ```
1. The edge from `stopword-source` to `tokenize` transfers a single
item, but it must deliver it to all `tokenize` processors. In our
example, the same stopwords file is accessible on all members and the
`stopword-source` processor reads it on each member independently.
Therefore a _local broadcast_ edge is the correct choice: its effect
will be to publish the reference to the local `HashSet` to all
`tokenize` processors. This edge must have a raised priority because
`tokenize` cannot do its job until it has received the stopwords.
    ```java
    dag.edge(between(stopwordSource.localParallelism(1), tokenize)
       .broadcast().priority(-1))
    ```
1. `doc-count` receives data from a distributed, partitioned data source
but needs to see all the items to come up with the total count. The
choice here is to set its `localParallelism` to one and configure its
inbound edge as _distributed broadcast_: each processor will observe all
the items, emitted on any member. It can then deliver its count over a
local broadcast, high-priority edge to all the local `tf-idf`
processors.
    ```java
    dag.edge(between(docSource.localParallelism(1),
                     docCount.localParallelism(1))
              .distributed().broadcast());
       .edge(between(docCount, tfidf).broadcast().priority(-1))
    ```
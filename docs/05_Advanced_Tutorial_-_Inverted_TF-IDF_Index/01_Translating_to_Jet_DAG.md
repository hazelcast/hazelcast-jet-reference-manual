Our DAG as a whole will look relatively complex, but it can be understood as a "backbone" (cascade of vertices) starting from
a source and ending in a sink with several more vertices attached on the side. This is just the backbone:

<img alt="Backbone of the TF-IDF DAG"
     src="../images/tf-idf-backbone.png"
     width="240"/>

1. The data source is a Hazelcast `IMap` which holds a mapping from
document ID to its filename. The source vertex will emit all the map's
entries, but only a subset on each cluster member.
1. `doc-lines` opens each file named by the map entry and emits all its
lines in the `(docId, line)` format.
1. `tokenize` transforms each line into a sequence of its words, again
paired with the document ID, so it emits `(docId, word)`.
1. `tf` builds a set of all distinct pairs emitted from `tokenize` and maintains the count of each pair's occurrences (its TF score).
1. `tf-idf` takes that set, groups the pairs by word, and calculates
the TF-IDF scores. It emits the results to the sink, which saves them
to a distributed `IMap`.

Edge types follow the same pattern as in the word-counting job: after flatmapping there is first a local, then a distributed partitioned edge. The logic behind it is not the same, though: TF can actually compute the final TF scores by observing just the local data. This is because it treats each document separately (document ID is a part of the grouping key) and the source data is already partitioned by document ID. The TF-IDF vertex does something similar to word count's combining, but there's again a twist: it will group the TF entries by word, but instead of just merging them into a single result per word, it will keep them all in lists.

To this cascade we add a `stopword-source` which reads the stopwords
file, parses it into a `HashSet`, and sends the whole set as a single
item to the `tokenize` vertex. We also add a vertex that takes the data
from `doc-source` and simply counts its items; this is the total
document count used in the TF-IDF formula. We end up with this DAG:

<img alt="The TF-IDF DAG"
     src="../images/tf-idf-full.png"
     width="520"/>


The choice of edge types into and out of `doc-count` may look suprising, so let's examine it. We start with the `doc-source` vertex, which emits one item per document, but its output is distributed across the cluster. To get the full document count on each member, each `doc-count` processor must get all the items, and that's just what the distributed broadcast edge will achieve. We'll configure `doc-count` with local parallelism of 1, so there will be one processor on every member, each observing all the `doc-source` items. The output of `doc-count` must reach all `tf-idf` processors on the same member, so we use the local broadcast edge.

Another thing to note are the two flat-mapping vertices: `doc-lines` and `tokenize`. From a purely semantic standpoint, composing flatmap with flatmap yields just another flatmap. As we'll see below, we're using custom code for these two processors... so why did we choose to separate the logic this way? There are actually two good reasons. The first one has to do with Jet's cooperative multithreading model: `doc-lines` makes blocking file IO calls, so it must be declared _non-cooperative_; tokenization is pure computation so it can be in a _cooperative_ processor. The second one is more general: the workload of `doc-lines` is very uneven. It consists of waiting, then suddenly coming up with a whole block of data. If we left tokenization there, performance would suffer because first the CPU would be forced to sit idle, then we'd be late in making the next IO call while tokenizing the input. The separate vertex can proceed at full speed all the time.

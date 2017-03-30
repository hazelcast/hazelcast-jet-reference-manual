The general outline of most DAGs is a cascade of vertices starting from
a source and ending in a sink. Each grouping operation will typically be
done in its own vertex. We have two such operations: the first one
prepares the TF map and the second one builds the inverted index.

Flatmap-like operations (this also encompasses _map_ and _filter_ as
special cases) are simple to  distribute because they operate on each
item independently. Such an operation can be attached to the work of an
existing vertex; however concerns like blocking I/O and load balancing
encourage the use of dedicated flatmapping vertices. In our case we'll
have one flatmapping vertex that transforms a filename into a stream of
the file's lines and another one that tokenizes a line into a stream of
its words. The file-reading vertex will have to use _non-cooperative_
processors due to the blocking I/O and while a processor is blocking to
read more lines, the tokenizing processors can run at full speed,
processing the lines already read.

This is the outline of the DAG's "backbone" --- the main cascade where
the data flows from the source to the sink:

1. The data source is a Hazelcast `IMap` which holds a mapping from
document ID to its filename. The source vertex will emit all the map's
entries, but only a subset on each cluster member.
1. `doc-lines` opens each file named by the map entry and emits all its
lines in the `(docId, line)` format.
1. `tokenize` transforms each line into a sequence of its words, again
paired with the document ID: `(docId, word)`.
1. `tf` builds a set of all distinct tuples and maintains the count
of each tuple's occurrences (its TF score).
1. `tf-idf` takes that set, groups the tuples by word, and calculates
the TF-IDF scores. It emits the results to the sink, which saves them
to a distributed `IMap`.

To this cascade we add a `stopword-source` which reads the stopwords
file, parses it into a `HashSet`, and sends the whole set as a single
item to the `tokenize` vertex. We also add a vertex that takes the data
from `doc-source` and simply counts its items; this is the total
document count used in the TF-IDF formula. It feeds this result into
`tf-idf`. We end up with this DAG:

```
            ------------              -----------------
           | doc-source |            | stopword-source |
            ------------              -----------------
         0  /           \ 1                   |
           /       (docId, docName)           |
          /                \                  |
         /                  V         (set-of-stopwords)
 (docId, docName)         -----------         |
        |                | doc-lines |        |
        |                 -----------         |
        |                     |               |
        |                (docId, line)        |
   -----------                |               |
  | doc-count |               V  1            |
   -----------            ----------    0     |
        |                | tokenize | <------/
        |                 ----------
        |                     |
     (count)            (docId, word)
        |                     |
        |                     V
        |                   ----
        |                  | tf |
        |                   ----
        |                     |
        |           ((docId, word), count)
        |                     |
        | 0    --------    1  |
         \--> | tf-idf | <---/
               --------
                  |
   (word, list(docId, tfidf-score)
                  |
                  V
               ------
              | sink |
               ------
```

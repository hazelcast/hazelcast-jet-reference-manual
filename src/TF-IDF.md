# Tutorial: building an Inverted TF-IDF index with Core API

In this tutorial we'll cover most of the topics involved in building a
batch processing job with Jet's Core API. We'll show you how to decide
on processor parallelism, partitioning and forwarding patterns on edges,
and how to optimally leverage the Core API to build the vertex logic
with minimum boilerplate.

Our example, the inverted index, is a basic data structure in the domain
of full-text search. The goal is to be able to quickly find the
documents that contain a given set of search terms, and to sort them by
relevance. To understand it we'll need to throw in some terminology...

- A _document_ is treated as a list of words that has a unique ID. It is
useful to define the notion of a _document index_ which maps each
document ID to the list of words it contains. We won't build this index;
it's just for the sake of explanation.
- The _inverted index_ is the inverse of the document index: it maps
each word to the list of documents that contain it. This is the
fundamental building block in our search algorithm: it will allow us to
find in O(1) time all documents relevant to a search term.
- In the inverted index, each entry in the list is assigned a _TF-IDF
score_ which quantifies how relevant the document is to the search
request.
    - Let DF (_document frequency_) be the length of the list: the
    number of documents that contain the word.
    - Let D be the total number of documents that were indexed.
    - IDF (_inverse document frequency_) is equal to `log(D/DF)`.
    - TF (_term frequency_) is the number of occurrences of the word in
    the document.
    - TF-IDF score is simply the product of `TF * IDF`.

Note that IDF is a property of the word itself: it quantifies the
relevance of each entered word to the search request as a whole. The
list of entered words can be perceived as a list of filtering functions
that we apply to the full set of documents. A more relevant word will
apply a stronger filter. Specifically, common words like "the", "it",
"on" act as pure "pass-through" filters and consequently have an IDF of
zero, making them completely irrelevant to the search.

TF, on the other hand, is the property of the combination of word and
document, and tells us how relevant the document is to the word,
regardless of the relevance of the word itself.

When the user enters a search phrase:

1. each individual term from the phrase is looked up in the inverted
index;
1. an intersection is found of all the lists, resulting in the list of
documents that contain all the words;
1. each document is scored by summing the TF-IDF contributions of each
word;
1. the result list is sorted by score (descending) and presented to the
user.

Let's have a look at a specific search phrase:

    the man in the black suit murdered the king

The list of documents that contain all the above words is quite long...
how do we decide which are the most relevant? The TF-IDF logic will make
those stand out that have an above-average occurrence of words that are
generally rare across all documents. For example, "murdered" occurs in
far fewer documents than "black"... so given two documents where one has
the same number of "murdered" as the other one has of "black", the one
with "murdered" wins because its word is more salient in general. On the
other hand, "suit" and "king" might have a similar IDF, so the document
that simply contains more of both wins.

Also note the limitation of this technique: a phrase is treated as just
the sum of its parts; a document may contain the exact phrase and this
will not affect its score.

## Building the inverted index with Java Streams

To warm us up, let's see what it takes to build the inverted index with
just thread parallelism and without the ability to scale out across
many machines. It is expressible in Java Streams API without too much
work.

We'll start from the point where we already prepared a
`Stream<Entry<Long, String>> docWords`: a stream of all the words found
in all the documents. We use `Map.Entry` as a holder of a pair of values
(a 2-tuple) and here we have a pair of `Long docId` and `String word`.
We also already know the number of all documents and have a `double
logDocCount`, the logarithm of the document count, ready.

Calculating TF is very easy, just count the number of occurrences of
each distinct pair and save the result in a `Map<Entry<Long, String>,
Long>`:

```java
// TF map: (docId, word) -> count
final Map<Entry<Long, String>, Long> tfMap = docWords
        .parallel()
        .collect(groupingBy(identity(), counting()));
```

And now we build the inverted index. We start from `tfMap`, group by
word, and the list under each word already matches our final product:
the list of all the documents containing the word. We finish off by
applying a transformation to the list: currently it's just the raw
entries from the `tf` map, but we need pairs `(docId, tfIDfScore)`.

```java
invertedIndex = tfMap
    .entrySet() // set of ((docId, word), count)
    .parallelStream()
    .collect(groupingBy(
        e -> e.getKey().getValue(),
        collectingAndThen(
            toList(),
            entries -> {
                double idf = logDocCount - Math.log(entries.size());
                return entries.stream()
                              .map(e -> tfidfEntry(e, idf))
                              .collect(toList());
            }
        )
    ));

// ((docId, word), count) -> (docId, tfIdf)
private static Entry<Long, Double> tfidfEntry(
        Entry<Entry<Long, String>, Long> tfEntry, Double idf
) {
    final Long tf = tfEntry.getValue();
    return entry(tfEntry.getKey().getKey(), tf idf);
}
```

The search function can be implemented with another Streams expression,
which you can review in the `SearchGui` class. You can also run the
`TfIdfJdkStreams` class and take the inverted index for a spin, making
actual searches.

There is one last concept in this model that we haven't mentioned yet:
the _stopword set_. It contains those words that are known in advance to
be common enough to occur in every document. Without treatment, these
words are the worst case for the inverted index: the document list under
each such word is the longest possible, and the score of all documents
is zero due to zero IDF. They raise the index's memory footprint without
providing any value. The cure is to prepare a file, `stopwords.txt`,
which is read in advance into a `Set<String>` and used to filter out the
words in the tokenization phase. The same set is used to cross out words
from the user's search phrase, as if they weren't entered.


## Translating to Jet DAG

The code given so far is a great option as long as your dataset is small
enough to not require scaling out to a cluster. There are some caveats
due to the details of how the JDK's Fork/Join engine parallelizes the
work, but when done right it will perform very well. The concerns of
_scaling out_, however, have a significant impact on the shape of the
computation. While in single-node concurrent computing a major challenge
is sharing mutable data, in multi-node distributed computing the major
challenge is simply sharing --- of any kind.

### Partitioning and merging partial results

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

### DAG's vertices: steps of the computation

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

To this cascade we add a `stopwords` vertex which reads the stopwords
file, parses it into a `HashSet`, and sends the whole set as a single
item to the `tokenize` vertex. We also add a vertex that takes the data
from the source and simply counts its tokens; this is the total document
count used in the TF-IDF formula. It feeds this result into `tf-idf`. We
end up with this DAG:

```
            ------------              -----------------
           | doc-source |            | stopword-source |
            ------------              -----------------
            /           \                     |
           /       (docId, docName)           |
          /                \                  |
         /                  V         (set-of-stopwords)
 (docId, docName)         -----------         |
        |                | doc-lines |        |
        |                 -----------         |
        |                     |               |
        |                (docId, line)        |
   -----------                |               |
  | doc-count |               V               |
   -----------            ----------          |
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
        |      --------       |
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

### DAG's edges: data routing concerns

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


### Partitioning strategy

The concern of coming up with a partition ID for an item has two
aspects:

1. extract the partitioning key from the item;
2. calculate the partition ID from the key.

The first point is covered by the _key extractor_ function and the
second one is captured by the `Partitioner` type. In most cases the
choice of partitioner boils down to two types provided out of the box:

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

### Implementing the vertex computation

The source vertex reads a Hazelcast IMap so we just use the processor
provided in Jet:

```java
dag.newVertex("doc-source", Processors.readMap(DOCID_NAME));
```

The stopwords-producing vertex has a custom processor:

```java
dag.newVertex("stopword-source", StopwordsP::new);
```

The processor's implementation is quite simple:

```java
private static class StopwordsP extends AbstractProcessor {
    @Override
    public boolean complete() {
        emit(docLines("stopwords.txt").collect(toSet()));
        return true;
    }
}
```

It emits a single item: the `HashSet` built directly from the stream
of a text file's lines.

The `doc-count` processor can again be built from the primitives
provided in the Jet's library:

```java
dag.newVertex("doc-count", Processors.accumulate(() -> 0L, (count, x) -> count + 1));
```

The `doc-lines` processor is more of a mouthful, but still built from
existing primitives:

```java
dag.newVertex("doc-lines",
    nonCooperative(
        Processors.flatMap((Entry<Long, String> e) ->
            traverseStream(docLines("books/" + e.getValue())
                           .map(line -> entry(e.getKey(), line))))));
```

Let's break down this expression... `Processors.flatMap` returns a
standard processor that emits an arbitrary number of items for each
received item. The user supplies a function of the shape `inputItem ->
Traverser(outputItems)` and the processor takes care of all the logic
required to cooperatively emit those items while respecting the output
buffer limits.

This is the user-supplied expression evaluated for each incoming item:

```java
traverseStream(docLines("books/" + e.getValue())
               .map(line -> entry(e.getKey(), line))))
```

`traverseStream` converts a `java.util.Stream` to `Traverser` so the
inner part builds the stream: `docLines()` simply  returns

```java
Files.lines(Paths.get(TfIdf.class.getResource(name).toURI()))
```

and then the mapping stage is applied, which creates a pair `(docId,
line)`. `tokenizer` is another custom vertex:

```java
dag.newVertex("tokenize", TokenizeP::new);

private static class TokenizeP extends AbstractProcessor {
    private Set<String> stopwords;
    private final FlatMapper<Entry<Long, String>, Entry<Long, String>> flatMapper =
        flatMapper(e -> traverseStream(
                   Arrays.stream(DELIMITER.split(e.getValue()))
                         .filter(word -> !stopwords.contains(word))
                         .map(word -> entry(e.getKey(), word))));

    @Override
    protected boolean tryProcess0(@Nonnull Object item) {
        stopwords = (Set<String>) item;
        return true;
    }

    @Override
    protected boolean tryProcess1(@Nonnull Object item) {
        return flatMapper.tryProcess((Entry<Long, String>) item);
    }
}
```

This is a processor that must deal with two different inbound edges. It
receives the stopword set over edge 0 and then it does a flatmapping
operation on edge 1. The logic presented here uses the same approach as
the implementation of the provided `Processors.flatMap()` processor:
there is a single instance of the `FlatMapper` that holds the business
logic of the transformation, and the `tryProcess1` callback method
directly delegates into it. If the `FlatMapper` is done emitting the
previous items, it will accept the new item, apply the user-provided
transformation, and start emitting the output items. If the buffer state
prevents it from emitting all the pending items, it will return `false`,
which will make the framework call the same `tryProcess1` method later,
with the same input item.

Let's show the code that creates the `tokenize`'s two inbound edges:

```java
dag.edge(between(stopwordSource, tokenize).broadcast())
   .edge(from(docLines).to(tokenize, 1).priority(1))
```

Especially note the `.priority(1)` part: this ensures that there will be
no attempt to deliver any data coming at edge with ordinal 1 before all
the data from ordinal 0 is already delivered. (The `between` factory
method creates an edge with ordinal 0 at both ends.) The processor would
fail if it had to tokenize a line before it has its stopword set in
place.

`tf` is another simple vertex, built purely from the provided
primitives:

```java
dag.newVertex("tf", groupAndAccumulate(() -> 0L, (count, x) -> count + 1));
```

`tf-idf` is the most complex processor:

```java
dag.newVertex("tf-idf", TfIdfP::new);

private static class TfIdfP extends AbstractProcessor {
    private double logDocCount;

    private final Map<String, List<Entry<Long, Double>>> wordDocTf = new HashMap<>();
    private final Traverser<Entry<String, List<Entry<Long, Double>>>> invertedIndexTraverser =
            lazy(() -> traverseIterable(wordDocTf.entrySet()).map(this::toInvertedIndexEntry));

    @Override
    protected boolean tryProcess0(@Nonnull Object item) throws Exception {
        logDocCount = Math.log((Long) item);
        return true;
    }

    @Override
    protected boolean tryProcess1(@Nonnull Object item) throws Exception {
        final Entry<Entry<Long, String>, Long> e = (Entry<Entry<Long, String>, Long>) item;
        final long docId = e.getKey().getKey();
        final String word = e.getKey().getValue();
        final long tf = e.getValue();
        wordDocTf.computeIfAbsent(word, w -> new ArrayList<>())
                 .add(entry(docId, (double) tf));
        return true;
    }

    @Override
    public boolean complete() {
        return emitCooperatively(invertedIndexTraverser);
    }

    private Entry<String, List<Entry<Long, Double>>> toInvertedIndexEntry(
            Entry<String, List<Entry<Long, Double>>> wordDocTf
    ) {
        final String word = wordDocTf.getKey();
        final List<Entry<Long, Double>> docidTfs = wordDocTf.getValue();
        return entry(word, docScores(docidTfs));
    }

    private List<Entry<Long, Double>> docScores(List<Entry<Long, Double>> docidTfs) {
        final int df = docidTfs.size();
        return docidTfs.stream()
                       .map(tfe -> tfidfEntry(df, tfe))
                       .collect(toList());
    }

    private Entry<Long, Double> tfidfEntry(int df, Entry<Long, Double> docidTf) {
        final Long docId = docidTf.getKey();
        final Double tf = docidTf.getValue();
        final double idf = logDocCount - Math.log(df);
        return entry(docId, tf * idf);
    }
}
```

This is quite a lot of code, but each piece is quite easy to grasp:

1. `tryProcess0()` accepts a single item, the total document count.
1. `tryProcess1()` performs a boilerplate `groupBy` operation,
collecting a list of items under each key.
1. `complete()` outputs the accumulated results, also applying the
final transformation on each one: replacing the TF score with the final
TF-IDF score. It relies on a _lazy_ traverser, which holds a
`Supplier<Traverser>` and will obtain the inner traverser from it the
first time `next()` is called. This makes it very simple to write code
that obtains a traverser for a map after it has been populated.

Finally, our DAG is terminated by a sink vertex:

```java
dag.newVertex("sink", Processors.writeMap(INVERTED_INDEX));
```

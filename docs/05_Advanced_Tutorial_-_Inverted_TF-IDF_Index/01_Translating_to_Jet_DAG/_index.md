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
line)`. Finally, the whole processor expression is wrapped into a call
of `nonCooperative()` which will declare the processor non-cooperative,
as required by the fact that it does blocking file I/O.

`tokenizer` is another custom vertex:

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
there is a single instance of `FlatMapper` that holds the business logic
of the transformation, and the `tryProcess1()` callback method directly
delegates into it. If `FlatMapper` is done emitting the previous items,
it will accept the new item, apply the user-provided transformation, and
start emitting the output items. If the buffer state prevents it from
emitting all the pending items, it will return `false`, which will make
the framework call the same `tryProcess1` method later, with the same
input item.

Let's show the code that creates the `tokenize`'s two inbound edges:

```java
dag.edge(between(stopwordSource, tokenize).broadcast().priority(-1))
   .edge(from(docLines).to(tokenize, 1));
```

Especially note the `.priority(-1)` part: this ensures that there will
be no attempt to deliver any data coming from `docLines` before all the
data from `stopwordSource` is already delivered. The processor would
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
        final double logDf = Math.log(docidTfs.size());
        return docidTfs.stream()
                       .map(tfe -> tfidfEntry(logDf, tfe))
                       .collect(toList());
    }

    private Entry<Long, Double> tfidfEntry(double logDf, Entry<Long, Double> docidTf) {
        final Long docId = docidTf.getKey();
        final double tf = docidTf.getValue();
        final double idf = logDocCount - logDf;
        return entry(docId, tf * idf);
    }
}
```

This is quite a lot of code, but each of the three pieces is not too
difficult to follow:

1. `tryProcess0()` accepts a single item, the total document count.
1. `tryProcess1()` performs a boilerplate `groupBy` operation,
collecting a list of items under each key.
1. `complete()` outputs the accumulated results, also applying the
final transformation on each one: replacing the TF score with the final
TF-IDF score. It relies on a _lazy_ traverser, which holds a
`Supplier<Traverser>` and will obtain the inner traverser from it the
first time `next()` is called. This makes it very simple to write code
that obtains a traverser from a map after it has been populated.

Finally, our DAG is terminated by a sink vertex:

```java
dag.newVertex("sink", Processors.writeMap(INVERTED_INDEX));
```

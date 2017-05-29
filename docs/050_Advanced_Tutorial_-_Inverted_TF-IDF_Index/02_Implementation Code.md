As we announced, some of the processors in our DAG will need custom
implementation code. Let's start from the source vertex. It is easy,
just the standard `IMap` reader:

```java
dag.newVertex("doc-source", Processors.readMap(DOCID_NAME));
```

The stopwords-producing processor has custom code, but it's quite
simple:

```java
dag.newVertex("stopword-source", StopwordsP::new);
```

```java
private static class StopwordsP extends AbstractProcessor {
    @Override
    public boolean complete() {
        return tryEmit(docLines("stopwords.txt").collect(toSet()));
    }
}
```

Since this is a source processor, all its action happens in
`complete()`. It emits a single item: the `HashSet` built directly from
the text file's lines.

The `doc-count` processor can be built from the primitives provided in
Jet's library:

```java
dag.newVertex("doc-count", Processors.aggregate(counting()));
```

The `doc-lines` processor is more of a mouthful, but still built from
existing primitives:

```java
dag.newVertex("doc-lines",
    Processors.nonCooperative(
        Processors.flatMap((Entry<Long, String> e) ->
            traverseStream(docLines("books/" + e.getValue())
                           .map(line -> entry(e.getKey(), line))))));
```

Let's break down this expression... `Processors.flatMap` returns a
standard processor that emits an arbitrary number of items for each
received item. We already saw one in the introductory Word Count
example. There we created a traverser from an array, here we create it
from a Java stream. We additionally apply the `nonCooperative()` wrapper
which will declare all the created processors non-cooperative. We
already explained why we do this: this processor will make blocking I/O
calls.

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
of the transformation, and `tryProcess1()` directly delegates into it.
If `FlatMapper` is done emitting the previous items, it will accept the
new item, apply the user-provided transformation, and start emitting the
output items. If the outbox refuses a pending item, it will return
`false`, which will make the framework call the same `tryProcess1()` method later, with the same input item.

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

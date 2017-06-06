To warm us up, let's see what it takes to build the inverted index with
just thread parallelism and without the ability to scale out across
many machines. It is expressible in Java Streams API without too much
work. The full code is [here](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/tf-idf/src/main/java/TfIdfJdkStreams.java).

We'll start by preparing a `Stream<Entry<Long, String>> docWords`: a
stream of all the words found in all the documents. We use `Map.Entry` as
a holder of a pair of values (a 2-tuple) and here we have a pair of
`Long docId` and `String word`:


```java
Stream<Entry<Long, String>> docWords = docId2Name
        .entrySet()
        .parallelStream()
        .flatMap(TfIdfJdkStreams::docLines)
        .flatMap(this::tokenize);
```

We know the number of all documents so we can compute `double 
logDocCount`, the logarithm of the document count:

```java
double logDocCount = Math.log(docId2Name.size());
```

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
    return entry(tfEntry.getKey().getKey(), tf * idf);
}
```

The search function can be implemented with another Streams expression,
which you can review in the [SearchGui](
https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/tf-idf/src/main/java/SearchGui.java)
class. You can also run the [TfIdfJdkStreams](
https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/tf-idf/src/main/java/TfIdfJdkStreams.java)
class and take the inverted index for a spin, making actual searches.

There is one last concept in this model that we haven't mentioned yet:
the _stopword set_. It contains those words that are known in advance to
be common enough to occur in every document. Without treatment, these
words are the worst case for the inverted index: the document list under
each such word is the longest possible, and the score of all documents
is zero due to zero IDF. They raise the index's memory footprint without
providing any value. The cure is to prepare a file, `stopwords.txt`,
which is read in advance into a `Set<String>` and used to filter out the
words in the tokenization phase. The same set is used to cross out words
from the user's search phrase, as if they weren't entered. We'll add this
feature to our DAG based model in the following section.

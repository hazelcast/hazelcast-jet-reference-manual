# Tutorial: building an Inverted TF-IDF index in Jet

TF-IDF is a basic technique in the domain of full-text search. The goal
is to be able to quickly find the documents that contain the given set
of search terms, and to sort them by relevance. To understand it we'll
need to throw in some terminology...

- A _document_ is treated as a list of words that has a unique ID. It is
useful to define the notion of a _document index_ which maps each
document ID to the list of words it contains.
- The _inverted index_ is the inverse of the document index: it maps
each word to the list of documents that contain it. This is the
fundamental building block in our search algorithm: it will allow us to
find in O(1) time all documents relevant to a search term.
- In the inverted index, each document in the list under a given search
term is assigned a _TF-IDF score_ which quantifies how relevant the
document is to the term.
    - Let DF (_document frequency_) be the length of the list: the
    number of documents that contain the word.
    - Let D be the total number of documents that were indexed.
    - IDF (_inverse document frequency_) is equal to `log(D/DF)`.
    - TF (_term frequency_) is the number of occurrences of the word in
    a given document.
    - TF-IDF score is simply the product of `TF * IDF`.

Note that IDF is the property of the word itself, outside of the context
of any particular document. It measures how _rare_ it is among the
documents. Specifically, all the common words like "the", "it", "on"
occur in all documents and have an IDF of zero. TF is the property of
the combination of word and document, and tells us how relevant the
document is to the word.

When the user enters a search phrase:
1. each individual term from the phrase is looked up in the inverted
index;
1. an intersection is found of all the lists, resulting in the list of
documents that contain all the words;
1. each document is scored by summing the TF-IDF contributions of each
word;
1. the result list is sorted by score (descending) and presented to the
user.

The point of TF-IDF scoring is best studied on an example, say

    the man in the black suit murdered the king

The list of documents that contain all the above words is quite long...
how do we decide which are the most relevant? The TF-IDF logic will make
those stand out that have an above-average occurrence of words that are
generally rare across all documents. For example, "murdered" occurs in
far fewer documents than "black"... so given two documents where one has
the same number of "murdered" as the other one has of "black", the one
with "murdered" wins because its word is more salient in general. On
the other hand, if two words have a similar IDF, then the document that
simply contains more of both put together wins.

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
(a 2-tuple) and here we have a par of `Long docId` and `String word`.
We also already know the number of all documents and have a `double
logDocCount`, the logarithm of the document count, ready.

Calculating TF is very easy, just count the number of each distinct pair
and save the result in a `Map<Entry<Long, String>, Long>`:

```java
// TF map: (docId, word) -> count
final Map<Entry<Long, String>, Long> tf = docWords
        .parallel()
        .collect(groupingBy(identity(), counting()));
```

We'll use the `tf` map as the starting point for all further
computations. To build the IDF map we just need to go through all
unique `(docId, word)` pairs, group them by word, and count the size of
each group. We retrieve IDF as our already prepared `logDocCount` minus
the logarithm of the count we computed. The `keySet` of `tf` is exactly
the set of all unique `(docId, word)` pairs, so:

```java
// IDF map: word -> idf
final Map<String, Double> idf = tf
        .keySet()
        .parallelStream()
        .collect(groupingBy(Entry::getValue,
                collectingAndThen(counting(),
                    count -> logDocCount - Math.log(count))));
```

Now we throw in a concept we haven't discussed so far... the _stopword
set_. This is the set of all those words common enough to occur
everywhere. It is used as an optimization: these words can be
entirely skipped while building the word list of a document, and they
can be crossed out from the search phrase before performing the search.
Stopword set is optimally leveraged when it's prepared in advance so
it's already there while scanning the documents, but in this example
we build it as a by-product.

The stopword set is simply a set of all the words whose IDF came out as
zero:

```java
stopwords = idf.entrySet()
               .parallelStream()
               .filter(e -> e.getValue() <= 0)
               .map(Entry::getKey)
               .collect(toSet());
```

And we're already at the last step: building the inverted index. Again
we start from `tf`, but now we need the full entry set. We filter out
all the stopwords since the inverted index doesn't have to contain them.
Then we group by word, and the list under each word already matches our
final product: the list of all documents containing the word. We finish
off by applying a transformation to the list: currently it's just the
raw entry from the `tf` map, but we need a pair `(docId, tfIDfScore)`.

```java
invertedIndex = tf
    .entrySet()
    .parallelStream()
    .filter(e -> idf.get(wordFromTfEntry(e)) > 0)
    .collect(groupingBy(
        TfIdf_Streams::wordFromTfEntry,
        collectingAndThen(
                toList(),
                entries -> entries.stream()
                                  .map(e -> tfidfEntry(idf, e))
                                  .collect(toList())
        )
    ));
}

static String wordFromTfEntry(Entry<Entry<Long, String>, Long> tfEntry) {
    return tfEntry.getKey().getValue();
}

static Entry<Long, Double> tfidfEntry(
    Map<String, Double> idf, Entry<Entry<Long, String>, Long> tfEntry
) {
    final String word = wordFromTfEntry(tfEntry);
    final Long tf = tfEntry.getValue();
    return new SimpleImmutableEntry<>(tfEntry.getKey().getKey(), tf * idf.get(word));
}
```

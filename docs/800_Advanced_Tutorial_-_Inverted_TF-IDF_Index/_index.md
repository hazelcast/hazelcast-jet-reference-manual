In this tutorial we'll explore what the DAG model offers beyond the
simple cascade of computing steps. Our DAG will feature splits, joins,
broadcast, and prioritized edges. We'll access data from the file system
and show a simple technique to distribute file reading across Jet
members. Several vertices we use can't be implemented in terms of
out-of-the-box processors, so we'll also show you how to implement your
own with minimum boilerplate.

The full code is available at the `hazelcast-jet-code-samples`
repository:

[TfIdfJdkStreams.java](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/tf-idf/src/main/java/TfIdfJdkStreams.java)

[TfIdf.java](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/tf-idf/src/main/java/TfIdf.java)

Let us first introduce the problem. The inverted index is a basic data 
structure in the domain of full-text search. First used in the 1950s, it
is still at the core of modern information retrieval systems such as
Lucene. The goal is to be able to quickly find the documents that
contain a given set of search terms, and to sort them by relevance. To
understand it we'll need to throw in some terminology...

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
2. an intersection is found of all the lists, resulting in the list of
documents that contain all the words;
3. each document is scored by summing the TF-IDF contributions of each
word;
4. the result list is sorted by score (descending) and presented to the
user.

Let's have a look at a specific search phrase:

```text
the man in the black suit murdered the king
```

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

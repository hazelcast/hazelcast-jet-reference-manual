For the first introductory example we'll go through the steps of
creating a distributed word counting job in Hazelcast Jet. We have a
dataset consisting of lines of text; we want to find the number of
occurrences of each word in it. In essence, our computation is this:

```java
List<String> lines = ... // a pre-existing list
Map<String, Long> counts = new HashMap<>();
for (String line : lines) {
    for (String word : line.split("\\W+")) {
        counts.compute(word.toLowerCase(), (w, c) -> c == null ? 1L : c + 1);
    }
}
```

We'll show you how to model this computation as a directed graph of computation steps; we'll point out the concerns that arise when trying to make it work on a huge amount of data distributed across several machines, and we'll show you how to meet those concerns with Jet.

Note that here we are describing a _batch_ job: the input is finite
and present in full before the job starts. Later on we'll present a
_streaming_ job that keeps processing an infinite stream forever,
transforming it into another infinite stream.

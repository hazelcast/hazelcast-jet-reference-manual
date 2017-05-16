In this chapter we'll introduce the basic concept of DAG-based distributed computing. We'll use a simple example that may already be familiar: there is a dataset consisting of lines of natural language text and we want to find the number of occurrences of each word in it. A single-threaded computation that does it can be expressed in just a few lines of Java:

```java
List<String> lines = ... // a pre-existing list
Map<String, Long> counts = new HashMap<>();
for (String line : lines) {
    for (String word : line.split("\\W+")) {
        counts.compute(word.toLowerCase(), (w, c) -> c == null ? 1L : c + 1);
    }
}
```

We'll move in small increments from this towards a formulation that is ready to be executed simultaneously on all machines in a cluster, utilizing all the CPU cores on each of them.

The first step will be modeling the computation as a DAG. We'll start from a single-threaded model and gradually expand it into a parallelized, distributed one, discussing at each step the concerns that arise and how to meet them. Then, in the second part, we'll show you the code to implement and run it on a Hazelcast Jet cluster.

Note that here we are describing a _batch_ job: the input is finite
and present in full before the job starts. Later on we'll present a
_streaming_ job that keeps processing an infinite stream forever,
transforming it into another infinite stream.

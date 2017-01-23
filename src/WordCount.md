# Quickstart: Word Count

In this example, we will go through building a word count application
using Jet.

## Starting a Jet cluster

To start a new Jet cluster, we need to start Jet instances. Typically
these would be started on separate machines, but for the purposes of
this tutorial we will be using the same JVM for both of the instances.

```java
public class WordCount {
    public static void main(String[] args) {
        JetInstance instance1 = Jet.newJetInstance();
        JetInstance instance2 = Jet.newJetInstance();
    }
}
```

The two nodes should automatically form a cluster, as they will by
default use multicast to discover each other.
You should see output like the following:

```
Members [2] {
  Member [10.0.1.3]:5701 - f1e30062-e87e-4e97-83bc-6b4756ef6ea3
  Member [10.0.1.3]:5702 - d7b66a8c-5bc1-4476-a528-795a8a2d9d97 this
}
```

This means the nodes successfully formed a cluster. Don't forget to
shut down the nodes afterwards, by adding

```
Jet.shutdownAll();
```

as the last line of your application.

## Populating some data

To be able to do a word count, we need some source data. Jet has built-in
readers for maps and lists from Hazelcast, so we will go ahead and
populate an `IMap` with some lines of text:

```java
IStreamMap<Integer, String> map = instance1.getMap("lines");
map.put(0, "It was the best of times,");
map.put(1, "it was the worst of times,");
map.put(2, "it was the age of wisdom,");
map.put(3, "it was the age of foolishness,");
map.put(4, "it was the epoch of belief,");
map.put(5, "it was the epoch of incredulity,");
map.put(6, "it was the season of Light,");
map.put(7, "it was the season of Darkness");
map.put(8, "it was the spring of hope,");
map.put(9, "it was the winter of despair,");
map.put(10, "we had everything before us,");
map.put(11, "we had nothing before us,");
map.put(12, "we were all going direct to Heaven,");
map.put(13, "we were all going direct the other way --");
map.put(14, "in short, the period was so far like the present period, that some of "
   + "its noisiest authorities insisted on its being received, for good or for "
   + "evil, in the superlative degree of comparison only.");
```

You might wonder why we are using a map for a sequence of lines. The
reason is that the complete list is stored on a single cluster member,
whereas the map is sharded according to each entry's key and distributed
across the cluster.

## Single-threaded computation

We want to count how many times each word occurs in the text. If we want
to do a word count without using a DAG and in a single-threaded
computation, we could do it like this:

```java
Pattern pattern = Pattern.compile("\\s+");
Map<String, Long> counts = new HashMap<>();
for (String line : map.values()) {
    for (String word : pattern.split(line)) {
        counts.compute(word.toLowerCase(), (w, c) -> c == null ? 1L : c + 1);
    }
}
```

As soon as we try to parallelize this computation, it becomes clear that
we have to model it differently. More complications arise on top of that
when we want to scale out, across multiple machines.

## Modeling Word Count in terms of a DAG

The word count computation can be roughly divided into three steps:

1. Read a line from the map ("source" step)
2. Split the line into words ("generator" step)
3. Update the running totals for each word ("accumulator" step)

We can represent these steps as a DAG:

TODO: include DAG picture

It the simplest case, the computation inside each vertex might be
executed in turn in a single-threaded environment. To execute these
steps in parallel, a producer vertex must publish its output items to
a consumer vertex running on another thread. For this to work
efficiently without interference between threads, we need to introduce
_concurrent queues_ between the vertices so each thread can do its
processing at its own pace.

TODO: include picture

Now let us exploit the parallelizability of line parsing. We can
have multiple generator instances, each parsing its subset of lines:

TODO: include picture

The accumulation step counts occurrences of individual words. Counting
one word is independent of counting any other, but we must ensure that
for each given word there is a unique instance of accumulator in charge
of it, and will receive all entries with that word. This is called
_partitioning_ in Jet, and is achieved by creating a _partitioned edge_
in the DAG, which ensures the words with same partitioning key are
transmitted to the same instance of the vertex's processor.

TODO: include picture

So far our DAG will produce correct results only when executed on a
single Jet instance (cluster of size 1). With more members, each will
compute just its local counts and then each member will race to push its
result to the output map. Fixing this requires the addition of another
vertex in the DAG, which will combine results from each local
accumulator into a global result:

TODO: include picture

## Jet Implementation

Now we will implement this DAG in Jet. The first step is to create a
source vertex:

 ```java
Vertex source = new Vertex("source", Processors.mapReader("lines"));
```

This is a simple vertex which will read the lines from the `IMap` and
emit items of type `Map.Entry<Integer, String>` to the next vertex. The
key of the entry is the line number, and the value is the line itself.
We can use the built-in map-reading processor here, which can read a
distributed IMap.

The next vertex is the _Generator_. Its responsibility is to take
incoming lines and split them into words. This operation can be
represented using a _flat map_ processor, which comes built in with Jet:

```java
final Pattern PATTERN = Pattern.compile("\\w+");
Vertex generator = new Vertex("generator",
        Processors.<Map.Entry<Integer, String>, Map.Entry<String, Long>>flatMap(line ->
                traverseArray(PATTERN.split(line.getValue()))
        )
);
```

This vertex will take an item of type `Map.Entry<Integer, String>` and
split its value part into words. The key is ignored, as the line number
is not useful for the purposes of word count. We will set a count of 1
for each word here, and the counts will be combined in the later stages
of the computation. The vertex should emit entries like `(word, 1)`,
which will have the type `Map.Entry<String, Long>`. The `Traverser`
interface is designed to be used by the built in Jet processors.

The next vertex will do the grouping of the words and emit the count
for each word. We can use the built-in `groupAndAccumulate` processor.

```java
Vertex accumulator = new Vertex("accumulator",
        Processors.<Map.Entry<String, Long>, Long>groupAndAccumulate(Entry::getKey, (currentCount, entry) ->
                entry.getValue() + (currentCount == null ? 0L : currentCount)
        )
);
```

This processor will take items of type `Map.Entry<String, Long>`, where
the key is the word and the value is the count. The expected output is
of the same type, with the counts for each word combined together. The
processor can only emit the final values after it has exhausted all the
data.

The accumulation lambda given to the `groupAndAccumulate` processor
combines the current running count with the count from the new entry.

This vertex will do a _local_ accumulation of word counts on each node.
The next step is to do a _global_ accumulation of counts. This is the
combining step:

```java
Vertex combiner = new Vertex("combiner",
        Processors.<Map.Entry<String, Long>, Long>groupAndAccumulate(Entry::getKey, (currentCount, entry) ->
                entry.getValue() + (currentCount == null ? 0L : currentCount)
        )
);
```

This vertex is identical to the previous one &mdash; since accumulation
and combining in this case are identical.

The final vertex is the output &mdash; we want to store the output in
another IMap:

```java
Vertex sink = new Vertex("sink", Processors.mapWriter("counts"));
```

Next, we add the vertices we created to our DAG, and connect the
vertices together with edges:

```java
DAG dag = new DAG()
        .vertex(source)
        .vertex(generator)
        .vertex(accumulator)
        .vertex(combiner)
        .vertex(sink)
        .edge(between(source, generator))
        .edge(between(generator, accumulator)
                .<Map.Entry<String, Long>, String>partitionedByKey(Entry::getKey))
        .edge(between(accumulator, combiner)
                .distributed()
                .<Map.Entry<String, Long>, String>partitionedByKey(Entry::getKey))
        .edge(between(combiner, sink));
```

Let's take a closer look at some of the connections between the vertices.
First, source and generator:

```java
.edge(between(generator, accumulator)
        .<Map.Entry<String, Long>, String>partitionedByKey(Entry::getKey))

```

The edge between the generator and accumulator is _partitioned_, because
all entries with the same word as key needs to be processed by the
same instance of the vertex. Otherwise the same word would be duplicated
across many instances.


```java
.edge(between(accumulator, combiner)
        .distributed()
        .<Map.Entry<String, Long>, String>partitionedByKey(Entry::getKey))
```

The edge between the `accumulator` and `combiner` is also _partitioned_,
similar to the edge between the `generator` and `accumulator`. However,
there is also a key difference: the edge is also _distributed_. A
_distributed_ edge allows items to be sent to other nodes. Since this
edge is both partitioned and distributed, the partitioning will be across
all the nodes: all entries with the same word as key will be sent to
a single processor instance in the whole cluster. This ensures that we
get the correct total count for a word.

To run the DAG and print out the results, we simply do:
```java
instance1.newJob(dag).execute().get();
System.out.println(instance1.getMap("counts").entrySet());
```

The final output should look like:

```
[heaven=1, times=2, of=12, its=2, far=1, light=1, noisiest=1,
the=14, other=1, incredulity=1, worst=1, hope=1, on=1, good=1, going=2,
like=1, we=4, was=11, best=1, nothing=1, degree=1, epoch=2, all=2,
that=1, us=2, winter=1, it=10, present=1, to=1, short=1, period=2,
had=2, wisdom=1, received=1, superlative=1, age=2, darkness=1, direct=2,
only=1, in=2, before=2, were=2, so=1, season=2, evil=1, being=1,
insisted=1, despair=1, belief=1, comparison=1, some=1, foolishness=1,
or=1, everything=1, spring=1, authorities=1, way=1, for=2]
```

An executable version of this sample can be found at the
 [Jet code samples repository](https://github.com/hazelcast/hazelcast-jet-code-samples)

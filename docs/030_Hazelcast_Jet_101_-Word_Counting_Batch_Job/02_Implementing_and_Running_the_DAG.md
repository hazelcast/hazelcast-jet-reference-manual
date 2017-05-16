Now that we've come up with a good DAG design, it's time to implement it
using the Jet DAG API. We'll present this in several steps:

1. Start a Jet cluster;
2. populate an `IMap` with sample data;
3. build the Jet DAG;
4. submit it for execution.

To start a new Jet cluster, we must start some Jet instances.
Typically these would be started on separate machines, but for the
purposes of this tutorial we'll be using the same JVM for both
instances. We can start them as shown below:

```java
public class WordCount {
    public static void main(String[] args) {
        JetInstance jet = Jet.newJetInstance();
        Jet.newJetInstance();
    }
}
```

These two instances should automatically discover each other using IP
multicast and form a cluster. You should see log output similar to the
following:

```
Members [2] {
  Member [10.0.1.3]:5701 - f1e30062-e87e-4e97-83bc-6b4756ef6ea3
  Member [10.0.1.3]:5702 - d7b66a8c-5bc1-4476-a528-795a8a2d9d97 this
}
```

This means the members successfully formed a cluster. Don't forget to
shut down the members afterwards, by adding the following as the last line of your application:

```
Jet.shutdownAll();
```

This must be executed unconditionally, even in the case of an exception;
otherwise your Java process will stay alive because Jet has started its
internal threads:

```java
public class WordCount {
    public static void main(String[] args) {
        try {
            JetInstance jet = Jet.newJetInstance();
            Jet.newJetInstance();

            ... your code here...

        } finally {
            Jet.shutdownAll();
        }
    }
}
```

As explained earlier, we'll use an `IMap` as our data source. Let's give it some sample data:

```java
IMap<Integer, String> map = jet.getMap("lines");
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

Now we move on to the code that builds and runs the DAG. We start by instantiating the DAG class and adding the source vertex:

```java
DAG dag = new DAG();
Vertex source = dag.newVertex("source", Processors.readMap("lines"));
```

It will read the lines from the `IMap` and emit items of type
`Map.Entry<Integer, String>` to the next vertex. The key of the entry
is the line number, and the value is the line itself. The built-in
map-reading processor will do just what we want: on each member it will
read only the data local to that member.

The next vertex is the _tokenizer_, which does a simple "flat-mapping"
operation (transforms one input item into zero or more output items). The
low-level support for such a processor is a part of Jet's library, we just
need to provide the mapping function:

```java
// (lineNum, line) -> words
Pattern delimiter = Pattern.compile("\\W+");
Vertex tokenizer = dag.newVertex("tokenizer",
    Processors.flatMap((Entry<Integer, String> e) ->
        Traversers.traverseArray(delimiter.split(e.getValue().toLowerCase()))
              .filter(word -> !word.isEmpty()))
);
```

This creates a processor that applies the given function to each
incoming item. The function must return a `Traverser`, which is an
abstraction that traverses a sequence of non-null items. Its purpose is
equivalent to the standard Java `Iterator`, but avoids the cumbersome
two-method API. `Traverser` is a simple functional interface with just
one method to implement: `next()`.

Specifically, our processor accepts items of type `Entry<Integer, String>`, splits the entry value into lowercase words, and emits all non-empty words.

The next vertex will do the actual word count. We can use the built-in
`groupAndAccumulate` processor for this:

```java
// word -> (word, count)
Vertex accumulator = dag.newVertex("accumulator",
    Processors.groupAndAccumulate(() -> 0L, (count, x) -> count + 1)
);
```

This processor maintains a hashtable that maps each distinct item to its
accumulated value. It takes two functions: the first one provides the
initial value and is called when observing a new distinct item; the
second one gets the previous value for the item and returns a new value.
The above function definitions can be seen to have the effect of counting
the items. The processor emits nothing until it has received all the
input, and at that point it emits the hashtable as a stream of
`Entry<String, Long>`.

Next is the combining step which computes the grand totals from
individual members' contributions. This is the code:

```java
// (word, count) -> (word, count)
Vertex combiner = dag.newVertex("combiner",
    Processors.groupAndAccumulate(
            Entry<String, Long>::getKey,
            () -> 0L,
            (count, wordAndCount) -> count + wordAndCount.getValue())
);
```

It's very similar to the accumulator vertex, but instead of the simple
increment here we have to add the entry's value to the total.

The final vertex is the sink &mdash; we want to store the output in
another `IMap`:

```java
Vertex sink = dag.newVertex("sink", Processors.writeMap("counts"));
```

Now that we have all the vertices, we must connect them into a graph and
specify the edge type as discussed in the previous section. Here's all
the code at once:

```java
dag.edge(between(source, tokenizer))
   .edge(between(tokenizer, accumulator)
           .partitioned(DistributedFunctions.wholeItem(), Partitioner.HASH_CODE))
   .edge(between(accumulator, combiner)
           .distributed()
           .partitioned(DistributedFunctions.entryKey()))
   .edge(between(combiner, sink));
```

Let's take a closer look at some of the edges. First, source to
tokenizer:

```java
   .edge(between(tokenizer, accumulator)
           .partitioned(DistributedFunctions.wholeItem(), Partitioner.HASH_CODE))
```

We chose a _local partitioned_ edge. For each word, on each member there
will be a processor responsible for it so that no items must travel
across the network. In the `partitioned()` call we specify two things:
the function that extracts the partitioning key (in this case the
built-in `wholeItem()` function which is simply the identity function
with a descriptive name), and the policy object that decides how to
compute the partition ID from the key. Here we use the built-in
`HASH_CODE`, which will derive the ID from `Object.hashCode()`. This is
always safe to use on a local edge.

Next, the edge from the accumulator to the combiner:

```java
.edge(between(accumulator, combiner)
       .distributed()
       .partitioned(DistributedFunctions.entryKey()))
```

It is _distributed partitioned_: for each word there is a single
`combiner` processor in the whole cluster responsible for it and items
will be sent over the network if needed. The partitioning key is again
the word, but here it is the key part of the `Map.Entry<String, Long>`.
For demonstration purposese we are using the default partitioning policy
here (default Hazelcast partitioning). It is the slower-but-safe choice
on a distributed edge. Detailed inspection shows that hashcode-based
partitioning would be safe as well because all of `String`, `Long`, and
`Map.Entry` have the hash function specified in their Javadoc.

To run the DAG and print out the results, we simply do the following:

```java
jet.newJob(dag).execute().get();
System.out.println(jet.getMap("counts").entrySet());
```

The final output should look like the following:

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

The full version of this sample can be found at the
[Hazelcast Jet code samples repository](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/wordcount-core-api/src/main/java/WordCountRefMan.java). You'll have to excuse the lack of
indentation; we use that file to copy-paste from it into this tutorial.
In the same directory there is also a more elaborated code sample that
processes 100 MB of disk-based text data
([WordCount.java](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/wordcount-core-api/src/main/java/WordCount.java)).

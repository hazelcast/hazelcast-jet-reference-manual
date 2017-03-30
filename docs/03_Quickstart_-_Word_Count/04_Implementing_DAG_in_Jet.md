Now we will implement this DAG in Jet. The first step is to create a
DAG and source vertex:

 ```java
DAG dag = new DAG();
Vertex source = dag.newVertex("source", Processors.mapReader("lines"));
```

This is a simple vertex which will read the lines from the `IMap` and
emit items of type `Map.Entry<Integer, String>` to the next vertex. The
key of the entry is the line number, and the value is the line itself.
We can use the built-in map-reading processor here, which can read a
distributed IMap.

The next vertex is the _tokenizer_. Its responsibility is to take
incoming lines and split them into words. This operation can be
represented using a _flat map_ processor, which comes built in with Jet:

```java
// line -> words
final Pattern delimiter = Pattern.compile("\\W+");
Vertex tokenizer = dag.newVertex("tokenizer",
      flatMap((Entry<Integer, String> line) -> traverseArray(delimiter.split(line.getValue().toLowerCase()))
                                  .filter(word -> !word.isEmpty()))
);
```

This vertex will take an item of type `Map.Entry<Integer, String>` and
split its value part into words. The key is ignored, as the line number
is not useful for the purposes of word count. There will be one item
emitted for each word, which will be the word itself. The `Traverser`
interface is a convenience designed to be used by the built-in Jet processors.

The next vertex will do the grouping of the words and emit the count
for each word. We can use the built-in `groupAndAccumulate` processor.

```java
// word -> (word, count)
Vertex accumulator = dag.newVertex("accumulator",
        groupAndAccumulate(() -> 0L, (count, x) -> count + 1)
);
```

This processor will take items of type `String`, where the item is the
word. The initial value of the count for a word is zero, and the value
is incremented by one for each time the word is encountered. The
expected output is of the type `Entry<String, Long>` where the key is
the word, and the value is the accumulated count. The processor can
only emit the final values after it has exhausted all the data.

The accumulation lambda given to the `groupAndAccumulate` processor
combines the current running count with the count from the new entry.

This vertex will do a _local_ accumulation of word counts on each member.
The next step is to do a _global_ accumulation of counts. This is the
combining step:

```java
// (word, count) -> (word, count)
Vertex combiner = dag.newVertex("combiner",
        groupAndAccumulate(Entry<String, Long>::getKey, initialZero,
                (Long count, Entry<String, Long> wordAndCount) -> count + wordAndCount.getValue())
);
```

This vertex is very similar to the previous accumulator vertex, except
we are combining two accumulated values instead of accumulated one for
each word.

The final vertex is the output &mdash; we want to store the output in
another IMap:

```java
Vertex sink = dag.newVertex("sink", Processors.mapWriter("counts"));
```

Next, we add the vertices we created to our DAG, and connect the
vertices together with edges:

```java
dag.edge(between(source, tokenizer))
   .edge(between(tokenizer, accumulator)
            .partitioned(wholeItem(), HASH_CODE))
   .edge(between(accumulator, combiner)
            .distributed()
            .partitioned(entryKey()))
   .edge(between(combiner, sink));
```

Let's take a closer look at some of the connections between the vertices.
First, source and tokenizer:

```java
.edge(between(tokenizer, accumulator)
         .partitioned(wholeItem(), HASH_CODE))
```

The edge between the tokenizer and accumulator is _partitioned_, because
all entries with the same word as key need to be processed by the same
instance of the vertex. Otherwise the same word would be duplicated
across many instances. The partitioning key is the built-in
`wholeItem()`  partitioner, and we are using the built-in `HASH_CODE` as
the partitioning function, which uses `Object.hashCode()`.

```java
.edge(between(accumulator, combiner)
         .distributed()
         .partitioned(entryKey()))
```

The edge between the `accumulator` and `combiner` is also _partitioned_,
similar to the edge between the `generator` and `accumulator`. However,
there is a key difference: the edge is also _distributed_. A
_distributed_ edge allows items to be sent to other members. Since this
edge is both partitioned and distributed, the partitioning will be across
all the members: all entries with the same word as key will be sent to
a single processor instance in the whole cluster. This ensures that we
get the correct total count for a word.

The partitioning key here is the key part of the `Map.Entry<String,
Long>`, which is the word. We are using the default partitioning
function here which uses default Hazelcast partitioning. This
partitioning function can be slightly slower than `HASH_CODE`
partitioning, but is guaranteed to return consistent results across all
JVM processes, so is a better choice for distributed edges.

To run the DAG and print out the results, we simply do the following:

```java
instance1.newJob(dag).execute().get();
System.out.println(instance1.getMap("counts").entrySet());
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

An executable version of this sample can be found at the 
[Hazelcast Jet code samples repository](https://github.com/hazelcast/hazelcast-jet-code-samples).

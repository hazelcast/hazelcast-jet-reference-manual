[TOC]

The internal vertices are where the computation takes place. Jet comes with
built-in processors for the most common types of operations.

# Map

Mapping is one of the most common operations and maps an incoming item
to one output element.

For example, a vertex which converts incoming `String` elements to
 `Integer` values can simply be written as:

```java
Vertex mapper = dag.newVertex("toInteger", Processors.map((String s) -> Integer.valueOf(s)));
```

# Filter

Filtering is another common operation, which filters incoming items
based on a predicate. Only items for which the predicate evaluates
to `true` are allowed.

An vertex which filters out odd numbers could be written as follows:

```java
Vertex filter = dag.newVertex("filterOddNumbers", Processors.filter((Integer s) -> s % 2 == 0));
```

# FlatMap

FlatMap is a generalization of [Map](#page_Map) and maps each incoming
element to zero or more elements. The flatMap processor expects a
[`Traverser`](/Advanced_Topics/Convenience_API_to_Implement_a_Processor#page_Traverser)
for each incoming item. The processor then emits all mapped values
for an item before moving on to the next.

A vertex which will split incoming lines into words can be expressed
as follows:

```java
Vertex splitWords = dag.newVertex("splitWords",
        Processors.flatMap((String line) -> Traversers.traverseArray(line.split("\\W+"))));
```

# Aggregation

The focal
point of distributed computation is solving the problem of _grouping_ by
a time window and/or grouping key and _aggregating_ the data of each
group. As we explained in the
[Hazelcast Jet 101](Getting_Started/Hazelcast_Jet_101_-_Word_Counting_Batch_Job.md)
section, aggregation can take place in a single stage or in two stages,
and there are separate variants for batch and stream jobs. The main class
with factories for built-in computational vertices is `Processors`.

The complete matrix of factories for aggregator vertices
is presented in the following table:

<table border="1">
<tr>
    <th></th>
    <th>single-stage</th>
    <th>stage 1/2</th>
    <th>stage 2/2</th>
</tr><tr>
    <th>batch,<br>no grouping</th>
    <td>aggregate()</td>
    <td>accumulate()</td>
    <td>combine()</td>
</tr><tr>
    <th>batch, group by key</th>
    <td>aggregateByKey()</td>
    <td>accumulateByKey()</td>
    <td>combineByKey()</td>
</tr><tr>
    <th>stream, group by key<br>and aligned window</th>
    <td>aggregateToSlidingWindow()</td>
    <td>accumulateByFrame()</td>
    <td>combineToSlidingWindow()</td>
</tr><tr>
    <th>stream, group by key<br>and session window</th>
    <td>aggregateToSessionWindow()</td>
    <td>N/A</td>
    <td>N/A</td>
</tr>
</table>

#### Other computation

The `Processors` class has factories for some other kinds of computation
as well. There are the simple map/filter/flatMap vertices, the
punctuation-inserting vertex for streaming jobs, and some other
low-level utilities.

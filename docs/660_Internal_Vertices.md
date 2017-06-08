The internal vertices are where the computation takes place. The focal
point of distributed computation is solving the problem of _grouping_ by
a time window and/or grouping key and _aggregating_ the data of each
group. As we explained in the
[Hazelcast Jet 101](Hazelcast_Jet_101_-_Word_Counting_Batch_Job.md)
section, aggregation can take place in a single stage or in two stages,
and there are separate variants for batch and stream jobs. The main class
with factories for built-in computational vertices is `Processors`.

#### Aggregation

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

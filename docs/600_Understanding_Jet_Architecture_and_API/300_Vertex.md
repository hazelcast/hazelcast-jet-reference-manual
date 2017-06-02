The vertex is the main unit of work in a Jet computation. Conceptually,
it receives input from its inbound edges and emits data to its outbound
edges. Practically, it is a number of `Processor` instances which
receive each of its own part of the full stream traveling over the
inbound edges, and likewise emits its own part of the full stream going
down the outbound edges.

## Edge Ordinal

An edge is connected to a vertex with a given **ordinal**, which
identifies it to the vertex and its processors. When a processor
receives an item, it knows the ordinal of the edge on which the item
came in. Things are similar on the outbound side: the processor emits an
item to a given ordinal, but also has the option to emit the same item
to all ordinals. This is the most typical case and allows easy
replication of a data stream across several edges.

In the DAG-building API the default value of the ordinal is 0. There
must be no gaps in ordinal assignment, which means a vertex will have
inbound edges with ordinals 0..N and outbound edges with ordinals 0..M.

## Local and Global Parallelism

The vertex is implemented by one or more instances of `Processor` on
each member. Each vertex can specify how many of its processors will run
per cluster member using the `localParallelism` property; every member
will have the same number of processors. A new `Vertex` instance has
this property set to `-1`, which requests to use the default value equal
to the configured size of the cooperative thread pool. The latter
defaults to `Runtime.availableProcessors()`.

The **global parallelism** of the vertex is also an important value,
especially in terms of the distribution of partitions among processors.
It is equal to local parallelism multiplied by the cluster size.

## Processor implementations provided in Jet's library

Jet's library contains factory methods for many predefined vertices.
The `com.hazelcast.jet.processor` package contains static utility
classes with factory methods that return suppliers of processors, as
required by the `dag.newVertex(name, procSupplier)` calls.

While formally there's only one kind of vertex in Jet, in practice there
is an important distinction between the following:

* A **source** is a vertex with no inbound edges. It injects data from
the environment into the Jet job.
* A **sink** is a vertex with no outbound edges. It drains the output of
the Jet job into the environment.
* An **internal** vertex has both kinds of edges. It accepts some data
from upstream vertices, performs some computation, and emits the results
to downstream vertices. Typically it doesn't interact with the
environment.

### Sources and Sinks

The main factory class for source vertices is `Sources`. It contains
sources that ingest data from Hazelcast IMDG structures like `IMap`,
`ICache`, `IList`, etc., as well as some simple sources that get data
from files and TCP sockets (`readFiles`, `streamSocket` and some more).

Paralleling the sources there's `Sinks` for the sink vertices,
supporting the same range of resources (IMDG, files, sockets). There's
also a general `writeBuffered` method that takes some boilerplate out of
writing custom sink. The user must implement a few primitives: create a
new buffer, add an item to it, flush the buffer. The provided code takes
care of integrating these primitives into the `Processor` API (draining
the inbox into the buffer and handling the general lifecycle).

In addition to these two clasess in Jet's Core module, there are modules
that connect to 3rd party resources like Kafka and Hadoop Distributed
File System (HDFS). Each such module declares a class in the same
package, `com.hazelcast.jet.processor`, exposing the module's source and
sink definitions.

### Internal vertices

The internal vertices are where the computation takes place. The focal
point of distributed computation is solving the problem of _grouping_ by
a time window and/or grouping key and _aggregating_ the data of each
group. As we explained in the 
[Hazelcast Jet 101](../400_Hazelcast_Jet_101_-_Word_Counting_Batch_Job/100_Modeling_Word_Count_in_terms_of_a_DAG.md)
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

### Punctuation policies

As mentioned in the [Hazelcast Jet 102](../035_Hazelcast_Jet_102_-_Trade_Monitoring_Streaming_Job/01_The_Stream-Processing_DAG_and_Code.md) section, determining punctuation is somewhat of a black art; it's about superimposing order over a disordered stream of events. We must decide at which point it stops making sense to wait even longer for data about past events to arrive. There's a tension between two opposing forces here:

- wait as long as possible to account for all the data;
- get results as soon as possible.

While there are ways to (kind of) achieve both, there's a significant associated cost in terms of complexity and overal performance. Hazelcast Jet takes a simple approach and strictly triages stream items into "still on time" and "late", discarding the latter. We provide some general, data-agnostic punctuation policies in the `PunctuationPolicies` class:

#### "With fixed lag"

The `withFixedLag()` policy will emit punctuation that lags behind the highest observed event timestamp by a configured amount. In other words, each time an event with the highest timestamp so far is encountered, this policy emits a punctuation item with `eventTimestamp - lag`. This puts a limit on the spread between timestamps in the stream: all events whose timestamp is more than the configured `lag` behind the highest timestamp are considered late.


#### "Limiting lag and delay"

The `limitingLagAndDelay()` policy applies the same fixed-lag logic as above and adds another limit: maximum delay from observing any item and emitting punctuation at least as large as its timestamp. A stream may experience a lull (no items arriving) and this added limit will ensure that the punctuation doesn't stay behind the highest timestamp observed before the onset of the lull. However, the skew between substreams may still cause the punctuation that reaches the downstream vertex to stay behind some timestamps. This is because the downstream will only get the lowest of all substream punctuations.

The advantage of this policy is that it doesn't assume anything about the unit of measurement used for event timestamps.

#### "Limiting lag and lull"

The `limitingLagAndLull()` policy is similar to `limitingLagAndDelay` in adressing the stream lull problem and goes a step further by addressing the issues of lull combined with skew. To achieve this it must introduce an assumption, though: that the time unit used for event timestamps is milliseconds. After a given period passes with punctuation not being advanced by the arriving data (i.e., a lull happens), it will start advancing it in lockstep with the passage of the local system time. The punctuation isn't adjusted _towards_ the local time; the policy just ensures the difference between local time and punctuation stays the same during a lull. Since the system time advances equally on all substream processors, the punctuation propagated to downstream is now guaranteed to advance regardless of the lull.

There is, however, a subtle issue with `limitingLagAndLull()`: if there is any substream that never observes an item, that substream's policy instance won't be able to initialize its "last seen timestamp" and will cause the punctuation sent to the downstream to forever lag behind all the actual data.

#### "Limiting timestamp and wall-clock lag"

The `limitingTimestampAndWallClockLag()` policy makes a stronger assumption: that the event timestamps are in milliseconds since the Unix epoch and that they are synchronized with the local time on the processing machine. It puts a limit on how much the punctuation can lag behind the local time. As long as its assumption holds, this policy gives straightforward results. It also doesn't suffer from the subtle issue with `limitingLagAndLull()`.

### Punctuation throttling

The policy objects presented above will return the "ideal" punctuation value according to their logic; however it would be too much overhead to insert a punctuation item each time the ideal punctuation advances. This is why a throttling layer should always be added on top of the baseline policy. For the purpose of sliding windows, there is an easy answer: suppress all punctuation items that belong to the same frame as the already emitted one. Such items would have no effect since the punctuation must advance beyond a frame's end for the aggregating vertex to consider the frame completed and act upon its results. The method `PunctuationPolicy#throttleByFrame()` will return a policy with this kind of throttling applied. For other cases there is `throttleByMinStep()` which suppresses punctuation items until the punctuation has advanced more than `minStep` ahead of the previously emitted one.

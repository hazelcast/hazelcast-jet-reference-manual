The word count computation can be roughly divided into three steps:

1. Read a line from the map ("source" step)
2. Split the line into words ("tokenizer" step)
3. Update the running totals for each word ("accumulator" step)

We can represent these steps as a DAG:

<img alt="Word-counting DAG" 
     src="../images/wordcount-dag.jpg"
     height="200"/>

In the simplest case the computation inside each vertex can be
executed in turn in a single-threaded environment; however just by
modeling the computation as a DAG we've split the work into isolated
steps with clear data interfaces between them. This means each vertex
can have its own thread and they can communicate over concurrent
queues:

<img alt="Word-counting DAG with concurrent queues shown" 
     src="../images/wordcount-dag-queue.jpg"
     height="200"/>

This achieves a _pipelined_ architecture: while the tokenizer is busy
with the regex work, the accumulator is updating the map using the data
the tokenizer is done with; and the source and sink stages are pumping
the data from/to the environment. Our design is now able to engage more
than one CPU core and will complete that much sooner; however we're still limited by the number of vertices. We'll be able utilize two or three cores regardless of how many are available. To move forward we must try to parallelize the work of each individual vertex.

Given that our input is an in-memory list of lines, the bottleneck occurs in the processing stages (tokenizing and accumulating). Let's first attack the tokenizing stage: it is a so-called "embarassingly parallelizable" task because the processing of each line is completely self-contained. At this point we have to make a clear distinction between the notions of _vertex_ and _processor_: there can be several processors doing the work of a single vertex. Let's add another tokenizing processor:

<img alt="Word-counting DAG with tokenizer vertex parallelized" 
     src="../images/wordcount-tokenizer.jpg"
     height="200"/>

The input processor can now use all the available tokenizers as a pool
and submit to any one whose queue has some room.

The next step is parallelizing the accumulator vertex, but this is
trickier: all occurences of the same word must go to the same
processor. The input to the accumulator must be _partitioned by word_
so that each processor is responsible for a non-overlapping subset of the
words. In Jet we'll use a _partitioned edge_ between the tokenizer and
the accumulator:

<img alt="Word-counting DAG with tokenizer and accumulator parallelized"
     src="../images/wordcount-partitioned.jpg"
     height="200"/>

As a word is emitted from the tokenizer, it goes through a
"switchboard" stage where it's routed to the correct downstream
processor. To determine where a word should be routed we can for
example calculate its hashcode and use the lowest bit to address either
accumulator 0 or accumulator 1.

At this point we have a blueprint for a fully functional parallelized
computation job which can max out all the CPU cores given enough
instances of tokenizing and accumulating processors. The next challenge
is making this work across machines.

For starters, our input can no longer be a simple in-memory list because
that would mean each machine processes the same data. To exploit a
cluster as a unified computation device, each node must observe only a
slice of the dataset. Given that a Jet instance is also a fully
functional Hazelcast instance and a Jet cluster is also a Hazelcast
cluster, the natural choice is to pre-load our data into an `IMap`, which
will be automatically partitioned and distributed between the nodes. Now
each Jet node can just read the slice of data that was stored locally on
it.

When run in a cluster, Jet will instantiate a replica of the whole DAG on
each node. On a two-member cluster there will be two source processors, four
tokenizers, and so on. The trickiest part is the partitioned edge between
tokenizer and accumulator: each accumulator is supposed to receive its
own subset of words. That means that, for example, a word emitted from
tokenizer 0 will have to travel across the network to reach accumulator
3, if that's the one that happens to own it. On average we can expect
every other word to need network transport, causing both serious network
traffic and serialization/deserialization CPU load.

There is a simple trick we can employ to avoid most of this traffic:
we'll make our accumulators process only local data, coming up with local
word counts, and we'll introduce another vertex downstream of
accumulator, called the _combiner_, which will just total up the local
counts. This way we reduce traffic from O(totalWords) to
O(distinctWords). Given that there are only so many words in a language,
this is in fact a reduction from O(n) in input size to O(1). The more
text we process, the larger our savings in network traffic.

Jet distinguishes between _local_ and _distributed_ edges, so we'll use a _local partitioned_ edge for tokenizer->accumulator and a _distributed partitioned_ edge for accumulator->combiner. With this move we've finalized our DAG design, which can be illustrated by the following diagram:

<img alt="Word-counting DAG parallelized and distributed" 
     src="../images/wordcount-distributed.jpg"
     height="420"/>

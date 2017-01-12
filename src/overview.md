# Jet Overview

At the core of Jet is a DAG execution engine. It is built on top of Hazelcast IMDG. For more information about Hazelcast IMDG,
see the [latest Hazelcast reference manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html).

## Cluster

Jet is a distributed processing engine and is meant to run on several machines, although it is also possible to run
 it on a single node for testing purposes. There are several ways to configure the nodes for discovery, which are explained in detail
 in the [Hazelcast reference manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#setting-up-clusters).

## Directed Acyclic Graph

A computation in Jet is represented by a directed acyclic graph(DAG) of vertices. Each vertex performs a step in the computation 
and emits some output the vertices it is connected to. Edges between the vertices determine how the data is forwarded between them.

The DAG is replicated and executed on each node in the cluster. As such, Jet will work best when the data source or sources can be partitioned,
so that each node can do a part of the work. 

### Vertices

A vertex represents a step in the computation, and simply consists of a name and a `Processor` class. The `parallelism` property
controls how many actual `Processor` instances will be created.

### Processors

Each Vertex consists of several identical `Processor` instances, controlled by the `parallelism` attribute of the Vertex. 
A `Processor` is the basic computational unit in a DAG. 

During a `Job` execution, each node in a Jet cluster will create multiple `Processor`s for a single vertex
 and each `Processor` instance will operate on a subset of the data.

As a generalization, a single `Processor` instance is responsible for transforming zero or more input streams 
into zero or more output streams. This means that a `Processor` can act as a producer, consumer, or an intermediate step.

`AbstractProcessor` class provides some basic infrastructure for writing Processor implementations. 

A trivial `Processor` implementation might look like this:

```java
public class PlusOneProcessor extends AbstractProcessor {
    @Override
    protected boolean tryProcess(int ordinal, Object item) {
        emit((int)item + 1);
        return true;
    }
}
```

The processor given above will simply add one the input, and emit it forwards.

Processors can be stateful and do need need to be thread-safe, since a single instance will always be called by a single thread at a time.

### Cooperative multithreading

Cooperative multithreading is one of the core concepts of Jet, and can be roughly compared to [green threads][https://en.wikipedia.org/wiki/Green_threads].

There are two kinds of processors in Jet: cooperative and non-cooperative.

#### Cooperative Processors

Cooperative processors are the main Processor type for performing computations. They share a fixed size pool of cooperative threads 
and each processor takes a bit of input, does a little bit of work, and then yields. This allows to run thousands of
 cooperative processors instances on a few threads with minimal overhead. 

It is critical these processors do not perform any blocking operations and that they do not perform computations that would take longer than miliseconds.

#### Non-cooperative Processors

Sometimes a processor will need to do a blocking operation, such as reading from a file, 
or retrieving something over the network. In these cases it might not be possible to implement these Processors in a cooperative way. 
By declaring them as non-cooperative, Jet will allocate a dedicated Java thread for them, and allow other cooperative processors
 to proceed unhindered during blocking operation.

### Edges

Edges control how the data flows from one vertex to the other. As there might be several `Processor` instances in the source 
and the destination, sometimes it's necessary to control how the items are mapped between the Processors. There are different
types of edges which control which item is forwarded to which instance of processor.

The types of edges are detailed in the [edge types](#edge-types) section.
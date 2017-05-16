Vertex is the main unit of work in a Jet computation. Conceptually, it
receives input from its inbound edges and emits data to its outbound
edges. Practically, it is a number of `Processor` instances which
receive each of its own part of the full stream traveling over the
inbound edges, and likewise emits its own part of the full stream going
down the outbound edges.

### Edge Ordinal

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

### Source and Sink

Jet uses only one kind of vertex, but in practice there is an important
distinction between the following:

* **internal** vertex which accepts input and transforms it into output,
* **source** vertex which generates output without receiving anything,
* **sink** vertex which consumes input and does not emit anything.

Sources and sinks must interact with the environment to store/load data,
making their implementation more involved compared to the internal
vertices, whose logic is self-contained.

### Local and Global Parallelism

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

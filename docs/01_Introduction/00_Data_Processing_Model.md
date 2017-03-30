Hazelcast Jet provides high performance in-memory data processing by
modeling a computation as a _Directed Acyclic Graph (DAG)_ of processing
vertices. Each _vertex_ performs a step in the computation and emits
data items for the vertices it is connected to. A single vertex's
computation work is performed in parallel by many instances of the
`Processor` type around the cluster. The different vertices are linked
together through _edges_.

One of the major reasons to divide the full computation task into
several vertices is _data partitioning_: the ability to split the data
stream traveling over an edge into slices which can be processed
independently of each other. To make this work, a function must be
defined which computes the _partitioning key_ for each item and makes
all related items map to the same key. The computation engine can then
route all such items to the same processor instance. This makes it easy
to parallelize the computation: each processor will have the full
picture for its slice of the entire stream.

Edges determine how the data is routed from individual source processors
to individual destination processors. Different edge properties offer
precise control over the flow of data.
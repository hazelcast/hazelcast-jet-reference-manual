# Introduction

Hazelcast Jet is a distributed data processing engine, built for high-performance batch and stream processing.
 It is built on top of Hazelcast In Memory Data Grid (IMDG) at its foundation, but is a completely separate product,
with features not available in Hazelcast.

Jet also introduces distributed `java.util.stream` support for Hazelcast IMDG data structures, such as `IMap` and `IList`.

## Data Processing Model

Jet provides high performance in memory data processing by modelling a computation as a _Directed Acyclic Graph (DAG)_ of processing 
vertices. Each _vertex_ is responsible for a step of the computation and these vertices are linked together through _edges_.

The DAG models the dependencies between the steps in the computation and is flexible enough to model almost any computation.







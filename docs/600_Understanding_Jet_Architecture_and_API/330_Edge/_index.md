An edge represents a link between two vertices in the DAG. Conceptually,
data flows between two vertices along an edge; practically, each
processor of the upstream vertex contributes to the overall data stream
over the edge and each processor of the downstream vertex receives a
part of that stream. For any given pair of vertices, there can be at
most one edge between them.

Several properties of the `Edge` control the routing from upstream to
downstream processors.
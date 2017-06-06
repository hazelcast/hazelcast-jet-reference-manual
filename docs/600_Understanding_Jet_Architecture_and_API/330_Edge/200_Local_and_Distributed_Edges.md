A major choice to make in terms of data routing is whether the candidate
set of target processors is unconstrained, encompassing all processors
across the cluster, or constrained to just those running on the same
cluster member. This is controlled by the `distributed` property of the
edge. By default the edge is local and calling the `distributed()`
method removes this restriction.

With appropriate DAG design, network traffic can be minimized by
employing local edges. Local edges are implemented with the most
efficient kind of concurrent queue: single-producer, single-consumer
bounded queue. It employs wait-free algorithms on both sides and avoids
`volatile` writes by using `lazySet`.
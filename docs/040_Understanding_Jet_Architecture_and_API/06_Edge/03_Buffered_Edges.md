In some special cases, unbounded data buffering must be allowed on an
edge. Consider the following scenario:

A vertex sends output to two edges, creating a fork in the DAG. The
branches later rejoin at a downstream vertex which assigns different
priorities to its two inbound edges. Since the data for both edges is
generated simultaneously, and since the lower-priority edge will apply
backpressure while waiting for the higher-priority edge to be consumed
in full, the upstream vertex will not be allowed to emit its data and a
deadlock will occur. The deadlock is resolved by activating the unbounded
buffering on the lower-priority edge.
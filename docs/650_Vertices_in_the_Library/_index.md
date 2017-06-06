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

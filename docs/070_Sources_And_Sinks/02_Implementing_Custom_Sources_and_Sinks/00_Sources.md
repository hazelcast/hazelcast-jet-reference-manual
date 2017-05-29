One of the main concerns when writing custom sources is that the source
is typically distributed across multiple machines and partitions, and
the work needs to be distributed across multiple members and processors.

Jet provides a flexible `ProcessorMetaSupplier` and `ProcessorSupplier`
API which can be used to control how a source is distributed across the
network.

The procedure for generating `Processor` instances is as follows:

1. The `ProcessorMetaSupplier` for the `Vertex` is serialized and sent to
the coordinating member.
2. The coordinator calls `ProcessorMetaSupplier.get()` once for each member
in the cluster and a `ProcessorSupplier` is created for each member.
3. The `ProcessorSupplier` for each member is serialized and sent to that
member.
4. Each member will call its own `ProcessorSupplier` with the correct
`count` parameter, which corresponds to the `localParallelism` setting of
that vertex.
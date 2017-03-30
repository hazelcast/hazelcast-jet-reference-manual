These are the steps taken to create and initialize a Jet job:

1. The user builds the DAG and submits it to the local Jet client instance.
2. The client instance serializes the DAG and sends it to a member of
the Jet cluster. This member becomes the **coordinator** for this Jet job.
3. The coordinator deserializes the DAG and builds an execution plan for
each member.
4. The coordinator serializes the execution plans and distributes each to
its target member.
5. Each member acts upon its execution plan by creating all the needed
tasklets, concurrent queues, network senders/receivers, etc.
6. The coordinator sends the signal to all members to start job execution.

The most visible consequence of the above process is the
`ProcessorMetaSupplier` type: you must provide one for each
`Vertex`. In Step 3, the coordinator deserializes the meta-supplier as a
constituent of the `DAG` and asks it to create `ProcessorSupplier`
instances which go into the execution plans. A separate instance of
`ProcessorSupplier` is created specifically for each member's plan. In
Step 4, the coordinator serializes these and sends each to its member. In
Step 5 each member deserializes its `ProcessorSupplier` and asks it to
create as many `Processor` instances as configured by the vertex's
`localParallelism` property.

This process is so involved because each `Processor` instance may need
to be differently configured. This is especially relevant for processors
driving a source vertex: typically each one will emit only a slice of
the total data stream, as appropriate to the partitions it is in charge
of.

### ProcessorMetaSupplier

This type is designed to be implemented by the user, but the
`Processors` utility class provides implementations covering most cases.
You may need custom meta-suppliers primarily to implement a
custom data source or sink. Instances of this type are serialized and
transferred as a part of each `Vertex` instance in a `DAG`. The
**coordinator** member deserializes it to retrieve `ProcessorSupplier`s.
Before being asked for `ProcessorSupplier`s, the meta-supplier is given
access to the Hazelcast instance so it can find out the parameters of
the cluster the job will run on. Most typically, the meta-supplier in
the source vertex will use the cluster size to control the assignment of
data partitions to each member.

### ProcessorSupplier

Usually this type will be custom-implemented in the same cases where its
meta-supplier is custom-implemented and complete the logic of a
distributed data source's partition assignment. It supplies instances of
`Processor` ready to start executing the vertex's logic.

Please see
the [Implementing Custom Sources and Sinks section](/07_Implementing_Custom_Sources_and_Sinks)
for more guidance on how these interfaces can be implemented.

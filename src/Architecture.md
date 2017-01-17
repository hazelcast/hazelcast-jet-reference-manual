# Architecture Overview

## Create and execute a job

These are the steps taken to create and execute a Jet job:

1. User builds the DAG and submits it to the local Jet client
instance.
1. The client instance serializes the DAG and sends it to a member of
the Jet cluster. This member becomes the _coordinator_ for this Jet job.
1. Coordinator deserializes the DAG and builds an execution
plan for each member.
1. Coordinator serializes the execution plans and distributes each to
its target member.
1. Each member acts upon its execution plan by creating all the needed
tasklets, concurrent queues, network senders/receivers, etc.
1. Coordinator sends the signal to all members to start job execution.

The most visible consequence of the above process is the
`ProcessorMetaSupplier` type: the user must provide one for each
`Vertex`. In step 3 the coordinator deserializes the meta-supplier and
asks it to create `ProcessorSupplier` instances, one per cluster member.
In step 4 it serializes these and sends each to its member. In step 5
each member deserializes its `ProcessorSupplier` and asks it to create
as many `Processor` instances as configured by the vertex's
`localParallelism` property.

This process is so involved because each `Processor` instance may need
to be differently configured. This is especially relevant for processors
driving a source vertex: typically each one will emit only a slice of
the total data stream, as appropriate to the partitions it is in charge
of.

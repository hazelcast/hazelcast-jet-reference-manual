`Processor` is the main type whose implementation is up to the user: it
contains the code of the computation to be performed by a vertex. There
are a number of Processor building blocks in the Jet API which allow you
to just specify the computation logic, while the provided code
handles the processor's cooperative behavior. Please refer to the
[AbstractProcessor section](/04_Understanding_Jet_Architecture_and_API/05_Convenience_API_to_Implement_a_Processor/00_AbstractProcessor.md).

A processor's work can be conceptually described as follows: "receive
data from zero or more input streams and emit data into zero or more
output streams." Each stream maps to a single DAG edge (either inbound
or outbound). There is no requirement on the correspondence between
input and output items; a processor can emit any data it sees fit,
including none at all. The same `Processor` abstraction is used for all
kinds of vertices, including sources and sinks.
Like the sources, sinks are just another kind of `Processor`s. Sinks are
typically implemented as `AbstractProcessor` and implement the
`tryProcess` method, and write each incoming item to the sink.
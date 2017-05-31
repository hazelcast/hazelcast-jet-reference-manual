By default, the functional interfaces which were added to
`java.util.function` are not serializable. In a distributed system, the
defined lambdas need to be serialized and sent to the other members. Jet
includes the serializable version of all the interfaces found in the
`java.util.function` which can be accessed using the
`com.hazelcast.jet.stream.Distributed` class.
You may see the following exceptions thrown when working with Jet:

* [`JetException`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/JetException.html):
General exception that will be thrown in job failure and will have the
original exception as the `cause` field.
* [`TopologyChangedException`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/core/TopologyChangedException.html):
This exception is thrown when a member participating in a job leaves the
cluster. Job will typically be restarted automatically without throwing
the exception to the user if auto-restart is enabled.
* [`JobNotFoundException`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/core/JobNotFoundException.html):
Thrown when the coordinator node is not able to find the metadata for a
given job.

Furthermore, there are several Hazelcast exceptions that might be thrown
when interacting with `JetInstance`. For description of Hazelcast IMDG
exceptions, please refer to the [IMDG Reference manual](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#common-exception-types).

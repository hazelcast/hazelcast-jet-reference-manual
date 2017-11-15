
Hazelcast Jet is distributed using the
<a href="http://www.apache.org/licenses/LICENSE-2.0" target="_blank">Apache License 2</a>,
therefore permissions are granted to use, reproduce and distribute it
along with any kind of open source and closed source applications.

Depending on the used feature-set, Hazelcast Jet has certain runtime
dependencies which might have different licenses. Following are
dependencies and their respective licenses.

## Embedded Dependencies

Embedded dependencies are merged (shaded) with the Hazelcast Jet codebase
at compile-time. These dependencies become an integral part of the
Hazelcast Jet distribution.

For license files of embedded dependencies, please see the `license`
directory of the Hazelcast Jet distribution, available at our
<a href="https://jet.hazelcast.org/download/" target="_blank">download page</a>.

### minimal-json

minimal-json is a JSON parsing and generation library which is a part of
the Hazelcast Jet distribution. It is used for communication
between the Hazelcast Jet cluster and the Management Center.

minimal-json is distributed under the <a href="http://opensource.org/licenses/MIT" target="_blank">MIT license</a> and offers the same rights to add, use,
modify, and distribute the source code as the Apache License 2.0 that Hazelcast uses. However, some other restrictions might apply.

## Runtime Dependencies

Depending on the used features, additional dependencies might be added
to the dependency set. Those runtime dependencies might have
other licenses. See the following list of additional runtime dependencies.

### Apache Hadoop

Hazelcast integrates with Apache Hadoop and can use it as a data
 sink or source. Jet has a dependency on the libraries required to
 read from and write to the Hadoop File System.

Apache Hadoop is distributed under the terms of the <a href="http://www.apache.org/licenses/LICENSE-2.0" target="_blank">Apache License 2</a>.

### Apache Kafka

Hazelcast integrates with Apache Kafka and can make use of it as a
data sink or source. Hazelcast has a dependency on Kafka client
libraries.

Apache Kafka is distributed under the terms of the <a href="http://www.apache.org/licenses/LICENSE-2.0" target="_blank">Apache License 2</a>.

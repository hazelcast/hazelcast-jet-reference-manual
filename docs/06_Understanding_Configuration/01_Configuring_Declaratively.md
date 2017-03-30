It is also possible to configure Jet through XML files when a
`JetInstance` is created without any explicit `JetConfig` file. Jet will
look for a configuration file in the following order:

1. Check the system property `hazelcast.jet.config`. If the value is set,
and starts with `classpath:`, then it will be treated as a classpath
resource. Otherwise, it will be treated as a file reference.
2. Check for the presence of `hazelcast-jet.xml` in the working directory.
3. Check for the presence of `hazelcast-jet.xml` in the classpath.
4. If all the above checks fail, then the default XML
configuration will be loaded.

An example configuration looks like the following:

```xml
<hazelcast-jet xsi:schemaLocation="http://www.hazelcast.com/schema/jet-config hazelcast-jet-config-0.3.xsd"
               xmlns="http://www.hazelcast.com/schema/jet-config"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <instance>
        <!-- number of threads to use for DAG execution -->
       <cooperative-thread-count>8</cooperative-thread-count>
        <!-- frequency of flow control packets, in milliseconds -->
       <flow-control-period>100</flow-control-period>
        <!-- working directory to use for placing temporary files -->
       <temp-dir>/var/tmp/jet</temp-dir>
    </instance>
    <properties>
       <property name="custom.property">custom property</property>
    </properties>
    <edge-defaults>
        <!-- number of available slots for each concurrent queue between two vertices -->
       <queue-size>1024</queue-size>

        <!-- number of slots before high water is triggered for the outbox -->
       <high-water-mark>2048</high-water-mark>

        <!-- maximum packet size in bytes, only applies to distributed edges -->
       <packet-size-limit>16384</packet-size-limit>

        <!-- target receive window size multiplier, only applies to distributed edges -->
       <receive-window-multiplier>3</receive-window-multiplier>
    </edge-defaults>
    <!-- custom properties which can be read within a ProcessorSupplier -->
</hazelcast-jet>
```

The following table lists the configuration elements for Hazelcast Jet:

|Name|Description|Default Value
|-|-|
|Cooperative Thread Count|Maximum number of cooperative threads to be used for execution of jobs.|`Runtime.getRuntime().availableProcessors()`
|Temp Directory| Directory where temporary files will be placed, such as JAR files submitted by clients.|Jet will create a temp directory, which will be deleted on exit.
|Flow Control Period| While executing a Jet job there is the issue of regulating the rate at which one member of the cluster sends data to another member. The receiver will regularly report to each sender how much more data it is allowed to send over a given DAG edge. This option sets the length (in milliseconds) of the interval between flow-control packets.|100ms
|Edge Defaults|The default values to be used for all edges.|Please see the [Tuning Edges section](../04_Understanding_Jet_Architecture_and_API/06_Edge/04_Tuning_Edges.md).
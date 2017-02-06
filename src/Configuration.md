# Understanding Configuration

You can configure Hazelcast Jet either programmatically or declaratively (XML).

## Configuring Programmatically

Programmatic configuration is the simplest way to configure Jet. For
example, the following will configure Jet to use only two threads
for cooperative execution:

```java
JetConfig config = new JetConfig();
config.getInstanceConfig().setCooperativeThreadCount(2);
JetInstance jet = Jet.newJetInstance(config);
```

Any XML configuration files that might be present will be ignored when
programmatic configuration is used.

## Configuring Declaratively

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

<table>
    <tr>
      <th>Name</th>
      <th>Description</th>
      <th>Default Value</th>
    </tr>
    <tr>
        <td>Cooperative Thread Count</td>
        <td>
            Maximum number of cooperative threads to be used for execution of jobs.
        </td>
        <td>`Runtime.getRuntime().availableProcessors()`</td>
    </tr>
    <tr>
        <td>Temp Directory</td>
        <td>
            Directory where temporary files will be placed, such as JAR files
            submitted by clients.
        </td>
        <td>Jet will create a temp directory, which will be deleted on exit.</td>
    </tr>
    <tr>
        <td>Flow Control Period</td>
        <td>
            While executing a Jet job there is the issue of regulating the rate at
            which one member of the cluster sends data to another member. The
            receiver will regularly report to each sender how much more data it
            is allowed to send over a given DAG edge. This option sets the
            length (in milliseconds) of the interval between flow-control
            packets.
        </td>
        <td>100ms</td>
    </tr>
    <tr>
        <td>Edge Defaults</td>
        <td>
            The default values to be used for all edges.
        </td>
        <td>[See Per Edge Configuration Options](#tuning-edges)</td>
    </tr>    
</table>



## Configuring Underlying Hazelcast Instance

Each Jet member or client, will have a respective underlying Hazelcast
member or client. Please refer to the [Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#understanding-configuration) for specific configuration options for Hazelcast
IMDG.

### Programmatic

The underlying Hazelcast IMDG member can be configured as follows:

```java
JetConfig jetConfig = new JetConfig();
jetConfig.getHazelcastConfig().getGroupConfig().setName("test");
JetInstance jet = Jet.newJetInstance(jetConfig);
```

The underlying Hazelcast IMDG client can be configured as follows:


````java
ClientConfig clientConfig = new ClientConfig();
clientConfig.getGroupConfig().setName("test");
JetInstance jet = Jet.newJetClient(clientConfig);
````
### Declarative

The underlying Hazelcast IMDG configuration can also be updated declaratively.
Please refer to the [Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#configuring-declaratively) for information on how to do this.


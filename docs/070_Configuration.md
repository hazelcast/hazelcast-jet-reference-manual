You can configure Hazelcast Jet either programmatically or declaratively (XML).

## Programmatic Configuration

Programmatic configuration is the simplest way to configure Jet. You instantiate a
[`JetConfig`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/config/JetConfig.html)
object and set the desired properties. For example, the following will
configure Jet to use only two threads for cooperative execution:

```java
JetConfig config = new JetConfig();
config.getInstanceConfig().setCooperativeThreadCount(2);
JetInstance jet = Jet.newJetInstance(config);
```

## Declarative Configuration

If you don't pass an explicit `JetConfig` object when constructing a Jet
instance, it will look for an XML configuration file in the following
locations (in that order):

1. Check the system property `hazelcast.jet.config`. If the value is set
   and starts with `classpath:`, Jet treats it as a classpath resource.
   Otherwise it treats it as a file pathname.
2. Check for the presence of `hazelcast-jet.xml` in the working
   directory.
3. Check for the presence of `hazelcast-jet.xml` in the classpath.
4. If all the above checks fail, then Jet loads the default XML
   configuration that's packaged in the Jet JAR file.

An example configuration looks like the following:

```xml
<hazelcast-jet xsi:schemaLocation="http://www.hazelcast.com/schema/jet-config hazelcast-jet-config-0.5.xsd"
               xmlns="http://www.hazelcast.com/schema/jet-config"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <instance>
        <!-- number of threads to use for DAG execution -->
       <cooperative-thread-count>8</cooperative-thread-count>
        <!-- frequency of flow control packets, in milliseconds -->
       <flow-control-period>100</flow-control-period>
        <!-- working directory to use for placing temporary files -->
       <temp-dir>/var/tmp/jet</temp-dir>
        <!-- number of backups for job specifics maps -->
       <backup-count>1</backup-count>
    </instance>
    <properties>
       <property name="custom.property">custom property</property>
    </properties>
    <edge-defaults>
        <!-- number of available slots for each concurrent queue between two vertices -->
       <queue-size>1024</queue-size>

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
|Backup Count|Sets the number of synchronous backups for storing job metadata and snapshots. Maximum allowed value is 6.|1|
|Edge Defaults|The default values to be used for all edges.|Please see the section on [Tuning Edges](/Expert_Zone_—_The_Core_API/DAG#page_Fine-Tuning+Edges).

## Configure the Underlying Hazelcast Instance

Each Jet member or client has its underlying Hazelcast member or client. Please refer to the
[Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#understanding-configuration)
for specific configuration options for Hazelcast IMDG.

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

Hazelcast IMDG can also be configured declaratively as well.
Please refer to the [Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#configuring-declaratively)
for information on how to do this.

# Configuration

It is possible to configure Jet either programmatically or declaratively
using XML configuration.

## Programmatic Configuration

Programmatic configuration is the simplest way to configure Jet. For
example, the following will configure Jet to use only two threads
for cooperative execution.

```java
JetConfig config = new JetConfig();
config.getInstanceConfig().setCooperativeThreadCount(2);
JetInstance jet = Jet.newJetInstance(config);
```

Any XML configuration files that might be present will be ignored when
programmatic configuration is used.

## Declarative Configuration (XML)

It is also possible to configure Jet through XML files when a
`JetInstance` is created without any explicit `JetConfig` file. Jet will
look for a configuration file in the following order:

1. Check system property `hazelcast.jet.config`. If the value is set,
and starts with `classpath:`, then it will be treated as a classpath
resource, otherwise it will be treated as a file reference.
2. Check for presence of `hazelcast-jet.xml` in the working directory.
3. Check for presence of `hazelcast-jet.xml` in the classpath.
4. If none of these options are specified, then the default XML
configuration will be loaded.

TODO: link to sample XML configuration

## Configuring underlying Hazelcast instance

Each Jet member or client, will have a respective underlying Hazelcast
member or client. For specific configuration options for Hazelcast
IMDG, see the [Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#understanding-configuration)

### Programmatic

The underlying IMDG configuration can be configured as follows:

#### Member

```java
JetConfig jetConfig = new JetConfig();
jetConfig.getHazelcastConfig().getGroupConfig().setName("test");
JetInstance jet = Jet.newJetInstance(jetConfig);
```
#### Client

````java
ClientConfig clientConfig = new ClientConfig();
clientConfig.getGroupConfig().setName("test");
JetInstance jet = Jet.newJetClient(clientConfig);
````
### Declarative

The underlying IMDG configuration can also be updated declaratively.
More information about how this is done is available in the
[Hazelcast Reference Manual](http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#configuring-declaratively)

## List of Options

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

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
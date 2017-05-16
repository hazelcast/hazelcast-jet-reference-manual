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
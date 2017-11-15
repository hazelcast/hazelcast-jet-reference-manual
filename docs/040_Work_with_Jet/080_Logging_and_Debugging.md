[TOC]

## Configuring Logging

Jet, like Hazelcast IMDG does not depend on a specific logging framework
and has built-in adapters for a variety of logging frameworks. You can
also write a new adapter to integrate with loggers Jet doesn't natively
support. To use one of the built-in adapters, set the
`hazelcast.logging.type` property to one of the following:

* `jdk`: java.util.logging (default)
* `log4j`: Apache Log4j
* `log4j2`: Apache Log4j 2
* `slf4j`: SLF4J
* `none`: Turn off logging

For example, to configure Jet to use Log4j, you can do one of the following:

```java
System.setProperty("hazelcast.logging.type", "log4j");
```

or

```java
JetConfig config = new JetConfig() ;
config.getHazelcastConfig().setProperty( "hazelcast.logging.type", "log4j" );
```

For more detailed information about how to configure logging, please
refer to the
[IMDG reference manual](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#logging-configuration).

## Inspecting Output of Individual Stages

When building pipelines, it's often useful to see what the  output of
each stage is. This can be achieved by using the
[`peek()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/ComputeStage.html#peek--)
stage. For example:

```java
p.drawFrom(Sources.<Long, String>map(...))
   .flatMap(e -> traverseArray(delimiter.split(e.getValue().toLowerCase())))
   .filter(word -> !word.isEmpty())
   .groupBy(wholeItem(), counting())
   .peek()
   .drainTo(Sinks.map(COUNTS));
```

In this pipeline, the output of the `groupBy` stage will be logged using
the configured logging framework:

```
16:05:29,650  INFO || - [com.hazelcast.jet.impl.processor.PeekWrappedP.groupByKey.cd6373be.stage2#0] hz._hzInstance_1_jet.jet.cooperative.thread-1 - [10.0.1.3]:5701 [jet] [0.6-SNAPSHOT] Output to 0: accusers=6
16:05:29,650  INFO || - [com.hazelcast.jet.impl.processor.PeekWrappedP.groupByKey.cd6373be.stage2#0] hz._hzInstance_1_jet.jet.cooperative.thread-1 - [10.0.1.3]:5701 [jet] [0.6-SNAPSHOT] Output to 0: mutability=2
16:05:29,650  INFO || - [com.hazelcast.jet.impl.processor.PeekWrappedP.groupByKey.cd6373be.stage2#0] hz._hzInstance_1_jet.jet.cooperative.thread-1 - [10.0.1.3]:5701 [jet] [0.6-SNAPSHOT] Output to 0: lovely=53
```

The logger name of
`com.hazelcast.jet.impl.processor.PeekWrappedP.groupByKey.cd6373be.stage2#0`
can be decomposed as follows:

* `com.hazelcast.jet.impl.processor.PeekWrappedP`: class of the processor
writing the log message
* `groupByKey.cd6373be.stage2`: Name of the vertex the processor belongs
to
* `#0`: the unique index (per vertex) of the processor instance

Keep in mind that this can create a large amount of output when dealing
with large volumes of data, and should strictly be used in a
non-production environment.

For more information about logging when using the Core API, see the
[Best Practices](The_Core_API/Best_Practices#page_Inspecting+Processor+Input+and+Output)
section.

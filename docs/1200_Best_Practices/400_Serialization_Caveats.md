Creating a DAG for Jet usually involves writing *lambda expressions*.
Because the DAG is sent to the cluster members in serialized form,
the lambda expressions must be serializable. To somewhat alleviate the
inconvenience of this requirement, Jet declares the package
`com.hazelcast.jet.function` with all the functional interfaces from
`java.util.function` subtyped and made `Serializable`. Each subtype has
the name of the original with `Distributed` prepended. For example, a
`DistributedFunction` is just like `Function`, but implements
`Serializable`. Java has explicit support for lambda target types that
implement `Serializable`. There are several caveats, however.

### Lambda variable capture

If the lambda references a variable in the outer scope, the variable is
captured and must also be serializable. If it references an instance
variable of the enclosing class, it implicitly captures `this` so the
entire class will be serialized. For example, this will fail because
`JetJob` doesn't implement `Serializable`:

```java
public class JetJob {
    private String instanceVar;
    
    public DAG buildDag() {
        DAG dag = new DAG();
        
        // Refers to instanceVar, capturing "this", but JetJob is not
        // Serializable so this call will fail.
        dag.newVertex("filter", filter(item -> instanceVar.equals(item)));
    }
}
```

Just adding `implements Serializable` to `JetJob` would be a viable workaround here. However, consider something just a bit different:

```java
public class JetJob implements Serializable {
    private String instanceVar;
    private OutputStream fileOut;
    
    public DAG buildDag() {
        DAG dag = new DAG();
        
        // Refers to instanceVar, capturing "this". JetJob is declared
        // Serializable, but has a non-serializable field and this fails.
        dag.newVertex("filter", filter(item -> parameter.equals(item)));
    }
}
```

Even though we never refer to `fileOut`, we are still capturing the
entire `JetJob` instance. We might mark `fileOut` as `transient`, but
the sane approach is to avoid referring to instance variables of the
surrounding class. This can be simply achieved by assigning to a local
variable, then referring to that variable inside the lambda:

```java
public class JetJob {
    private String instanceVar;
    
    public DAG buildDag() {
        DAG dag = new DAG();
        String findMe = instanceVar;
        // By referring to the local variable "findMe" we avoid
        // capturing "this" and the job runs fine.
        dag.newVertex("filter", filter(item -> findMe.equals(item)));
    }
}
```

Another common pitfall is capturing an instance of `DateTimeFormatter`
or a similar non-serializable class:

```java
DateTimeFormatter formatter = 
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                     .withZone(ZoneId.systemDefault());

// Captures the non-serializable formatter, so this fails
dag.newVertex("map", map((Long tstamp) -> 
    formatter.format(Instant.ofEpochMilli(tstamp))));
```

Sometimes we can get away by using one of the preconfigured formatters
available in the JDK:

```java
// Accesses the static final field ISO_LOCAL_TIME. Static fields are
// not subject to lambda capture, they are dereferenced when the code
// runs on the target machine.
dag.newVertex("map", map((Long tstamp) -> 
    DateTimeFormatter.ISO_LOCAL_TIME.format(
        Instant.ofEpochMilli(tstamp).atZone(ZoneId.systemDefault()))));
```

This refers to a `static final` field in the JDK, so the instance is available on any JVM. A similar approach is to declare our own `static final` field; however in that case we must add the declaring class as a job resource:

```java
public class JetJob {

    // Our own static field
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                             .withZone(ZoneId.systemDefault());

    DAG buildDag() {
        DAG dag = new DAG();
        dag.newVertex("map", Processors.map((Long tstamp) ->
                formatter.format(Instant.ofEpochMilli(tstamp))));
        return dag;
    }

    // The job will fail unless we attach the JetJob class as a
    // resource, making the formatter instance available at the 
    // target machine.
    void runJob(JetInstance jet) throws Exception {
        JobConfig c = new JobConfig();
        c.addClass(JetJob.class);
        jet.newJob(buildDag(), c).execute().get();
    }
}
```

An approach that is self-contained is to instantiate the
non-serializable class just in time, inside the processor supplier:

```java
// This lambda captures nothing and creates its own formatter when
// executed.
dag.newVertex("map", () -> {
    DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                             .withZone(ZoneId.systemDefault());
    return Processors.map((Long tstamp) ->
            formatter.format(Instant.ofEpochMilli(tstamp))).get();
});
```

Note the `.get()` at the end: this retrieves the processor from the Jet-provided processor supplier and returns it from our custom-declared supplier.

### Serialization performance

When it comes to serializing the description of a Jet job, performance
is not critical. However, when the job executes, every distributed edge
in the DAG will cause stream items to be serialized and sent over the
network. In this context the performance of Java serialization is so
poor that it regularly becomes the bottleneck. This is due to its heavy
usage of reflection, overheads in the serialized form, etc.

Since Hazelcast IMDG faced the same problem a long time ago, we have
mature support for optimized custom serialization and in Jet you can
use it for stream data. In essence, you must implement a
`StreamSerializer` for the objects you emit from your processors and
register it in Jet configuration:

```java
SerializerConfig serializerConfig = new SerializerConfig()
        .setImplementation(new MyItemSerializer())
        .setTypeClass(MyItem.class);
JetConfig config = new JetConfig();
config.getHazelcastConfig().getSerializationConfig()
      .addSerializerConfig(serializerConfig);
JetInstance jet = Jet.newJetInstance(config);
```

Consult the chapter on
[custom serialization](http://docs.hazelcast.org/docs/3.8.1/manual/html-single/index.html#custom-serialization)
in Hazelcast IMDG's reference manual for more details.

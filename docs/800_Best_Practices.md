## Jobs

- All the code and state needed for the Jet job must be declared in the
classes that become a part of the job's definition through
`JobConfig.addClass()` or `addJar()`.

- If you have a client connecting to your Jet cluster, the Jet job
should never have to refer to `ClientConfig`. Create a separate
`DagBuilder` class using the `buildDag()` method; this class should not
have any references to the `JobHelper` class.

- You should have a careful control over the object graph which is
submitted with the Jet job. Please be aware that inner classes/lambdas
may inadvertently capture their parent classes which will cause
serialization errors.

## Packaging the Job

One way to easily submit the job to a Jet cluster is by using the
`submit-job.sh` script (`submit-job.bat` on Windows).

The main issue with achieving this is that the JAR must be attached as a
resource to the job being submitted, so the Jet cluster will be able to
load and use its classes. However, from within a running `main()` method
it is not trivial to find out the filename of the JAR containing it.

To use the `submit-job` script, follow these steps:

* Write your `main()` method and your Jet code the usual way, except
for calling `JetBootstrap.getInstance()` to acquire a Jet client
instance (instead of `Jet.newJetClient()`).

* Create a runnable JAR with your entry point declared as the
`Main-Class` in `MANIFEST.MF`.

* Run your JAR, but instead of `java -jar jetjob.jar` use `submit-jet.sh
jetjob.jar`. The script is found in the Jet distribution zipfile, in the
`bin` directory. On Windows use `submit-jet.bat`.

* The Jet client will be configured from `hazelcast-client.xml` found in
the `config` directory in Jet's distribution directory structure. Adjust
that file to suit your needs.

For example, write a class like this:

```java
public class CustomJetJob {
  public static void main(String[] args) {
    JetInstance jet = JetBootstrap.getInstance();
    jet.newJob(buildDag()).execute().get();
  }

  public static DAG buildDag() {
    // ...
  }
}
```

After building the JAR, submit the job:

```
$ submit-jet.sh jetjob.jar
```

## Inspecting Processor Input and Output

The structure of the DAG model is a very poor match for Java's type
system, which results in the lack of compile-time type safety between
connected vertices. Developing a type-correct DAG therefore usually
requires some trial and error. To facilitate this process, but also to
allow many more kinds of diagnostics and debugging, Jet's library offers
ways to capture the input/output of a vertex and inspect it.

### Peeking with processor wrappers

The first approach is to decorate a vertex declaration with a layer that
will log all the data traffic going through it. This support is present
in the `DiagnosticProcessors` factory class, which contains the
following methods:

* `peekInput()`: logs items received at any edge ordinal.

* `peekOutput()`: logs items emitted to any ordinal. An item emitted to 
several ordinals is logged just once.

These methods take two optional parameters:

* `toStringF` returns the string representation of an item. The default
is to use `Object.toString()`.
* `shouldLogF` is a filtering function so you can focus your log output
only on some specific items. The default is to log all items.

#### Example usage

Suppose we have declared the second-stage vertex in a two-stage
aggregation setup:

```java
Vertex combine = dag.newVertex("combine", 
    combineByKey(counting()));
```

We'd like to see what exactly we're getting from the first stage, so
we'll wrap the processor supplier with `peekInput()`:

```java
Vertex combine = dag.newVertex("combine", 
    peekInput(combineByKey(counting())));
```

Keep in mind that logging happens on the machine running hosting the
processor, so this technique is primarily targetted to Jet jobs the
developer runs locally in his development environment.

### Attaching a sink vertex

Since most vertices are implemented to emit the same data stream to all
attached edges, it is usually possible to attach a diagnostic sink to
any vertex. For example, Jet's standard `writeFile()` sink can be very
useful here.

#### Example usage

In the example from the Word Count tutorial we can add the following
declarations:

```java
Vertex diagnose = dag.newVertex("diagnose",
        Sinks.writeFile("tokenize-output"))
        .localParallelism(1);
dag.edge(from(tokenize, 1).to(diagnose));
```

This will create the directory `tokenize-output` which will contain one
file per processor instance running on the machine. When running in a
cluster, you can inspect on each member the input seen on that member.
By specifying the `allToOne()` routing policy you can also have the
output of all the processors on all the members saved on a single member
(although the choice of exactly which member will be arbitrary).

## How to Unit-Test a Processor

Utility classes for unit testing is provided as part of the core API
inside `com.hazelcast.jet.test` package. Using these utility classes,
you can unit test custom processors by passing them input items and
asserting the expected output.

A `TestSupport.testProcessor()` set of methods is provided for the
typical case.

For cooperative processors a 1-capacity outbox will be provided, which
will additionally be full on every other processing method call. This
will test edge cases in cooperative processors.

This method does the following:

* initializes the processor by calling `Processor.init()`
* calls `Processor.process(0, inbox)`, the `inbox` contains all items
from `input` parameter
* asserts the progress of the `process()` call: that something was taken
from the inbox or put to the outbox
* calls `Processor.complete()` until it returns `true`
* asserts the progress of the `complete()` call if it returned `false`:
something must have been put to the outbox.

Note that this method never calls `Processor.tryProcess()`.

This class does not cover these cases:

* testing of processors which distinguish input or output edges by
ordinal.

* checking that the state of a stateful processor is empty at the end
(you can do that yourself afterwards).

Example usage. This will test one of the jet-provided processors:

```java
      TestSupport.testProcessor(
              Processors.map((String s) -> s.toUpperCase()),
              asList("foo", "bar"),
              asList("FOO", "BAR")
      );
```

## Serialization Caveats

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

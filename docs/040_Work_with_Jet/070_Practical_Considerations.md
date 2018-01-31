[TOC]

## Remember that a Jet Job is Distributed

The API to submit a job to Jet is in a way deceptively simple: "just
call a method." As long as you're toying around with Jet instances
started locally in a single JVM, everything will indeed work. However,
as soon as you try to deploy to an actual cluster, you'll face the
consequences of the fact that your job definition must travel over the
wire to reach remote members which don't have your code on their
classpath.

Your custom code must be packaged with the Jet job. For simple examples
you can have everything in a single class and use code like this:

```java
class JetExample {
    static Job createJob(JetInstance jet) {
        JobConfig jobConfig = new JobConfig();
        jobConfig.addClass(JetExample.class);
        return jet.newJob(createPipeline(), jobConfig);
    }
    ...
}
```

If you forget to do this, or don't add all the classes involved, you
may get a quite confusing exception:

```text
java.lang.ClassCastException:
cannot assign instance of java.lang.invoke.SerializedLambda
to field com.hazelcast.jet.core.ProcessorMetaSupplier$1.val$addressToSupplier
of type com.hazelcast.jet.function.DistributedFunction
in instance of com.hazelcast.jet.core.ProcessorMetaSupplier$1
```

`SerializedLambda` actually declares `readResolve()`, which would
normally transform it into an instance of the correct functional
interface type. If this method throws an exception, Java doesn't report
it but keeps the `SerializedLambda` instance and continues the
deserialization. Later in the process it will try to assign it to
a field whose type is the target type of the lambda
(`DistributedFunction` in the example above) and at that point it will
fail with the `ClassCastException`. So, if you see this kind of error,
double-check the list of classes you have added to the Jet job.

For more complex jobs it will become more practical to first package the
job in a JAR and then use a command-line utility to submit it, as
explained next.

### Submit a Job from the Command Line

Jet comes with the `jet-submit.sh` script, which allows you to submit a 
Jet job packaged in a JAR file. You can find it in the Jet distribution 
zipfile, in the `bin` directory. On Windows use `jet-submit.bat`. To use 
it, follow these steps:

* Write your `main()` method and your Jet code the usual way, except
for calling
[`JetBootstrap.getInstance()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/server/JetBootstrap.html)
to acquire a Jet client instance (instead of `Jet.newJetClient()`).
* Create a runnable JAR which declares its `Main-Class` in
* `MANIFEST.MF`.

* Run your JAR, but instead of `java -jar jetjob.jar` use `jet-submit.sh
jetjob.jar`.

* The script will create a Jet client and configure it from
`hazelcast-client.xml` located in the `config` directory of Jet's
distribution. Adjust that file to suit your needs.

For example, write a class like this:

```java
public class CustomJetJob {
    public static void main(String[] args) {
        JetInstance jet = JetBootstrap.getInstance();
        jet.newJob(buildPipeline()).join();
    }

    static Pipeline buildPipeline() {
        // ...
    }
}
```

After building the JAR, submit the job:

```
$ jet-submit.sh jetjob.jar
```

## Watch out for Capturing Lambdas

A typical Jet pipeline involves lambda expressions. Since the whole
pipeline definition must be serialized to be sent to the cluster, the
lambda expressions must be serializable as well. The Java standard
provides an essential building block: if the static type of the lambda
is a subtype of `Serializable`, you will automatically get a lambda
instance that can serialize itself.

None of the functional interfaces in the JDK extend `Serializable`, so
we had to mirror the entire `java.util.function` package in our own
`com.hazelcast.jet.function` with all the interfaces subtyped and made
`Serializable`. Each subtype has the name of the original with
`Distributed` prepended. For example, a `DistributedFunction` is just
like `Function`, but implements `Serializable`. We use these types
everywhere in the Pipeline API.

As always with this kind of magic, auto-serializability of lambdas has its
flipside: it is easy to overlook what's going on.

If the lambda references a variable in the outer scope, the variable is
captured and must also be serializable. If it references an instance
variable of the enclosing class, it implicitly captures `this` so the
entire class will be serialized. For example, this will fail because
`JetJob` doesn't implement `Serializable`:

```java
class JetJob {
    private String instanceVar;

    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        p.drawFrom(readList("input"))
         // Refers to instanceVar, capturing "this", but JetJob is not
         // Serializable so this call will fail.
         .filter(item -> item.equals(instanceVar));
        return p;
    }
}
```

Just adding `implements Serializable` to `JetJob` would be a viable
workaround here. However, consider something just a bit different:

```java
class JetJob {
    private String instanceVar;
    private OutputStream fileOut; // a non-serializable field

    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        p.drawFrom(readList("input"))
         // Refers to instanceVar, capturing "this". JetJob is declared
         // Serializable, but has a non-serializable field and this fails.
         .filter(item -> item.equals(instanceVar));
        return p;
    }
}
```

Even though we never refer to `fileOut`, we are still capturing the
entire `JetJob` instance. We might mark `fileOut` as `transient`, but
the sane approach is to avoid referring to instance variables of the
surrounding class. This can be simply achieved by assigning to a local
variable, then referring to that variable inside the lambda:

```java
class JetJob {
    private String instanceVar;

    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        String findMe = instanceVar;
        p.drawFrom(readList("input"))
         // By referring to the local variable "findMe" we avoid
         // capturing "this" and the job runs fine.
         .filter(item -> item.equals(findMe));
        return p;
    }
}
```

Another common pitfall is capturing an instance of `DateTimeFormatter`
or a similar non-serializable class:

```java
DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                         .withZone(ZoneId.systemDefault());
Pipeline p = Pipeline.create();
ComputeStage<Long> src = p.drawFrom(readList("input"));
// Captures the non-serializable formatter, so this fails
src.map((Long tstamp) -> formatter.format(Instant.ofEpochMilli(tstamp)));
```

Sometimes we can get away by using one of the preconfigured formatters
available in the JDK:

```java
// Accesses the static final field ISO_LOCAL_TIME. Static fields are
// not subject to lambda capture, they are dereferenced when the code
// runs on the target machine.
src.map((Long tstamp) ->
    DateTimeFormatter.ISO_LOCAL_TIME.format(
        Instant.ofEpochMilli(tstamp).atZone(ZoneId.systemDefault())));
```

This refers to a `static final` field in the JDK, so the instance is
available on any JVM. A similar approach is to declare our own `static
final` field; however in that case we must add the declaring class as a
job resource:

```java
class JetJob {

    // Our own static field
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                             .withZone(ZoneId.systemDefault());

    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        ComputeStage<Long> src = p.drawFrom(readList("input"));
        src.map((Long tstamp) -> formatter.format(Instant.ofEpochMilli(tstamp)));
        return p;
    }

    // The job will fail unless we attach the JetJob class as a
    // resource, making the formatter instance available at the
    // target machine.
    void runJob(JetInstance jet) throws Exception {
        JobConfig c = new JobConfig();
        c.addClass(JetJob.class);
        jet.newJob(buildPipeline(), c).join();
    }
}
```

## Standard Java Serialization is Slow

When it comes to serializing the description of a Jet job, performance
is not critical. However, for the data passing through the pipeline,
the cost of the serialize-deserialize cycle can easily dwarf the cost of
actual data transfer, especially on high-end LANs typical for data
centers. In this context the performance of Java serialization is so
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
[custom serialization](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#custom-serialization)
in Hazelcast IMDG's reference manual for more details.

Note the limitation implied here: the serializers must be registered
with Jet on startup because this is how it is supported in Hazelcast
IMDG. There is a plan to improve this and allow serializers to be
registered on individual Jet jobs.

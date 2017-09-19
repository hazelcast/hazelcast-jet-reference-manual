## Start Jet and Submit Jobs to it

To create a Jet cluster, we simply start some Jet instances. Normally
these would be started on separate machines, but for simple practice
we can use the same JVM for both instances. Even though they are in the
same JVM, they'll communicate over the network interface.

```java
public class WordCount {
    public static void main(String[] args) {
        JetInstance jet = Jet.newJetInstance();
        Jet.newJetInstance();
    }
}
```

These two instances should automatically discover each other using IP
multicast and form a cluster. You should see a log output similar to the
following:

```
Members [2] {
  Member [10.0.1.3]:5701 - f1e30062-e87e-4e97-83bc-6b4756ef6ea3
  Member [10.0.1.3]:5702 - d7b66a8c-5bc1-4476-a528-795a8a2d9d97 this
}
```

This means the members successfully formed a cluster. Since the Jet
instances start their own threads, it is important to explicitly shut
them down at the end of your program; otherwise the Java process will
remain alive after the `main()` method completes:

```java
public class WordCount {
    public static void main(String[] args) {
        try {
            JetInstance jet = Jet.newJetInstance();
            Jet.newJetInstance();

            ... work with Jet ...

        } finally {
            Jet.shutdownAll();
        }
    }
}
```

This is how you submit a Jet pipeline for execution:

```java
pipeline.execute(jet).get();
```

Alternatively, you can submit a Core API DAG:

```java
jet.newJob(dag).execute().get();
```

Code samples with
[the Core API DAG](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/core-api/batch/wordcount-core-api/src/main/java/refman/WordCountRefMan.java) 
and
[the pipeline](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/wordcount-pipeline-api/src/main/java/WordCountPipelineApi.java)
are available at our Code Samples repo.

## Build a Pipeline

The general shape of any data processing pipeline is `drawFromSource -> transform -> drainToSink` and the natural way to build it is from source
to sink. This is how the Pipeline API works. For example,

```java
Pipeline p = Pipeline.create();
p.drawFrom(Sources.<String>readList("input"))
 ... transform the data ...
 .drainTo(Sinks.writeMap("result");
```

In each step, such as `drawFrom` or `drainTo`, you create a pipeline
_stage_. The stage resulting from a `drainTo` operation is called a
_sink stage_ and you can't attach more stages to it. All others are
called _compute stages_ and expect you to attach stages to them.

In a more complex scenario you'll have more than one source and you'll
start several pipeline branches. Then you can merge them in a
multi-input transformation such as co-grouping:

```java
Pipeline p = Pipeline.create();
ComputeStage<String> src1 = p.drawFrom(Sources.readList("src1"));
ComputeStage<String> src2 = p.drawFrom(Sources.readList("src2"));
src1.coGroup(wholeItem(), src2, wholeItem(), counting2())
    .drainTo(Sinks.writeMap("result"));
```

For completeness, `counting2()` is a 2-way aggregate operation which
may be defined as follows:

```java
private static AggregateOperation2<String, String, LongAccumulator, Long> counting2() {
    return AggregateOperation
            .withCreate(LongAccumulator::new)
            .<String>andAccumulate0((count, item) -> count.add(1))
            .<String>andAccumulate1((count, item) -> count.add(1))
            .andCombine(LongAccumulator::add)
            .andFinish(LongAccumulator::get);
}
```

Symmetrically, the output of a stage can be sent to more than one
destination:

```java
Pipeline p = Pipeline.create();
ComputeStage<String> src = p.drawFrom(Sources.readList("src"));
src.map(String::toUpperCase)
   .drainTo(Sinks.writeList("uppercase"));
src.map(String::toLowerCase)
   .drainTo(Sinks.writeList("lowercase"));
```

## Choose Your Data Sources and Sinks



## Basic Pipeline Transforms

## Multi-Input Transorms


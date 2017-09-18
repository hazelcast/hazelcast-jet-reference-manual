Before we go on, let us spend some time explaining the code needed to
start a Jet cluster, load it with sample data, and submit jobs to it.
You can use the same template both for raw DAGs built with Core API and
pipelines built with the Pipeline API.

To start a new Jet cluster, we must start some Jet instances. Typically
these would be started on separate machines, but for simple practice we'll
be using the same JVM for both instances. We can start them as shown
below:

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

This means the members successfully formed a cluster. Don't forget to
shut down the members afterwards, by adding the following as the last
line of your application:

```
Jet.shutdownAll();
```

This must be executed unconditionally, even in the case of an exception;
otherwise your Java process will stay alive because Jet has started its
internal threads:

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

We'll use an `IMap` as our data source. Let's give it some sample data:

```java
IMap<Integer, String> map = jet.getMap("lines");
map.put(0, "It was the best of times,");
map.put(1, "it was the worst of times,");
map.put(2, "it was the age of wisdom,");
map.put(3, "it was the age of foolishness,");
map.put(4, "it was the epoch of belief,");
map.put(5, "it was the epoch of incredulity,");
map.put(6, "it was the season of Light,");
map.put(7, "it was the season of Darkness");
map.put(8, "it was the spring of hope,");
map.put(9, "it was the winter of despair,");
map.put(10, "we had everything before us,");
map.put(11, "we had nothing before us,");
map.put(12, "we were all going direct to Heaven,");
map.put(13, "we were all going direct the other way --");
map.put(14, "in short, the period was so far like the present period, that some of "
   + "its noisiest authorities insisted on its being received, for good or for "
   + "evil, in the superlative degree of comparison only.");
```

To run the DAG we do the following:

```java
jet.newJob(dag).execute().get();
```

Alternatively, to execute a pipeline, we say

```java
pipeline.execute(jet).get();
```

Finally, this will print out the job's results:

```java
System.out.println(jet.getMap("counts").entrySet());
```

If you use this with the DAG we are about to create in the next section,
the output should look like the following:

```
[heaven=1, times=2, of=12, its=2, far=1, light=1, noisiest=1,
the=14, other=1, incredulity=1, worst=1, hope=1, on=1, good=1, going=2,
like=1, we=4, was=11, best=1, nothing=1, degree=1, epoch=2, all=2,
that=1, us=2, winter=1, it=10, present=1, to=1, short=1, period=2,
had=2, wisdom=1, received=1, superlative=1, age=2, darkness=1, direct=2,
only=1, in=2, before=2, were=2, so=1, season=2, evil=1, being=1,
insisted=1, despair=1, belief=1, comparison=1, some=1, foolishness=1,
or=1, everything=1, spring=1, authorities=1, way=1, for=2]
```

Code samples with
[the Core API DAG](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/core-api/batch/wordcount-core-api/src/main/java/refman/WordCountRefMan.java) 
and
[the pipeline](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/batch/wordcount-pipeline-api/src/main/java/WordCountPipelineApi.java)
are available at our Code Samples repo.

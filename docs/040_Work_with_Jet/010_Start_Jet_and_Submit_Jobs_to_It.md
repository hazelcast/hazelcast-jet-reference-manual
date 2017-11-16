To create a Jet cluster, we simply start some Jet instances. Normally
these would be started on separate machines, but for simple practice
we can use the same JVM for two instances. Even though they are in the
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
jet.newJob(pipeline).join();
```

Alternatively, you can submit a Core API DAG:

```java
jet.newJob(dag).join();
```

Code samples with
[the pipeline](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/refman/src/main/java/refman/WordCountRefMan.java)
and
[the Core API DAG](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/refman/src/main/java/refman/WordCountCoreApiRefMan.java) 
are available at our Code Samples repo.

In the [Practical Considerations](Practical_Considerations) section
we'll deepen this story and explain what it takes to submit a job to a
real-life Jet cluster.

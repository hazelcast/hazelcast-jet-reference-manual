To start a new Jet cluster, we need to start Jet instances. Typically
these would be started on separate machines, but for the purposes of
this tutorial we will be using the same JVM for both of the instances.
You can start the instances as shown below:

```java
public class WordCount {
    public static void main(String[] args) {
        JetInstance instance1 = Jet.newJetInstance();
        JetInstance instance2 = Jet.newJetInstance();
    }
}
```

These two members should automatically form a cluster, as they will use
multicast, by default, to discover each other.
You should see an output similar to the following:

```
Members [2] {
  Member [10.0.1.3]:5701 - f1e30062-e87e-4e97-83bc-6b4756ef6ea3
  Member [10.0.1.3]:5702 - d7b66a8c-5bc1-4476-a528-795a8a2d9d97 this
}
```

This means the members successfully formed a cluster. Do not forget to
shut down the members afterwards, by adding the following as the last line
of your application:

```
Jet.shutdownAll();
```

This must be executed unconditionally, even in the case of an exception;
otherwise your Java process will stay alive because Jet has started its
internal threads.

In production code you'd put everything inside a `try-finally` block,
but for simplicity's sake, while playing around with samples, we can
add a shutdown hook:

```java
public class WordCount {
    public static void main(String[] args) {
        // Will clean up in case of an exception:
        Runtime.getRuntime().addShutdownHook(new Thread(Jet::shutdownAll));

        JetInstance instance1 = Jet.newJetInstance();
        JetInstance instance2 = Jet.newJetInstance();

        ... your code here...

        // Will clean up when there's no exception:
        Jet.shutdownAll();
    }
}
```

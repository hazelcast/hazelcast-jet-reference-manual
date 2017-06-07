Developing a DAG for Jet usually involves writing *lambda expressions*. 
Because the DAG is sent to members in serialized form, lambda 
expressions need to be serializable too. For this reason we subclassed 
all classes in `java.util.function` package with `Distributed` variants, 
which extend them by implementing `java.io.Serializable`.

### Lambda variable capture

If the lambda references a variable in the outer scope, the variable is 
captured and must also be serializable. If you reference an instance 
field of the enclosing class, that class will get referenced and must be
serializable:

```java
public class JetJob {
    private String parameter;
    public DAG buildDag() {
        DAG dag = new DAG();
        // this will fail: enclosing JetJob instance is not non-serializable
        Vertex filter = dag.newVertex("filter", filter(item -> parameter.equals(item)));
        // ...
    }
}
```

Another common pitfall is referencing an instance of `DateTimeFormatter`,
which is not serializable:

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault());
// this will fail, formatter is not serializable
Vertex map = dag.newVertex("map", 
    map((Long tstamp) -> formatter.format(Instant.ofEpochMilli(tstamp))));
```

In this specific case, the simplest option is to use one of the 
ready-made formatters:

```java
Vertex map = dag.newVertex("map", map((Long tstamp) -> 
    DateTimeFormatter.ISO_LOCAL_TIME.format(
        Instant.ofEpochMilli(tstamp).atZone(ZoneId.systemDefault()))));
```

A more generic option is to use `static` field: static fields are not 
serialized, however you have to ensure that the containing class is 
added to the job (see `JobConfig.addClass()`).

```java
private static DateTimeFormatter formatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                     .withZone(ZoneId.systemDefault());

void buildDag() {
    // ...
    Vertex map = dag.newVertex("map",
        map((Long tstamp) -> formatter.format(Instant.ofEpochMilli(tstamp))));
    // ...
}
```

### Serialization performance

Java serialization has low performance due to it's heavy usage of 
reflection. Hazelcast provides alternate serialization mechanism: 
Hazelcast Custom Serialization. Jet supports both this and Java 
serialization for the items processed by processors.

To use Hazelcast serialization, consult [Hazelcast Custom 
Serialization](http://docs.hazelcast.org/docs/3.8/manual/html/customserialization.html)
chapter in the manual.
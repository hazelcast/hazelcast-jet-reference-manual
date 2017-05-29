Log Writer is a sink which logs all items at the INFO level.
`Punctuation` items are not logged.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", DiagnosticProcessors.writeLogger());
```
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", DiagnosticProcessors.writeLogger(Object::toString));
```

Note that the event will be logged on the cluster members, 
not on the client, so it's primarily meant for testing.
Local parallelism of 1 is recommended for this vertex.
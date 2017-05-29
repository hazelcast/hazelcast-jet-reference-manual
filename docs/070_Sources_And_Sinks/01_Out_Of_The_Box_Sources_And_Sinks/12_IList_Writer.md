IList writer adds the items to a Hazelcast IList.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeList(LIST_NAME));
```

You can use IList writer to write the items to a remote 
Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeList(LIST_NAME, clientConfig));
```
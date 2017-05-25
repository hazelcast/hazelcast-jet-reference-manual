IList writer adds the items to a Hazelcast IList.

```java
    DAG dag = new DAG();
    
    ...
    Vertex source = dag.newVertex("sink", Processors.writeList(LIST_NAME));
```

You can use IList writer to write the items to a remote 
Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    
    ...
    ClientConfig clientConfig = new ClientConfig();
    // configure the client
    Vertex source = dag.newVertex("sink", Processors.writeList(LIST_NAME, clientConfig));
```
Since IList is not a partitioned data structure, 
all elements from the list are emitted on a single 
member &mdash; the one where the entire list is stored.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readList(LIST_NAME));
    // ... other vertices
```

You can use IList reader to fetch the items from 
a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    Vertex source = dag.newVertex("source", Sources.readList(LIST_NAME, clientConfig));
    // ... other vertices
```
Since IList is not a partitioned data structure, 
all elements from the list are emitted on a single 
member which the one where the entire list is stored.

```java
    DAG dag = new DAG();

    Vertex source = dag.newVertex("source", Processors.readList(LIST_NAME));
    ...
```

You can use IList reader to fetch the items from 
a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();

    ClientConfig clientConfig = new ClientConfig();
    ...
    Vertex source = dag.newVertex("source", Processors.readList(LIST_NAME, clientConfig));
    ...
```
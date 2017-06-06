IMap and ICache Readers distribute the partitions to 
processors according to ownership of the partitions. 
Thus each processor will access data locally. Processors 
will iterate over entries and emit them as `Map.Entry`. 
The number of Hazelcast partitions should be configured to 
at least `localParallelism * clusterSize`, otherwise some 
processors will have no partitions assigned to them.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readMap(MAP_NAME));
    // ... other vertices
```

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readCache(CACHE_NAME));
    // ... other vertices
```

You can use IMap and ICache readers to fetch the entries
from a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    Vertex source = dag.newVertex("source", Sources.readMap(MAP_NAME, clientConfig));
    // ... other vertices
```

```java
    DAG dag = new DAG();
    ClientConfig clientConfig = new ClientConfig();
    // ... configure the client
    Vertex source = dag.newVertex("source", Sources.readCache(CACHE_NAME, clientConfig));
    // ... other vertices
```

If the underlying map or cache is concurrently being modified, 
there are no guarantees given with respect to missing or duplicate items.


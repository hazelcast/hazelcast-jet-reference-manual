IMap and ICache Writers drain the entries to a buffer 
and uses `putAll` method for flushing them into 
IMap and ICache respectively. Processors expects 
items of type `Map.Entry`.

```java
    DAG dag = new DAG();
    
    ...
    Vertex source = dag.newVertex("sink", Processors.writeMap(MAP_NAME));
```
```java
    DAG dag = new DAG();
    
    ...
    Vertex source = dag.newVertex("sink", Processors.writeCache(CACHE_NAME));
```

You can use IMap and ICache writers to write the entries
to a remote Hazelcast cluster by configuring a `ClientConfig`.

```java
    DAG dag = new DAG();
    
    ...
    ClientConfig clientConfig = new ClientConfig();
    // configure the client
    Vertex source = dag.newVertex("sink", Processors.writeMap(MAP_NAME, clientConfig));
```
```java
    DAG dag = new DAG();
    
    ...
    ClientConfig clientConfig = new ClientConfig();
    // configure the client
    Vertex source = dag.newVertex("sink", Processors.writeCache(CACHE_NAME, clientConfig));
```
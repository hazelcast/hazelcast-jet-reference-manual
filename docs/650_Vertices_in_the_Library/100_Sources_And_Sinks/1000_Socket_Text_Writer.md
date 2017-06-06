Socket Text Writer is a sink which writes the items to 
a socket as text. Each processor instance will create 
a socket connection to the configured `[host:port]`, 
so there will be `clusterSize * localParallelism` 
connections. The server should do the load balancing.

Processors drain the items to a buffer and flush them 
to the underlying output stream.
 
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeSocket(HOST, PORT));
```
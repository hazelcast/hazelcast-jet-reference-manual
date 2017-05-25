Socket Text Streamer is a source which streams text read from 
a socket line by line. You can configure a `Charset` otherwise 
`UTF-8` will be used as default. Each processor instance will 
create a socket connection to the configured `[host:port]`, 
so there will be `clusterSize * localParallelism` connections. 
The server should do the load-balancing.

The processors will complete when the socket is closed by the server.
No reconnection is attempted.

```java
    DAG dag = new DAG();

    Vertex source = dag.newVertex("source", Processors.streamTextSocket(HOST, PORT));
    ...
```
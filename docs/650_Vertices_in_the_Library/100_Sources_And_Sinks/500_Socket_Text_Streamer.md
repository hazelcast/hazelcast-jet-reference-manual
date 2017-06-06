Socket Streamer is a source which streams text read from 
a socket line by line. You can configure a `Charset` otherwise 
`UTF-8` will be used as the default. Each processor instance will 
create a socket connection to the configured `[host:port]`, 
so there will be `clusterSize * localParallelism` connections. 
The server should do the load-balancing.

The processors will complete when the socket is closed by the server.
No reconnection is attempted.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.streamSocket(HOST, PORT));
    // ... other vertices
```

See the [Socket code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/streaming/socket)
for a fully working example.
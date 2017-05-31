File Writer is a sink which writes all items to a local file on each 
member. Result of `toStringF` function will be written to the file 
followed by a platform-specific line separator. Files are named with an 
integer number starting from 0, which is unique cluster-wide.

The same pathname must be available for writing on all members. 
The file on each member will contain the part of the data processed 
on that member.

```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeFile(DIRECTORY));
```
```java
    DAG dag = new DAG();
    // ... other vertices
    Vertex sink = dag.newVertex("sink", Sinks.writeFile(DIRECTORY, Object::toString, 
        StandardCharsets.UTF_8, true));
```

Since this processor is file IO-intensive, local parallelism 
of the vertex should be set according to the performance 
characteristics of the underlying storage system. Typical 
values are in the range of 1 to 4. 

See the [Access log analyzer sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/batch/access-log-analyzer)
for a fully working example.
File Writer is a sink which writes all items to a local 
file on each member. Result of `toStringF` function will 
be written to the file followed by a platform-specific line 
separator.

The same pathname must be available for writing on all nodes. 
The file on each node will contain part of the data processed 
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

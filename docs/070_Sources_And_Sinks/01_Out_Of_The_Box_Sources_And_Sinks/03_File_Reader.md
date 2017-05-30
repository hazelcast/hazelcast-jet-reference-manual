File Reader is a source that emits lines from files in a 
directory matching the supplied pattern (but not its subdirectories). 
You can pass `*` as the pattern to read all files in the directory.

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readFiles(DIRECTORY));
    // ... other vertices
```

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.readFiles(DIRECTORY, 
        StandardCharsets.UTF_8, PATTERN));
    // ... other vertices
```

The files must not change while being read; if they do, 
the behavior is unspecified. There will be no indication 
which file a particular line comes from.

The same pathname should be available on all members, 
but it should not contain the same files. For example 
it should not resolve to a directory shared over the network.

Since this processor is file IO-intensive, local parallelism of the 
vertex should be set according to the performance characteristics of the 
underlying storage system. Typical values are in the range of 1 to 4. 
Multiple files are read in parallel, if just a single file is read, it 
is always read by single thread.

See the [Access log analyzer sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/batch/access-log-analyzer)
for a fully working example.
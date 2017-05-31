A source that generates a stream of lines of text coming from 
files in the watched directory matching the supplied pattern 
(but not its subdirectories). You can pass `*` as the pattern 
to read all files in the directory. It will pick up both newly created 
files and content appended to pre-existing files. It expects the 
file contents not to change once appended. There is no indication 
which file a particular line comes from.
    
The processor will scan pre-existing files for file sizes on 
startup and process them from that position. It will ignore 
the first line if the starting offset is not immediately after 
a newline character (it is assumed that another process is 
concurrently appending to the file).

```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.streamFiles(DIRECTORY));
    // ... other vertices
```
```java
    DAG dag = new DAG();
    Vertex source = dag.newVertex("source", Sources.streamFiles(DIRECTORY, 
        StandardCharsets.UTF_8, PATTERN));
    // ... other vertices
```
    
The same pathname should be available on all members, but it 
should not contain the same files. For example it should not 
resolve to a directory shared over the network.
    
Since this processor is file IO-intensive, local parallelism 
of the vertex should be set according to the performance 
characteristics of the underlying storage system. Typical 
values are in the range of 1 to 4. If just a single file is read, 
it is always read by single thread.

When a change is detected the file is opened, appended lines are 
read and file is closed. This process is repeated as necessary.

The processor completes when the directory is deleted. However, 
in order to delete the directory, all files in it must be deleted 
and if you delete a file that is currently being read from, 
the job may encounter an `IOException`. The directory must be 
deleted on all nodes. Any `IOException` will cause the job to fail.
    
**Limitation on Windows**

On Windows the `WatchService` is not notified of appended lines
until the file is closed. If the writer keeps the file open while
appending (which is typical), the processor may fail to observe the
changes. It will be notified if any process tries to open that file,
such as looking at the file in Explorer. This holds for Windows 10 with
the NTFS file system and might change in future. You are advised to do
your own testing on your target Windows platform.

**Use the latest JRE**

The underlying JDK API `java.nio.file.WatchService` has a history of 
unreliability and this processor may experience infinite blocking, 
missed, or duplicate events as a result. Such problems may be resolved 
by upgrading the JRE to the latest version.

See the [Access stream analyzer sample](https://github.com/hazelcast/hazelcast-jet-code-samples/tree/master/streaming/access-stream-analyzer)
for a fully working example.
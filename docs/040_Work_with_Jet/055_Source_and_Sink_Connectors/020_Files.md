Jet provides various options to use the filesystem as a source or a sink.

## Reading from Files

Jet provides two main ways to use the filesystem as a source:

1. [`Sources.files()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sources.html#files-java.lang.String-java.nio.charset.Charset-java.lang.String-)): Reading all files in a
directory to completion. The sources completes once all the files
have been read. Useful when reading static files.
2. [`Sources.fileWatcher()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sources.html#fileWatcher-java.lang.String-java.nio.charset.Charset-java.lang.String-):
Continuously watching a directory for changes. For example, this is
useful for watching for files which are continuously appended to
and rolled over to new files. This source only completes when the
directory is deleted.

Both approaches assume a text based input and the output is always
emitted on a per line basis. In all cases, the input is a directory and each
member in the cluster expects the directory to be present in its local
filesystem. The files in each directory will be distributed among the
local processors depending on the local parallelism of the vertex.

Both sources currently do not support snapshots.

## Writing to Files

When using the file as a sink, the output will be written as several files
into the given output directory. Each local processor will write to a
unique file within the directory. The files are appended to as
new input is received. Each item is terminated with a new line.

The file sink only guarantees that items have been flushed to the operating
system on a snapshot, but it doesn't guarantee that the content is actually
written to disk.

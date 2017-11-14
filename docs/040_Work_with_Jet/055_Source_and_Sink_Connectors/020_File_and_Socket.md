Hazelcast Jet provides a few connectors that have limited production
use, but are simple and can be very useful in an early rapid prototyping
phase. These are the connectors for the local file system and TCP/IP
sockets. They assume the data is in the form of plain text and
emit/receive data items which represent individual lines of text.

Some of these sources are infinite, but when used in a stream processing
job they don't offer any fault tolerance because they are not
replayable. The finite variant of the file source is trivially
replayable: it just reads the same files again.

# File

These connectors work with a directory in the file system on each member.
Since each member has its own file system, these are to some extent
distributed sources and sinks; however there is no unified view of all
the data on all members. The user must manually distribute the source
data and collect the sink data from all the cluster members.

## Source

Jet provides two main ways to use the filesystem as a source:

1. [`Sources.files()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sources.html#files-java.lang.String-java.nio.charset.Charset-java.lang.String-)): read all the files in a
directory and complete. The files should not change while being read.
2. [`Sources.fileWatcher()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sources.html#fileWatcher-java.lang.String-java.nio.charset.Charset-java.lang.String-):
first emit the contents of the files present in the directory and then
continue watching the directory for further changes. Each time a
complete line of text appears in an existing or a newly created file,
the source emits another data item. The existing content in the files
should not change. This source completes only if the watched directory
is deleted.

## Sink

The
[`Sources.files()`](http://docs.hazelcast.org/docs/jet/latest-dev/javadoc/com/hazelcast/jet/Sinks.html#files-java.lang.String-com.hazelcast.jet.function.DistributedFunction-java.nio.charset.Charset-boolean-)
sink writes output to several files in the configured directory. Each
undelying processor writes to its own file to avoid contention.

The file sink only guarantees that items have been flushed to the
operating system on a snapshot, but it doesn't guarantee that the
content is actually written to disk.

The socket source can be used to receive text input over a network socket.

# Socket

These connectors open a blocking client TCP/IP socket and
send/receive data over it. As already noted, the data must be in the
form of lines of plain text.

## Source

Each underlying processor of the Socket Source connector opens its
own client socket and asks for data from it. The user supplies the
`host:port` connection details. The server side should ensure a
meaningful dispersion of data among all the connected clients, but
how it does it is outside of Jet's control.

You can study a comprehensive
[code sample](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/streaming/socket-connector/src/main/java/StreamTextSocket.java)
including a sample socket server using Netty.

## Sink

The Socket Sink also opens one client socket per processor and
pushes lines of text into it. It is the duty of the server-side
system to collect the data from all the concurrently connected
clients.

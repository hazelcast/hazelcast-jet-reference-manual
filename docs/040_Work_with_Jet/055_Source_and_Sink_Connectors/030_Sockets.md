The socket source can be used to receive text input over a network socket.

## Reading from Sockets

Jet can read from a text-based network socket and emit incoming lines as
output. The socket source does not create a socket server itself,
but instead expects to be supplied with a target host and port that all
the processors can connect to. The supplied target must be able to
deal with multiple concurrent inbound connections and decide if
it wants to push data to the clients in a partitioned or round robin fashion.
The implementation of the server itself is outside of the scope of Jet.

A comprehensive code sample including a sample socket server using Netty
can found in [code samples](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/streaming/socket-connector/src/main/java/StreamTextSocket.java).

## Writing to Sockets

Likewise when reading, it is possible to push data to a text-based
network socket, given a host and port which the sink can connect to.
Each item will be terminated with a new line.

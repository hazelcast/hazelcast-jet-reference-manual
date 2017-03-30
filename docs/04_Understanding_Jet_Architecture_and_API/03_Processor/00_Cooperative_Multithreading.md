Cooperative multithreading is one of the core features of Jet and can be
roughly compared to [green
threads](https://en.wikipedia.org/wiki/Green_threads). It is purely a
library-level feature and does not involve any low-level system or JVM
tricks; the [`Processor`](../03_Processor) API is simply designed in such a
way that the processor can do a small amount of work each time it is
invoked, then yield back to the Jet engine. The engine manages a thread
pool of fixed size and on each thread, the processors take their turn in
a round-robin fashion.

The point of cooperative multithreading is much lower context-switching
cost and precise knowledge of the status of a processor's input and
output buffers, which determines its ability to make progress.

`Processor` instances are cooperative by default. The processor can opt
out of cooperative multithreading by overriding `isCooperative()` to
return `false`. Jet will then start a dedicated thread for it.

#### Requirements

To maintain an overall good throughput, a cooperative processor must take
care not to hog the thread for too long (a rule of thumb is up to a
millisecond at a time). Jet's design strongly favors cooperative
processors and most processors can and should be implemented to fit
these requirements. The major exception are sources and sinks because
they often have no choice but calling into blocking I/O APIs.
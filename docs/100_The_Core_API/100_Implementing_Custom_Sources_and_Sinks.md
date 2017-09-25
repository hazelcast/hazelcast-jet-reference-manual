The Hazelcast Jet distribution contains implementations of source and
sink processors for the sources and sinks exposed through the Pipeline
API. You can extend Jet's support for sources and sinks by writing your
own.

One of the main concerns when writing custom sources is that the source
is typically distributed across multiple machines and partitions, and
the work needs to be distributed across multiple members and processors.

Jet provides a flexible `ProcessorMetaSupplier` and `ProcessorSupplier`
API which can be used to control how a source is distributed across the
network.

The procedure for generating `Processor` instances is as follows:

1. The `ProcessorMetaSupplier` for the `Vertex` is serialized and sent to
the coordinating member.
2. The coordinator calls `ProcessorMetaSupplier.get()` once for each member
in the cluster and a `ProcessorSupplier` is created for each member.
3. The `ProcessorSupplier` for each member is serialized and sent to that
member.
4. Each member will call its own `ProcessorSupplier` with the correct
`count` parameter, which corresponds to the `localParallelism` setting of
that vertex.

## Example - Distributed Integer Generator

Let's say we want to write a simple source that will generate numbers from
0 to 1,000,000 (exclusive). It is trivial to write a single `Processor`
which can do this using `java.util.stream` and [`Traverser`](Convenience_API_to_Implement_a_Processor#page_Traverser).

```java
class GenerateNumbersP extends AbstractProcessor {

    private final Traverser<Integer> traverser;

    GenerateNumbersP(int upperBound) {
        traverser = Traversers.traverseStream(IntStream.range(0, upperBound).boxed());
    }

    @Override
    public boolean complete() {
        return emitFromTraverser(traverser);
    }
}
```

Now we can build our DAG and execute it:

```java
JetInstance jet = Jet.newJetInstance();

int upperBound = 10;
DAG dag = new DAG();
Vertex generateNumbers = dag.newVertex("generate-numbers",
        () -> new GenerateNumbersP(upperBound));
Vertex logInput = dag.newVertex("log-input",
        DiagnosticProcessors.writeLogger(i -> "Received number: " + i));
dag.edge(Edge.between(generateNumbers, logInput));

try {
    jet.newJob(dag).execute().get();
} finally {
    Jet.shutdownAll();
}
```

When you run this code, you will see the output as below:

```
Received number: 4
Received number: 0
Received number: 3
Received number: 2
Received number: 2
Received number: 2
```

Since we are using the default parallelism setting on this vertex,
several instances of the source processor were created, all of which
generated the same sequence of values. Generally we want the ability
to parallelize the source vertex, so we have to make each processor emit
only a slice of the total data set.

So far we've used the simplest approach to creating processors: a
`DistributeSupplier<Processor>` function that keeps returning equal
instances of processors. Now we'll step up to Jet's custom interface that
gives us the ability to provide a list of separately configured
processors: `ProcessorSupplier` and its method `get(int processorCount)`.

First we must decide on a partitioning policy: what subset will each
processor emit. In our simple example we can use a simple policy: we'll
label each processor with its index in the list and have it emit only
those numbers `n` that satisfy `n % processorCount == processorIndex`.
Let's write a new constructor for our processor which implements this
partitioning logic:

```java
GenerateNumbersP(int upperBound, int processorCount, int processorIndex) {
    traverser = Traversers.traverseStream(
            IntStream.range(0, upperBound)
                     .filter(n -> n % processorCount == processorIndex)
                     .boxed());
}
```

Given this preparation, implementing `ProcessorSupplier` is trivial:

```java
class GenerateNumbersPSupplier implements ProcessorSupplier {

    private final int upperBound;

    GenerateNumbersPSupplier(int upperBound) {
        this.upperBound = upperBound;
    }

    @Override @Nonnull
    public List<? extends Processor> get(int processorCount) {
        return IntStream.range(0, processorCount)
                .mapToObj(index -> new GenerateNumbersP(upperBound, processorCount, index))
                .collect(Collectors.toList());
    }
}
```

Let's use the custom processor supplier in our DAG-building code:

```java
DAG dag = new DAG();
Vertex generateNumbers = dag.newVertex("generate-numbers",
        new GenerateNumbersPSupplier(10));
Vertex logInput = dag.newVertex("log-input",
        DiagnosticProcessors.writeLogger(i -> "Received number: " + i));
dag.edge(Edge.between(generateNumbers, logInput));
```

Now we can re-run our example and see that each number indeed occurs
only once. However, note that we are still working with a single-member
Jet cluster; let's see what happens when we add another member:

```java
JetInstance jet = Jet.newJetInstance();
Jet.newJetInstance();

DAG dag = new DAG();
...
```

------------------------------------------------------------------------

Running after this change we'll see that both members are generating the
same set of numbers. This is because `ProcessorSupplier` is instantiated
independently for each member and asked for the same number of
processors, resulting in identical processors on all members. We have to
solve the same problem as we just did, but at the higher level of
cluster-wide parallelism. For that we'll need the
`ProcessorMetaSupplier`: an interface which acts as a factory of
`ProcessorSupplier`s, one for each cluster member. Under the hood it is
actually always the meta-supplier that's created by the DAG-building
code; the above examples are just implicit about it for the sake of
convenience. They result in a simple meta-supplier that reuses the
provided suppliers everywhere.

The meta-supplier is a bit trickier to implement because its method
takes a list of Jet member addresses instead of a simple count, and the
return value is a function from address to `ProcessorSupplier`. In our
case we'll treat the address as just an opaque ID and we'll build a map
from address to a properly configured `ProcessorSupplier`. Then we can
simply return `map::get` as our function.


```java
class GenerateNumbersPMetaSupplier implements ProcessorMetaSupplier {

    private final int upperBound;

    private transient int totalParallelism;
    private transient int localParallelism;

    GenerateNumbersPMetaSupplier(int upperBound) {
        this.upperBound = upperBound;
    }

    @Override
    public void init(@Nonnull Context context) {
        totalParallelism = context.totalParallelism();
        localParallelism = context.localParallelism();
    }

    @Override @Nonnull
    public Function<Address, ProcessorSupplier> get(@Nonnull List<Address> addresses) {
        Map<Address, ProcessorSupplier> map = new HashMap<>();
        for (int i = 0; i < addresses.size(); i++) {
            // We'll calculate the global index of each processor in the cluster:
            int globalIndexBase = localParallelism * i;
            // Capture the value of the transient field for the lambdas below:
            int divisor = totalParallelism;
            // processorCount will be equal to localParallelism:
            ProcessorSupplier supplier = processorCount ->
                    range(globalIndexBase, globalIndexBase + processorCount)
                            .mapToObj(globalIndex ->
                                new GenerateNumbersP(upperBound, divisor, globalIndex)
                            ).collect(toList());
            map.put(addresses.get(i), supplier);
        }
        return map::get;
    }

}
```

We change our DAG-building code to use the meta-supplier:

```java
DAG dag = new DAG();
Vertex generateNumbers = dag.newVertex("generate-numbers",
        new GenerateNumbersPMetaSupplier(upperBound));
Vertex logInput = dag.newVertex("log-input",
        DiagnosticProcessors.writeLogger(i -> "Received number: " + i));
dag.edge(Edge.between(generateNumbers, logInput));
```

After re-running with two Jet members, we should once again see each
number generated just once.

## Sinks

Like a source, a sink is just another kind of processor. It accepts
items from the inbox and pushes them into some system external to the
Jet job (Hazelcast IMap, files, databases, distributed queues, etc.). A
simple way to implement it is to extend `AbstractProcessor` and override
`tryProcess`, which deals with items one at a time. However, sink
processors must often explicitly deal with batching. In this case
directly implementing `Processor` is better because its `process()`
method gets the entire `Inbox` which can be drained to a buffer and
flushed out.

## Example - File Writer

In this example we'll implement a vertex that writes the received items
to files. To avoid contention and conflicts, each processor must write
to its own file. Since we'll be using a `BufferedWriter` which takes
care of the buffering/batching concern, we can use the simpler approach
of extending `AbstractProcessor`:

```java
class WriteFileP extends AbstractProcessor implements Closeable {

    private final String path;

    private transient BufferedWriter writer;

    WriteFileP(String path) {
        setCooperative(false);
        this.path = path;
    }

    @Override
    protected void init(@Nonnull Context context) throws Exception {
        Path path = Paths.get(this.path, context.jetInstance().getName()
                + '-' + context.globalProcessorIndex());
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
    }

    @Override
    protected boolean tryProcess(int ordinal, Object item) throws Exception {
        writer.append(item.toString());
        writer.newLine();
        return true;
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }
}
```

Some comments:

* The constructor declares the processor
[non-cooperative](/Core_API/Processor#page_Cooperative+Multithreading)
because it will perform blocking IO operations.
* `init()` method finds a unique filename for each processor by relying
on the information reachable from the `Context` object.
* Note the careful implementation of `close()`: it first checks if
writer is null, which can happen if `newBufferedWriter()` fails in
`init()`. This would make `init()` fail as well, which would make the
whole job fail and then our `ProcessorSupplier` would call `close()`
to clean up.

Cleaning up on completion/failure is actually the only concern that we
need `ProcessorSupplier` for: the other typical concern, specializing
processors to achieve data partitioning, was achieved directly from the
processor's code. This is the supplier's code:

```java
class WriteFilePSupplier implements ProcessorSupplier {

    private final String path;

    private transient List<WriteFileP> processors;

    WriteFilePSupplier(String path) {
        this.path = path;
    }

    @Override
    public void init(@Nonnull Context context) {
        File homeDir = new File(path);
        boolean success = homeDir.isDirectory() || homeDir.mkdirs();
        if (!success) {
            throw new JetException("Failed to create " + homeDir);
        }
    }

    @Override @Nonnull
    public List<WriteFileP> get(int count) {
        processors = Stream.generate(() -> new WriteFileP(path))
                           .limit(count)
                           .collect(Collectors.toList());
        return processors;
    }

    @Override
    public void complete(Throwable error) {
        for (WriteFileP p : processors) {
            try {
                p.close();
            } catch (IOException e) {
                throw new JetException(e);
            }
        }
    }
}
```

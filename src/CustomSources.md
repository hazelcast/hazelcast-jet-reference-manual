# Implementing Custom Sources and Sinks

Jet provides a flexible API that makes it easy to implement your own
custom sources and sinks. Both sources and sinks are implemented using
the same API as the rest of the `Processor`s.
In this section we will work through some examples as a guide to how you
can connect Jet with your own data sources.

## Sources

One of the main concerns when writing custom sources is that the source
is typically distributed across multiple machines and partitions, and
the work needs to be distributed across multiple nodes and processors.

Jet provides a flexible `ProcessorMetaSupplier` and `ProcessorSupplier`
API which can be used to control how a source is distributed across the
network.

The procedure for generating `Processor` instances are as follows:

1. The `ProcessorMetaSupplier` for the `Vertex` is serialized and sent to
the coordinating member.
2. Coordinator calls `ProcessorMetaSupplier.get()` once for each member
in the cluster and a `ProcessorSupplier` for each member is created.
3. The `ProcessorSupplier` for each member is serialized and sent to that
member.
4. Each member will call their own `ProcessorSupplier` with the correct
count parameter, which corresponds to the `localParallelism` setting of
that vertex.

## Example: Distributed Integer generator

Let's say we want to write a simple source, which generates numbers from
0 to 1,000,000 (exclusive). It is trivial to write a single `Processor`
which can do this using java.util.stream and [`Traverser`](#traverser).

```java
public static class NumberGenerator extends AbstractProcessor {

    private final Traverser<Integer> traverser;

    public NumberGenerator(int limit) {
        traverser = traverseStream(IntStream.range(0, limit).boxed());
    }

    @Override
    public boolean complete() {
        return emitCooperatively(traverser);
    }
}
```

We will also add a simple logging processor, so we can see what values
are generated and received:

```java
public static class PeekProcessor extends AbstractProcessor {
    @Override
    protected boolean tryProcess(int ordinal, @Nonnull Object item) {
        System.out.println("Received number: " + item);
        emit(item);
        return true;
    }
}
```

You can then build your DAG as follows and execute it:

```java
final int limit = 10;
dag.newVertex("number-generator", () -> new NumberGenerator(limit));
// Vertex logger = dag.newVertex("logger-vertex", PeekProcessor::new);
dag.edge(Edge.between(generator, logger));

jet.newJob(dag).execute().get();
```

When you run this code, you'll see output like:

```
Received number: 4
Received number: 0
Received number: 3
Received number: 2
Received number: 2
Received number: 2
```

Since we are using the default parallelism setting on this vertex,
several instances of this processor is created, all of which will be
generating the same sequence of values.

What we actually want is for each processor to generate a subset of the
values. We will start by partitioning the data according to its
remainder,  when divided by the total number of processors. For example,
if we have  8 processors, numbers which have a remainder of 1 when
divided by 8, will go to  processor with index 1.

To do this, we need to implement the `ProcessorSupplier` API:

```java
static class NumberGeneratorSupplier implements ProcessorSupplier {

    private final int limit;

    public NumberGeneratorSupplier(int limit) {
        this.limit = limit;
    }

    @Nonnull
    @Override
    public List<? extends Processor> get(int count) {
        // each processor is responsible for a subset of the numbers.
        return IntStream.range(0, count)
                        .mapToObj(index ->
                            new NumberGenerator(IntStream.range(0, limit)
                                                         .filter(n -> n % count == index))
                        )
                        .collect(Collectors.toList());
    }
}

static class NumberGenerator extends AbstractProcessor {

    private final Traverser<Integer> traverser;

    public NumberGenerator(IntStream stream) {
        traverser = traverseStream(stream.boxed());
    }

    @Override
    public boolean complete() {
        return emitCooperatively(traverser);
    }
}
```

With this approach, each instance of processor will only generate a
subset of the numbers, with each instance generating the numbers where
the remainder of the number divided `count` matches the index of the
processor.

If we add another node to the cluster, we will quickly see that both
nodes are generating the same sequence of numbers. We need to distribute
the work across the cluster, by making sure that each node will generate
a subset of the numbers. We will again follow a similar approach as
above, but now we need to be aware of the global index for each
`Processor` instance.

To achieve this, we need to implement a custom `ProcessorMetaSupplier`.
A `ProcessorMetaSupplier` is called from a single _coordinator_ node,
and creates one `ProcessorSupplier` for each node. The main partition
allocation thus can be done by the `ProcessorMetaSupplier`. Our
distributed number generator source could then look as follows:

```java
static class NumberGeneratorMetaSupplier implements ProcessorMetaSupplier {

    private final int limit;

    private transient int totalParallelism;
    private transient int localParallelism;

    NumberGeneratorMetaSupplier(int limit) {
        this.limit = limit;
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
            Address address = addresses.get(i);
            int start = i * localParallelism;
            int end = (i + 1) * localParallelism;
            int mod = totalParallelism;
            map.put(address, count -> range(start, end)
                    .mapToObj(index -> new NumberGenerator(range(0, limit).filter(f -> f % mod == index)))
                    .collect(toList())
            );
        }
        return map::get;
    }
}
```

The Vertex creation can then be updated as follows:

```java
Vertex generator = dag.newVertex("number-generator", new NumberGeneratorMetaSupplier(limit));
```

## Sinks

Like with sources, sinks are just another kind of `Processor`. Sinks are
typically implemented as `AbstractProcessor` and implement the
`tryProcess` method, and write each incoming item to the sink.

## Example: File Writer

In this example, we will implement a simple DAG that dumps a Hazelcast
IMap into a folder.

As file writing will be distributed, we want each Processor to write to
a separate file, but within the same folder.

We can achieve this by implementing a `ProcessorSupplier` and
a corresponding `Processor`:

```java
static class Supplier implements ProcessorSupplier {

    private final String path;

    private transient List<Writer> writers;

    Supplier(String path) {
        this.path = path;
    }

    @Override
    public void init(@Nonnull Context context) {
        new File(path).mkdirs();
    }

    @Nonnull @Override
    public List<Writer> get(int count) {
        return writers = range(0, count)
                .mapToObj(e -> new Writer(path))
                .collect(Collectors.toList());
    }

    @Override
    public void complete(Throwable error) {
        writers.forEach(p -> {
            try {
                p.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

It can be seen in the implementation that `ProcessorSupplier` holds a
reference to all the Processors. This is not normally necessary, but in
this case we want to be able to close all the file writers gracefully
when the job execution is completed. `complete()` in `ProcessorSupplier`
is always called, even if the job fails with an exception or is
cancelled.

The `Processor` implementation itself is fairly straightforward:

```java
static class Writer extends AbstractProcessor implements Closeable {

    static final Charset UTF8 = Charset.forName("UTF-8");
    private final String path;

    private transient BufferedWriter writer;

    Writer(String path) {
        this.path = path;
    }

    @Override
    protected void init(@Nonnull Context context) throws Exception {
        Path path = Paths.get(this.path, context.jetInstance().getName() + "-" + context.index());
        try {
            writer = Files.newBufferedWriter(path, UTF8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean tryProcess(int ordinal, @Nonnull Object item) throws Exception {
        writer.append(item.toString());
        writer.newLine();
        return true;
    }

    @Override
    public boolean isCooperative() {
        return false;
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }
}
```

The init method appends the current node name as well as the processor
index to the file name. This ensures that each `Processor` instance is
writing to a  unique file.

The close method is called by the `Supplier`, after job execution is
completed.

This processor is also marked as [_non-cooperative_](#cooperative-multithreading)
since it makes blocking calls to the file system.

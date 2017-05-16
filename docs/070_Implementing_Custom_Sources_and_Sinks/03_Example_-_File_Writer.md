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

* The constructor declares the processor [non-cooperative](/04_Understanding_Jet_Architecture_and_API/03_Processor/00_Cooperative_Multithreading.md) 
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


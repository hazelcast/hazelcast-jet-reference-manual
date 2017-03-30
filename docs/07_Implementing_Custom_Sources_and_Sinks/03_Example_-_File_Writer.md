In this example, we will implement a simple DAG that dumps a Hazelcast
IMap into a folder.

As file writing will be distributed, we want each Processor writing to
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

The `init` method appends the current member name as well as the processor
index to the file name. This ensures that each `Processor` instance is
writing to a unique file.

The `close` method is called by the `Supplier`, after the job execution is
completed.

This processor is also marked as [_non-cooperative_](/04_Understanding_Jet_Architecture_and_API/03_Processor/00_Cooperative_Multithreading.md)
since it makes blocking calls to the file system.
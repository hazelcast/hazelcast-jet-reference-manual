Let's say we want to write a simple source that will generate numbers from
0 to 1,000,000 (exclusive). It is trivial to write a single `Processor`
which can do this using `java.util.stream` and [`Traverser`](/04_Understanding_Jet_Architecture_and_API/05_Convenience_API_to_Implement_a_Processor/01_Traverser.md).

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

We will also add a simple logging processor so we can see what values
are generated:

```java
class LogInputP extends AbstractProcessor {

    LogInputP() {
        setCooperative(false);
    }

    @Override
    protected boolean tryProcess(int ordinal, @Nonnull Object item) {
        System.out.println("Received number: " + item);
        emit(item);
        return true;
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
Vertex logInput = dag.newVertex("log-input", LogInputP::new);
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
generated the same sequence of values. Generally we'll want the ability
to parallelize the source vertex, so we have to make each processor emit
only a slice of the total data set.

So far we've used the simplest approach to creating processors: a
`Supplier<Processor>` function that keeps returning equal instances of
processors. Now we'll step up to Jet's custom interface that gives us
the ability to provide a list of separately configured processors:
`ProcessorSupplier` and its method `get(int processorCount)`. 

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
Vertex logInput = dag.newVertex("log-input", LogInputP::new);
dag.edge(Edge.between(generateNumbers, logInput));
```

Now we can re-run our example and see that indeed each number occurs
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
Vertex logInput = dag.newVertex("log-input", LogInputP::new);
dag.edge(Edge.between(generateNumbers, logInput));
```

After re-running with two Jet members, we should once again see each
number generated just once.

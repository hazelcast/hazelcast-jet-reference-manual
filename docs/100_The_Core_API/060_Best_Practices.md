## Inspecting Processor Input and Output

The structure of the DAG model is a very poor match for Java's type
system, which results in the lack of compile-time type safety between
connected vertices. Developing a type-correct DAG therefore usually
requires some trial and error. To facilitate this process, but also to
allow many more kinds of diagnostics and debugging, Jet's library offers
ways to capture the input/output of a vertex and inspect it.

### Peeking with processor wrappers

The first approach is to decorate a vertex declaration with a layer that
will log all the data traffic going through it. This support is present
in the `DiagnosticProcessors` factory class, which contains the
following methods:

* `peekInput()`: logs items received at any edge ordinal.

* `peekOutput()`: logs items emitted to any ordinal. An item emitted to 
several ordinals is logged just once.

These methods take two optional parameters:

* `toStringF` returns the string representation of an item. The default
is to use `Object.toString()`.
* `shouldLogF` is a filtering function so you can focus your log output
only on some specific items. The default is to log all items.

#### Example usage

Suppose we have declared the second-stage vertex in a two-stage
aggregation setup:

```java
Vertex combine = dag.newVertex("combine", 
    combineByKey(counting()));
```

We'd like to see what exactly we're getting from the first stage, so
we'll wrap the processor supplier with `peekInput()`:

```java
Vertex combine = dag.newVertex("combine", 
    peekInput(combineByKey(counting())));
```

Keep in mind that logging happens on the machine running hosting the
processor, so this technique is primarily targetted to Jet jobs the
developer runs locally in his development environment.

### Attaching a sink vertex

Since most vertices are implemented to emit the same data stream to all
attached edges, it is usually possible to attach a diagnostic sink to
any vertex. For example, Jet's standard `writeFile()` sink can be very
useful here.

#### Example usage

In the example from the Word Count tutorial we can add the following
declarations:

```java
Vertex diagnose = dag.newVertex("diagnose",
        Sinks.writeFile("tokenize-output"))
        .localParallelism(1);
dag.edge(from(tokenize, 1).to(diagnose));
```

This will create the directory `tokenize-output` which will contain one
file per processor instance running on the machine. When running in a
cluster, you can inspect on each member the input seen on that member.
By specifying the `allToOne()` routing policy you can also have the
output of all the processors on all the members saved on a single member
(although the choice of exactly which member will be arbitrary).

## How to Unit-Test a Processor

Utility classes for unit testing are provided as part of the core API
inside `com.hazelcast.jet.core.test` package. Using these utility classes,
you can unit test custom processors by passing them input items and
asserting the expected output.

Start by calling `TestSupport.verifyProcessor()` by passing it a processor
supplier or a by directly passing a processor instance.

The test process does the following:

* initializes the processor by calling `Processor.init()`
* does snapshot+restore (optional, see below)
* calls `Processor.process(0, inbox)`. The inbox always contains one item 
from `input` parameter
* every time the inbox gets empty does snapshot+restore
* calls `Processor.complete()` until it returns `true` (optional)
* does snapshot+restore after `complete()` returned `false`

The optional snapshot+restore test procedure:
* `saveToSnapshot()` is called
* new processor instance is created, from now on only this instance will be 
used
* snapshot is restored using `restoreFromSnapshot()`
* `finishSnapshotRestore()` is called

For each call to any processing method the progress is asserted (optional). 
The processor must do at least one of these:
* take something from inbox
* put something to outbox
* for `boolean`-returning methods, returning `true` is considered as making 
progress

#### Cooperative processors

For cooperative processors a 1-capacity outbox will be provided, which will 
additionally be full in every other call to `process()`. This will test the edge 
case: the `process()` method is called even when the outbox is full to give 
the processor a chance to process inbox. The snapshot outbox will also have 
capacity of 1 for a cooperative processor.

Additionally, time spent in each call to processing method must not exceed 
timeout specified by `cooperativeTimeout(long)`.

#### Not-covered cases

This class does not cover these cases:
* Testing of processors which distinguish input or output edges by ordinal
* Checking that the state of a stateful processor is empty at the end (you can 
do that yourself afterwards with the last instance returned from your supplier).
* This utility never calls `Processor.tryProcess()`.

#### Example usage

This will test one of the jet-provided processors:

```java
TestSupport.verifyProcessor(Processors.map((String s) -> s.toUpperCase()))
           .disableCompleteCall()             // enabled by default
           .disableLogging()                  // enabled by default
           .disableProgressAssertion()        // enabled by default
           .disableSnapshots()                // enabled by default
           .cooperativeTimeout(<timeoutInMs>) // default is 1000
           .outputChecker(<function>)         // default is `Objects::equal`
           .input(asList("foo", "bar"))       // default is `emptyList()`
           .expectOutput(asList("FOO", "BAR"));
```

#### Other utility classes

Following classes are suitable to be used as implementations of Jet interfaces 
in tests: 
* `TestInbox`
* `TestOutbox`
* `TestProcessorContext`
* `TestProcessorSupplierContext`
* `TestProcessorMetaSupplierContext`
* `JetAssert`: mini implementation of JUnit-like `assert` methods to avoid
dependency on JUnit

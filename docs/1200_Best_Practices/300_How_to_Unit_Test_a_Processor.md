A utility class to test custom processors is provided in the 
`hazelcast-jet-test-support` module. You can unit test custom processors 
by passing them with input objects and asserting the expected output.

A `TestSupport.testProcessor()` set of methods is provided for the 
typical case.

For cooperative processors a 1-capacity outbox will be provided, which 
will additionally be full on every other processing method call. This 
will test edge cases in cooperative processors.

This method does the following:

* initializes the processor by calling 
`Processor.init()` 

* calls `Processor.process(0, inbox)`, the `inbox` contains all items 
from `input` parameter

* asserts the progress of the `process()` call: that something was taken 
from the inbox or put to the outbox

* calls `Processor.complete()` until it returns `true`

* asserts the progress of the `complete()` call if it returned `false`: 
something must have been put to the outbox.

Note that this method never calls `Processor.tryProcess()`.

This class does not cover these cases:

* testing of processors which distinguish input or output edges by 
ordinal.

* checking that the state of a stateful processor is empty at the end 
(you can do that yourself afterwards).

Example usage. This will test one of the jet-provided processors:

```java
      TestSupport.testProcessor(
              Processors.map((String s) -> s.toUpperCase()),
              asList("foo", "bar"),
              asList("FOO", "BAR")
      );
```

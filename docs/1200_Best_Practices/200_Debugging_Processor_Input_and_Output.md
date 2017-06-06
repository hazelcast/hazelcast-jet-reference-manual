The `DiagnosticProcessors` class contains wrappers useful for debugging 
your DAG. It has following methods:

* `peekInput()`: logs input objects from all ordinals to logger

* `peekOutput()`: logs objects output to any ordinal to logger. Object 
emitted to multiple ordinals is logged just once.

Both methods come in overloaded versions for each type of processor 
supplier (the `Supplier<Processor>`, `ProcessorSupplier` and 
`ProcessorMetaSupplier`) and with and without optional `toStringF` and 
`shouldLogF`.

### Example usage

To peek on input of some vertex, we need to replace this line:

```java
Vertex combine = dag.newVertex("combine", combineByKey(counting()));
```

with the following one: just wrap the processor supplier in `peekInput()`:

```java
Vertex combine = dag.newVertex("combine", peekInput(combineByKey(counting())));
```

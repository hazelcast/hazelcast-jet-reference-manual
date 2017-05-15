`Traverser` is a very simple functional interface whose shape matches
that of a `Supplier`, but with a contract specialized for the traversal
over a sequence of non-null items: each call to its `next()` method
returns another item of the sequence until exhausted, then keeps
returning `null`. A traverser may also represent an infinite,
non-blocking stream of items: it may return `null` when no item is
currently available, then later return more items.

The point of this type is the ability to implement traversal over any kind of dataset or lazy sequence with minimum hassle: often just by providing a one-liner lambda expression. This makes it very easy to integrate with Jet's convenience APIs for cooperative processors.

`Traverser` also supports some `default` methods that facilitate
building a simple transformation layer over the underlying sequence:
`map`, `filter`, `flatMap`, etc.

The following example shows how you can implement a simple flatmapping
processor:

```java
public class ItemAndSuccessorP extends AbstractProcessor {
    private final FlatMapper<Integer, Integer> flatMapper =
        flatMapper(i -> traverseIterable(asList(i, i + 1)));

    @Override
    protected boolean tryProcess(int ordinal, Object item) {
        return flatMapper.tryProcess((int) item);
    }
}
```

For each received `Integer` item this processor emits the item and its
successor. It does not differentiate between inbound edges (treats data
from all edges the same way) and emits each item to all outbound edges
connected to its vertex.

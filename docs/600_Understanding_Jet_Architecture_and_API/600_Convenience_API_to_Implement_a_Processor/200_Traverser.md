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

For each received `Integer` this processor emits the number and its
successor. If the outbox refuses an item, `flatMapper.tryProcess()` returns `false` and stays ready to resume the next time it is invoked. The fact that it returned `false` signals Jet to invoke `ItemAndSuccessorP.tryProcess()` again with the same arguments.

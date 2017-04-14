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

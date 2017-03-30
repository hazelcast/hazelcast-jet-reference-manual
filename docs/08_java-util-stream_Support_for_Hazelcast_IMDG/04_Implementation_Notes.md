Jet's `java.util.stream` implementation will automatically convert a
stream into a `DAG` when one of the terminal methods are called. The DAG
creation is done lazily, and only if a terminal method is called.

The following DAG will be compiled as follows:

```java
IStreamMap<String, Integer> ints = jet.getMap("ints");
int result = ints.stream().map(Entry::getValue)
                 .reduce(0, (l, r) -> l + r);
```

![image](../images/jus-dag.jpg)
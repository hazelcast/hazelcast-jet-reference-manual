We want to count how many times each word occurs in the text. If we want
to do a word count without using a DAG and in a single-threaded
computation, we could do it as shown below:

```java
Pattern pattern = Pattern.compile("\\s+");
Map<String, Long> counts = new HashMap<>();
for (String line : map.values()) {
    for (String word : pattern.split(line)) {
        counts.compute(word.toLowerCase(), (w, c) -> c == null ? 1L : c + 1);
    }
}
```

As soon as we try to parallelize this computation, it becomes clear that
we have to model it differently. More complications arise on top of that
when we want to scale out across multiple machines.
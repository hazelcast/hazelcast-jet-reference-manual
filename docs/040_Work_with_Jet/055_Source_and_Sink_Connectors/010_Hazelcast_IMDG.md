[TOC]

## IMap and ICache

Hazelcast IMDG's `IMap` and `ICache` are very similar in the way Jet
uses them and largely interchangeable. `IMap` has a bit more features.
The simplest way to use them is as finite sources of their contents, but
if you enable the Event Journal on a map/cache, you'll be able to use
it as a source of an infinite stream of update events
([see below](#page_Receive+an+Infinite+Stream+of+Update+Events)).

The most basic usage is very simple, here are snippets to use `IMap`
and `ICache` as a source and a sink:

```java
Pipeline p = p.create();
ComputeStage<Entry<String, Long>> stage =
        p.drawFrom(Sources.<String, Long>map("myMap"));
stage.drainTo(Sinks.map("myMap"));
```

```java
Pipeline p = p.create();
ComputeStage<Entry<String, Long>> stage =
        p.drawFrom(Sources.<String, Long>cache("myCache"));
stage.drainTo(Sinks.cache("myCache"));
```

In these snippets we draw from and drain to the same kind of structure,
but you can use any combination.

### Access an External Cluster

To access a Hazelcast IMDG cluster separate from the Jet cluster, you
have to provide Hazelcast client configuration for the connection. In
this simple example we use programmatic configuration to draw from and
drain to remote `IMap` and `ICache`. Just for variety, we funnel the
data from `IMap` to `ICache` and vice versa:

```java
ClientConfig cfg = new ClientConfig();
cfg.getGroupConfig().setName("myGroup").setPassword("pAssw0rd");
cfg.getNetworkConfig().addAddress("node1.mydomain.com", "node2.mydomain.com");

Pipeline p = p.create();
ComputeStage<Entry<String, Long>> fromMap =
        p.drawFrom(Sources.<String, Long>remoteMap("inputMap", cfg));
ComputeStage<Entry<String, Long>> fromCache =
        p.drawFrom(Sources.<String, Long>remoteCache("inputCache", cfg));
fromMap.drainTo(Sinks.remoteCache("outputCache", cfg));
fromCache.drainTo(Sinks.remoteMap("outputMap", cfg));
```

For a full discussion on how to configure your client connection, refer
to the
[Hazelcast IMDG documentation](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#configuring-java-client)
on this topic.

### Optimize Data Traffic

If your use case calls for some filtering and/or transformation of the
data you retrieve, you can optimize the traffic volume by providing a
filtering predicate and an arbitrary transformation function to the
source connector itself and they'll get applied on the remote side,
before sending:

```java
Pipeline p = p.create();
p.drawFrom(Sources.<String, Person, Integer>remoteMap(
        "inputMap", clientConfig,
        e -> e.getValue().getAge() > 21,
        e -> e.getValue().getAge()));
```

The same optimization works on a local `IMap`, too, but has less impact.
However, Hazelcast IMDG goes a step further in optimizing your filtering
and mapping to a degree that matters even locally. If you don't need
fully general functions, but can express your predicate via
[`Predicates`](http://docs.hazelcast.org/docs/latest/javadoc/com/hazelcast/query/Predicates.html)
or
[`PredicateBuilder`](http://docs.hazelcast.org/docs/latest/javadoc/com/hazelcast/query/PredicateBuilder.html),
they will create a specialized predicate instance that can test the
object without deserializing it. Similarly, if the mapping you need is
of a constrained kind where you just extract one or more object fields
(attributes), you can specify a _projection_ instead of a general
mapping lambda:
[`Projections.singleAttribute()`](http://docs.hazelcast.org/docs/latest/javadoc/com/hazelcast/projection/Projections.html#singleAttribute-java.lang.String-)
or [
`Projections.multiAttribute()`](http://docs.hazelcast.org/docs/latest/javadoc/com/hazelcast/projection/Projections.html#multiAttribute-java.lang.String...-).
These will extract the listed attributes without deserializing the whole
object. For these optimizations to work, however, your objects must
employ Hazelcast's [portable serialization](http://docs.hazelcast.org/docs/3.9/manual/html-single/index.html#implementing-portable-serialization).
They are especially relevant if the volume of data you need in the Jet
job is significantly less than the volume of the stored data.

Note that the above feature is not available on `ICache`. It is,
however, available on `ICache`'s event journal, which we introduce next.

### Receive an Infinite Stream of Update Events

You can use `IMap`/`ICache` as sources of infinite event streams. For
this to work you have to enable the Event Journal on your data
structure. This is a feature you set in the Jet/IMDG instance
configuration, which means you cannot change it while the cluster is
running.

This is how you enable the Event Journal on an `IMap`:

```java
JetConfig cfg = new JetConfig();
cfg.getHazelcastConfig()
   .getMapEventJournalConfig("inputMap")
   .setEnabled(true)
   .setCapacity(1000) // how many events to keep before evicting
   .setTimeToLiveSeconds(10); // evict events older than this
JetInstance jet = Jet.newJetInstance(cfg);
```

The default journal capacity is 10,000 and the default time-to-live is 0
(which means "unlimited"). Since the entire event journal is kept in
RAM, you should take care to adjust these values to match your use case.

The configuration API for `ICache` is identical:

```java
cfg.getHazelcastConfig()
   .getCacheEventJournalConfig("inputCache")
   .setEnabled(true)
   .setCapacity(1000)
   .setTimeToLiveSeconds(10);
```

Once properly configured, you use Event Journal sources like this:

```java
Pipeline p = p.create();
ComputeStage<EventJournalMapEvent<String, Long>> fromMap =
        p.drawFrom(Sources.<String, Long>mapJournal("inputMap", true));
ComputeStage<EventJournalCacheEvent<String, Long>> fromCache =
        p.drawFrom(Sources.<String, Long>cacheJournal("inputCache", true));
```

`IMap` and `ICache` are on an equal footing here. The second argument,
`true` here, means "start receiving from the latest update event". If
you specify `false`, you'll get all the events still on record.

Note the type of the stream element: `EventJournalMapEvent` and
`EventJournalCacheEvent`. These are almost the same and have these
methods:

- `getKey()`
- `getOldValue()`
- `getNewValue()`
- `getType()`

The only difference is the return type of `getType()` which is specific
to each kind of structure and gives detailed insight into what kind of
event it reports. _Add_, _remove_ and _update_ are the basic ones, but
there are also _evict_, _clear_, _expire_ and some others. When you use
the Event Journal as a stream source, most often you'll care just about
the basic event types and just the key and the new value. You can supply
the appropriate filtering and mapping functions to the source:

```java
EnumSet<EntryEventType> evTypesToAccept =
        EnumSet.of(ADDED, REMOVED, UPDATED);
ComputeStage<Entry<String, Long>> stage = p.drawFrom(
        Sources.<String, Long, Entry<String, Long>>mapJournal("inputMap",
                e -> evTypesToAccept.contains(e.getType()),
                e -> entry(e.getKey(), e.getNewValue()),
                true));
```

Finally, you can get all of the above from a map/cache in another
cluster, you just have to prepend `remote` to the source names and add
`ClientConfig`, for example:

```java
ComputeStage<EventJournalMapEvent<String, Long>> fromRemoteMap = p.drawFrom(
        Sources.<String, Long>remoteMapJournal("inputMap", clientConfig(), true));
ComputeStage<EventJournalCacheEvent<String, Long>> fromRemoteCache = p.drawFrom(
        Sources.<String, Long>remoteCacheJournal("inputCache", clientConfig(), true));
```

## IList

Whereas `IMap` and `ICache` are the recommended choice of data sources and sinks in Jet jobs, Jet supports `IList` purely for convenience during prototyping, unit testing and similar non-production situations. It is not a partitioned and distributed data structure and only one cluster member has all the contents. In a distributed Jet job all the members will compete for access to the single member holding it.

With that said, `IList` is very simple to use. Here's an example how to fill it with test data, consume it in a Jet job, dump its results into another list, and fetch the results (we assume you already have a Jet instance in the variable `jet`):

```java
IList<Integer> inputList = jet.getList("inputList");
for (int i = 0; i < 10; i++) {
    inputList.add(i);
}

Pipeline p = Pipeline.create();
p.drawFrom(Sources.<Integer>list("inputList"))
 .map(i -> "item" + i)
 .drainTo(Sinks.list("resultList"));

jet.newJob(p).join();

IList<String> resultList = jet.getList("resultList");
System.out.println("Results: " + new ArrayList<>(resultList));
```

You can access a list in an external cluster as well, by providing a `ClientConfig` object:

```java
ClientConfig clientConfig = new ClientConfig();
clientConfig.getGroupConfig().setName("myGroup").setPassword("pAssw0rd");
clientConfig.getNetworkConfig().addAddress("node1.mydomain.com", "node2.mydomain.com");

Pipeline p = Pipeline.create();
ComputeStage<Object> stage = p.drawFrom(Sources.remoteList("inputlist", clientConfig));
stage.drainTo(Sinks.remoteList("resultList", clientConfig));
```

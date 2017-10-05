Processors keep internal state in memory and it will be lost in case of 
failure. This state includes accumulated values in windowing 
aggregations or event offsets for sources. It is important – especially 
for streaming jobs – to save this state to persistent storage, should 
the job fail.

Jet supports distributed state snapshots and automatic restarts. These 
are off by default and must be enabled manually.

```java
JobConfig config = new JobConfig();
config.setSnapshotIntervalMillis(MINUTES.toMillis(1));
config.setAutomaticRestartsEnabled(true);
config.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
config.setSplitBrainProtection(true);
jetInstance.newJob(pipeline, config);
```

Jet doesn't rely on any 3rd party service to orchestrate job life cycle 
and failures. When you submit a job, it stores the job’s metadata in 
internal HZ `IMaps`.

When there is a failure in the cluster, Jet automatically restarts all 
jobs that contain the failed member as a job participant. Only events 
such as failed network connection, member shutdown or communication 
timeouts are considered as _restartable_. Other failures do not cause 
the job to restart automatically. Note that IO failures might or might 
not be handled by particular source/sink implementation, which might 
handle them transparently. Refer to their documentation for more 
information.

In current version the job cannot be restarted manually. If you cancel 
the job, it is interpreted as a termination and all snapshots are 
deleted.

#### Processing guarantee

You can choose between _exactly once_ and _at least once_ processing. 
The trade-off is between correctness and performance. It's configured 
per-job.

* _Exactly once:_ Favors correctness. Guarantees that each event is 
processed exactly once by the processors. Might increase latency and 
decrease throughput due to aligning of the barriers.
* _At least once:_ Events which came after the barrier in the stream 
might be processed before the snapshot is taken. This will cause their 
duplicate processing, if the job is restarted.

The distributed snapshot algorithm works by sending _barriers_ down the 
stream. When one is received by a processor, it must do a state 
snapshot. However, the processor can have multiple inputs, so to save 
correct snapshot it must wait until the barrier is received from all 
other inputs and not process any more items from inputs from which the 
barrier was already received. If the stream is skewed (due to partition 
imbalance, long GC pause on some member or a network hiccup), processing 
has to be halted until the situation recovers. _At least once_ mode 
allows processing of further items during this alignment period.

#### Split-brain protection

You can enable split brain protection when you submit a job. If enabled 
and a network partitioning occurs, Jet guarantees that the job will only 
be restarted on the bigger sub-cluster.

It works like this: The cluster size is saved to job metadata when the 
job is first started. If the job is about to be restarted the quorum 
must be met. Quorum is calculated as `originalClusterSize / 2 + 1`. If 
current cluster has less members that the necessary quorum, job restart 
will be retried later. Note that this also prevents the job from 
restarting, if you intentionally shut down half of the members.

#### Sink and source guarantees

**Sources**

Only sources which are able to restart emission at position saved to a 
state snapshot can provide _exactly once_ guarantee. Sources that do not 
provide _at most once_ guarantee.

These sources currently provide _exactly once_ guarantee: 
* `streamMap()`
* `streamKafka()`

 **Sinks**
 
_Exactly once_ guarantee is provided for sinks which do idempotent 
updates. That is, if they receive an item with the same key, they 
overwrite the value instead of appending it. This also assumes that the 
keys are normally unique, that is in case when there is no failure, no 
duplicate key is output.

These sinks currently provide _exactly once_ guarantee:
* `writeMap()`

Note that it is also possible to support idempotence on application 
level. For example, event written to kafka topic can contain unique ID. 
The process reading from that topic can use the ID to ignore the event 
in case it is received for the second time.

#### Configuring the backup count of internal maps

Jet internally uses IMaps to store job metadata and snapshots. You can 
configure synchronous backup count of those maps. 

For example, if backup count is set to 2, all job metadata and snapshot 
data will be replicated to two other members. If snapshots are enabled 
in the case that at most two members fail simultaneously the job can be 
restarted and continue from latest snapshot without data loss.

```java
JetConfig config = new JetConfig();
config.getInstanceConfig().setBackupCount(10);
JetInstance = Jet.newJetInstance(config);
```

#### Postponing of snapshots when edge priority is used

If your DAG has vertices with multiple input edges and they have 
different priority, state snapshots cannot be made before all higher 
priority edges complete. First snapshot is only started after this 
happens.

Reason for this is that after receiving a barrier we stop processing 
items on that edge until the barrier is received from all other edges. 
But we don't process edges with lower priority until higher priority 
edges are done which prevents receiving the barrier from them, which in 
turn stalls the job indefinitely.

Technically this only applies to _exactly once_ processing guarantee, 
but the snapshot is also postponed for _at least once_ jobs, because the 
snapshot won't complete until after all higher priority edges are 
completed anyway, and will only increase the number of duplicately 
processed items.

#### Fault tolerance in batch jobs

Jet makes no distinction between streaming and batch jobs. Snapshotting 
can be enabled for all of them provided the involved processors support 
them.

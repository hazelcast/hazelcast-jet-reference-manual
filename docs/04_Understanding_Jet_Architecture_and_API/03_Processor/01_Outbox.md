The processor sends its output items to its `Outbox`, which has a
separate bucket for each outbound edge. The buckets have limited
capacity and will refuse an item when full. A cooperative processor
should be implemented such that when its item is rejected by the outbox,
it saves its processing state and returns from the processing method.
The execution engine will then drain the outbox buckets.

By contrast, a non-cooperative processor gets an auto-flushing, blocking
outbox that never rejects an item. This can be leveraged to simplify the
processor's implementation; however the simplification alone should
never be the reason to declare a processor non-cooperative.

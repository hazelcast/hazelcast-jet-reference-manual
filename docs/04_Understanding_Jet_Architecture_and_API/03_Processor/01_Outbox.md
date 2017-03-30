The processor deposits the items it wants to emit to an instance of
`Outbox`, which has a separate bucket for each outbound edge. The
buckets are unbounded, but each has a defined "high water mark" that
says when the bucket should be considered full. When the processor
realizes it has hit the high water mark, it should return from the
current processing callback and let the execution engine drain the
outbox.
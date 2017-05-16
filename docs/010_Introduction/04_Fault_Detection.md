In its current version, Hazelcast Jet can only detect a failure in one
of the cluster members that was running the computation, and abort the
job. A feature planned for the future is _fault tolerance_: the ability
to go back to a saved snapshot of the computation state and resume the
computation without the failed member.
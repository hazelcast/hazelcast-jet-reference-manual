- All the code and state needed for the Jet job must be declared in the 
classes that become a part of the job's definition through 
`JobConfig.addClass()` or `addJar()`.

- If you have a client connecting to your Jet cluster, the Jet job 
should never have to refer to `ClientConfig`. Create a separate 
`DagBuilder` class using the `buildDag()` method; this class should not 
have any references to the `JobHelper` class.

- You should have a careful control over the object graph which is 
submitted with the Jet job. Please be aware that inner classes/lambdas 
may inadvertently capture their parent classes which will cause 
serialization errors.
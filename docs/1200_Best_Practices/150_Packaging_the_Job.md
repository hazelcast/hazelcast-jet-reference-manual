One way to easily submit the job to a Jet cluster is by using the 
`submit-job.sh` script (`submit-job.bat` on Windows).

The main issue with achieving this is that the JAR must be attached as a 
resource to the job being submitted, so the Jet cluster will be able to 
load and use its classes. However, from within a running `main()` method 
it is not trivial to find out the filename of the JAR containing it.

To use the `submit-job` script, follow these steps:

* Write your `main()` method and your Jet code the usual way, except 
for calling `JetBootstrap.getInstance()` to acquire a Jet client 
instance (instead of `Jet.newJetClient()`).

* Create a runnable JAR with your entry point declared as the 
`Main-Class` in `MANIFEST.MF`.

* Run your JAR, but instead of `java -jar jetjob.jar` use `submit-jet.sh 
jetjob.jar`. The script is found in the Jet distribution zipfile, in the 
`bin` directory. On Windows use `submit-jet.bat`.

* The Jet client will be configured from `hazelcast-client.xml` found in 
the `config` directory in Jet's distribution directory structure. Adjust 
that file to suit your needs.

For example, write a class like this:

```java
public class CustomJetJob {
  public static void main(String[] args) {
    JetInstance jet = JetBootstrap.getInstance();
    jet.newJob(buildDag()).execute().get();
  }
  
  public static DAG buildDag() {
    // ...
  }
}
````
   
After building the JAR, submit the job:

```
$ submit-jet.sh jetjob.jar
```

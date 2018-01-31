In this section we'll get you started using Hazelcast Jet. We'll
show you how to set up a Java project with the proper dependencies and a
quick Hello World example to verify your setup. 

## Requirements

In the good tradition of Hazelcast products, Jet is distributed as a JAR
with no other dependencies. It requires JRE version 8 or higher to run.

## Using Maven and Gradle

The easiest way to start using Hazelcast Jet is to add it as a
dependency to your project.

Hazelcast Jet is published on the Maven repositories. Add the following
lines to your `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>com.hazelcast.jet</groupId>
    <artifactId>hazelcast-jet</artifactId>
    <version>0.5</version>
  </dependency>
</dependencies>
```

If you prefer to use Gradle, execute the following command:

```groovy
compile 'com.hazelcast.jet:hazelcast-jet:0.5'
```

## Downloading

Alternatively you can download the latest [distribution package of
Hazelcast Jet](http://jet.hazelcast.org/download/)
and add the `hazelcast-jet-<version>.jar` file to your classpath.

### Distribution Package

The distribution package contains the following scripts to help you get
started with Hazelcast Jet:

* `bin/jet-start.sh` and `bin/jet-start.bat` start a new Jet member in
the current directory.
* `bin/jet-stop.sh` and `bin/jet-stop.bat` stop the member started in 
the current directory.
* `bin/jet-submit.sh` and `bin/jet-submit.bat` submit a Jet computation 
job that was packaged in a self-contained JAR file.
* `bin/cluster.sh` provides basic functionality for Hazelcast cluster
manager, such as changing the cluster state, shutting down the cluster
or forcing the cluster to clean its persisted data.

## Verify Your Setup

You can verify your setup by running this simple program. It processes
the contents of a Hazelcast `IList` that contains lines of text, finds 
the number of occurrences of each word in it, and stores its results
in a Hazelcast `IMap`. In a distributed  computation job the input and 
output cannot be simple in-memory structures like a Java `List`; they 
must reside in the cluster so any member can access them. This is why we 
use Hazelcast structures.

```java
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Pipeline;
import com.hazelcast.jet.Sinks;
import com.hazelcast.jet.Sources;

import java.util.List;
import java.util.Map;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        // Create the specification of the computation pipeline. Note that it is
        // a pure POJO: no instance of Jet is needed to create it.
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("text"))
         .flatMap(word -> traverseArray(word.toLowerCase().split("\\W+")))
         .filter(word -> !word.isEmpty())
         .groupBy(wholeItem(), counting())
         .drainTo(Sinks.map("counts"));

        // Start Jet, populate the input list
        JetInstance jet = Jet.newJetInstance();
        try {
            List<String> text = jet.getList("text");
            text.add("hello world hello hello world");
            text.add("world world hello world");

            // Perform the computation
            jet.newJob(p).join();

            // Check the results
            Map<String, Long> counts = jet.getMap("counts");
            System.out.println("Count of hello: " + counts.get("hello"));
            System.out.println("Count of world: " + counts.get("world"));
        } finally {
            Jet.shutdownAll();
        }
    }
}
```

You should expect to see a lot of logging output from Jet (sent to
`stderr`) and two lines on `stdout`:

```text
Count of hello: 4
Count of world: 5
```

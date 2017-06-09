In this section we'll get you started using Hazelcast Jet. First we'll show you how to set up a Java project with the proper dependencies. This will be followed by two tutorials through which you'll learn about Jet's distributed computation model, its Core API, and how to use it to create and execute batch and streaming computation jobs.

## Requirements

In the good tradition of Hazelcast products, Jet is distributed as a JAR
with no other dependencies except Hazelcast IMDG itself. It requires JRE version 8 or higher to run.

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
    <version>0.4</version>
  </dependency>
</dependencies>
```

If you prefer to use Gradle, execute the following command:

```groovy
compile 'com.hazelcast.jet:hazelcast-jet:0.4'
```

## Downloading

Alternatively you can download the latest [distribution package of
Hazelcast Jet](http://jet.hazelcast.org/download/)
and add the `hazelcast-jet-<version>.jar` file to your classpath.

### Distribution Package

The distribution package contains the following scripts to help you get
started with Hazelcast Jet:

* `bin/start-jet.sh` and `bin/start-jet.bat` start a new Jet member in the
current directory.
* `bin/stop-jet.sh` and `bin/stop-jet.bat` stop the member started in the
current directory.
* `bin/submit-jet.sh` and `bin/submit-jet.bat` submit a Jet computation job
that was packaged in a self-contained JAR file.
* `bin/cluster.sh` provides basic functionality for Hazelcast cluster
manager, such as changing the cluster state, shutting down the cluster
or forcing the cluster to clean its persisted data.

![Note](images/NoteSmall.png)***NOTE***: *`start-jet.sh` / `start-jet.bat`
scripts let you start one Jet member per folder. To start a new
instance, please unzip the package in a new folder.*

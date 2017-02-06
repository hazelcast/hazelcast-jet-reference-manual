# Getting Started

This chapter explains how to start using Hazelcast Jet. It also describes the executable files in the downloaded distribution package.

## Requirements

Hazelcast Jet requires a minimum JDK version of 8.


## Using Maven and Gradle

The easiest way to start using Hazelcast Jet is to add it as a dependency to your
project.


You can find Hazelcast Jet in Maven repositories. Add the following lines to your `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>com.hazelcast.jet</groupId>
    <artifactId>hazelcast-jet</artifactId>
    <version>0.3</version>
  </dependency>
</dependencies>
```

If you prefer to use Gradle, execute the following command:

```groovy
compile 'com.hazelcast.jet:hazelcast-jet:0.3'
```

## Downloading

Alternatively, you can download the latest [distribution package for Hazelcast Jet](http://jet.hazelcast.org/download/)
and add the `hazelcast-jet-<version>.jar` file to your classpath.

### Distribution Package

The distribution package contains the following scripts to help you get started
with Hazelcast Jet:

* `bin/start.sh` and `bin/start.bat` start a new Jet member in the
current directory.
* `bin/stop.sh` and `bin/stop.bat` stop the member started in the
current directory.
* `bin/cluster.sh` provides basic functionality for Hazelcast cluster
manager, such as changing the cluster state, shutting down the cluster or
forcing the cluster to clean its persisted data.

![Note](images/NoteSmall.jpg)***NOTE***: *`start.sh` / `start.bat` scripts lets you start one Jet member per
folder. To start a new instance, please unzip the package in a new folder.*

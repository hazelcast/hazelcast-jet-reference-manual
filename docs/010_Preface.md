
## Naming

- **Hazelcast Jet** or **Jet** both refer to the same distributed data
processing engine provided by Hazelcast, Inc.
- **Hazelcast** or **Hazelcast IMDG** both refer to Hazelcast in-memory
data grid middleware. **Hazelcast** is also the name of the company
(Hazelcast, Inc.) providing Hazelcast IMDG and Hazelcast Jet.

## Licensing

Hazelcast Jet and Hazelcast Jet Reference Manual are free and provided
under the Apache License, Version 2.0.

## Trademarks

Hazelcast is a registered trademark of Hazelcast, Inc. All other
trademarks in this manual are held by their respective owners.

## Phone Home

Hazelcast uses phone home data to learn about usage of Hazelcast Jet.

Hazelcast Jet instances call our phone home server initially when they 
are started and then every 24 hours. This applies to all the instances 
joined to the cluster.

**What is sent in?**

The following information is sent in a phone home:

- Hazelcast Jet version
- Local Hazelcast Jet member UUID
- Download ID 
- A hash value of the cluster ID
- Cluster size bands for 5, 10, 20, 40, 60, 100, 150, 300, 600 and > 600
- Number of connected clients bands of 5, 10, 20, 40, 60, 100, 150, 300, 600 and > 600
- Cluster uptime
- Member uptime
- Environment Information:
	- Name of operating system
	- Kernel architecture (32-bit or 64-bit)
	- Version of operating system
	- Version of installed Java
	- Name of Java Virtual Machine
- Hazelcast IMDG Enterprise specific: 
	- Number of clients by language (Java, C++, C#)
	- Flag for Hazelcast Enterprise 
	- Hash value of license key
	- Native memory usage

**Phone Home Code**

The phone home code itself is open source. Please see <a 
href="https://github.com/hazelcast/hazelcast/blob/master/hazelcast/src/main/java/com/hazelcast/util/PhoneHome.java" 
target="_blank">here</a>.

**Disabling Phone Homes**

Set the `hazelcast.phone.home.enabled` system property to false either 
in the config or on the Java command line.

Starting with Hazelcast Jet 0.5, you can also disable the phone home 
using the environment variable `HZ_PHONE_HOME_ENABLED`. Simply add the 
following line to your `.bash_profile`:

```
export HZ_PHONE_HOME_ENABLED=false
```

**Phone Home URLs**

For versions 1.x and 2.x: <a href="http://www.hazelcast.com/version.jsp" 
target="_blank">http://www.hazelcast.com/version.jsp</a>.

For versions 3.x up to 3.6: <a 
href="http://versioncheck.hazelcast.com/version.jsp" target="_blank">
http://versioncheck.hazelcast.com/version.jsp</a>.

For versions after 3.6: <a href="http://phonehome.hazelcast.com/ping" 
target="_blank">http://phonehome.hazelcast.com/ping</a>.


## Getting Help

Support for Hazelcast Jet is provided via 
[GitHub](https://github.com/hazelcast/hazelcast-jet), [Hazelcast Jet 
mailing list ](https://groups.google.com/forum/#!forum/hazelcast-jet) 
and [Stack Overflow](https://stackoverflow.com/questions/tagged/hazelcast-jet).

For information on the commercial support for Hazelcast Jet, please see
[hazelcast.com](https://hazelcast.com/pricing/).

## Typographical Conventions

The below table shows the conventions used in this manual.

|Convention|Description|
|:-|:-|
|**bold font**| - Indicates part of a sentence that requires the reader's specific attention. <br> - Also indicates property/parameter values.|
|*italic font*|- When italicized words are enclosed with "<" and ">", it indicates a variable in the command or code syntax that you must replace (for example, `hazelcast-<`*version*`>.jar`). <br> - Note and Related Information texts are in italics.|
|`monospace`|Indicates files, folders, class and library names, code snippets, and inline code words in a sentence.|
|***RELATED INFORMATION***|Indicates a resource that is relevant to the topic, usually with a link or cross-reference.|
|![image](images/NoteSmall.jpg) ***NOTE***| Indicates information that is of special interest or importance, for example an additional action required only in certain circumstances.|
|element & attribute|Mostly used in the context of declarative configuration that you perform using an XML file. Element refers to an XML tag used to configure a feature. Attribute is a parameter owned by an element, contributing into the declaration of that element's configuration. Please see the following example.<br></br>`<property name="property1">value1</property>`<br></br> In this example, `name` is an **attribute** of the `property` **element**.


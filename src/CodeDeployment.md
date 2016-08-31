# Table of Contents

* [Code Deployment](#code-deployment)
* [Configuring Resources](#configuring-resources)
  * [Example Code for Configuration](#example-code-for-configuration)
* [Retrieving Deployed Resources on the Processor](#retrieving-deployed-resources-on-the-processor)
  * [Example Code for Resource Access](#example-code-for-resource-access) 

# Code Deployment

Code deployment feature enables you to distribute various resources to be used in your processors.
Those resources can be a class file, JAR file or any type of file. By deploying your classes or JAR files to the cluster, you do not need to worry about restarting the cluster to update the business logic anymore. The only thing you need to do is configuring the required class or JAR files for your Job. Once you configure them, Hazelcast JET will handle the distribution of your classes to all the nodes and loading of them when required. Hazelcast JET also provides isolation of the resources in the Job scope.

*NOTE: Deployed classes will be available only to JET Processors' class loader. If you would like to use Hazelcast data structures as sources or sinks, the types you want to store in Hazelcast data structures must be present on the Hazelcast classpath.*

# Configuring Resources

You can use any of the following methods on your `JobConfig` object to specify the deployment. The specified deployment with the resource will be uploaded to all the worker nodes when the Job starts its execution.

If you do not provide any deployment directory, a temporary folder will be created and used as a deployment storage on the Jet nodes.

```java
/**
 * Sets the name of the directory to be used for deployment.
 *
 * @param deploymentDirectory name of the directory.
 * @return the current job configuration.
 */
 public JobConfig setDeploymentDirectory(String deploymentDirectory)

/**
  * Add class to the job class loader.
  *
  * @param classes classes, which will be used during calculation.
  */
 public void addClass(Class... classes)

 /**
  * Add JAR to the job class loader.
  *
  * @param url location of the JAR file.
  */
 public void addJar(URL url)

 /**
  * Add JAR to the job class loader.
  *
  * @param url location of the JAR file.
  * @param id  identifier for the JAR file.
  */
 public void addJar(URL url, String id)

 /**
  * Add JAR to the job class loader.
  *
  * @param file the JAR file.
  */
 public void addJar(File file)

 /**
  * Add JAR to the job class loader.
  *
  * @param file the JAR file.
  * @param id   identifier for the JAR file.
  */
 public void addJar(File file, String id)

 /**
  * Add JAR to the job class loader.
  *
  * @param path path the JAR file.
  */
 public void addJar(String path)

 /**
  * Add JAR to the job class loader.
  *
  * @param path path the JAR file.
  * @param id   identifier for the JAR file.
  */
 public void addJar(String path, String id)

 /**
  * Add resource to the job class loader.
  *
  * @param url source url with classes.
  */
 public void addResource(URL url)

 /**
  * Add resource to the job class loader.
  *
  * @param url source url with classes.
  * @param id  identifier for the resource.
  */
 public void addResource(URL url, String id)

 /**
  * Add resource to the job class loader.
  *
  * @param file resource file.
  */
 public void addResource(File file)

 /**
  * Add resource to the job class loader.
  *
  * @param file resource file.
  * @param id   identifier for the resource.
  */
 public void addResource(File file, String id)

 /**
  * Add resource to the job class loader.
  *
  * @param path path of the resource.
  */
 public void addResource(String path)

 /**
  * Add resource to the job class loader.
  *
  * @param path path of the resource.
  * @param id   identifier for the resource.
  */
 public void addResource(String path, String id)
 ```

## Example Code for Configuration

```java
JobConfig config = new JobConfig();
String jarPath = "/path/to/jar/file.jar";
config.addJar(path);

Job job = JetEngine.getJob(hazelcastInstance, name, dag, config);
job.execute(); // The JAR file will be uploaded to the all nodes
// All the classes it contains will be loaded by the worker class loader
```

# Retrieving Deployed Resources on the Processor

All deployments will be available as a resource to the `Processor` class loader. To access to these resources inside a processor, you need to get a reference to the class loader, and then use `getResource(String name)` or `getResourceAsStream(String name)` methods. You can access to these resources via their file names or with the identifiers (if assigned any while configuring the deployment).


## Example Code for Resource Access

```java
@Override
public boolean process(ProducerInputStream inputStream,
                       ConsumerOutputStream outputStream,
                       String sourceName, ProcessorContext processorContext) throws Exception {

    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    URL url = contextClassLoader.getResource("lookupTable");
    // URL points to the deployed `lookupTable` resource.
    ....
    return true;
}
```

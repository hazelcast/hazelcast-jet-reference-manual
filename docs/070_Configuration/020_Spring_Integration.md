You can integrate Hazelcast Jet with Spring and this chapter explains 
the configuration of Hazelcast Jet within Spring context.
 

## Declaring Beans by Spring beans Namespace

You can declare Hazelcast Jet Objects using the default Spring beans namespace.
Example code for a Hazelcast Jet Instance declaration is listed below.

```xml

<bean id="instance" class="com.hazelcast.jet.Jet" factory-method="newJetInstance">
    <constructor-arg>
        <bean class="com.hazelcast.jet.config.JetConfig">
            <property name="hazelcastConfig">
                <bean class="com.hazelcast.config.Config">
                    <!-- ... -->
                </bean>
            </property>
            <property name="instanceConfig">
                <bean class="com.hazelcast.jet.config.InstanceConfig">
                    <property name="cooperativeThreadCount" value="2"/>
                </bean>
            </property>
            <property name="defaultEdgeConfig">
                <bean class="com.hazelcast.jet.config.EdgeConfig">
                    <property name="queueSize" value="2048"/>
                </bean>
            </property>
            <property name="properties">
                <props>
                    <prop key="foo">bar</prop>
                </props>
            </property>
        </bean>
    </constructor-arg>
</bean>

<bean id="map" factory-bean="instance" factory-method="getMap">
    <constructor-arg value="my-map"/>
</bean>
```

## Declaring Beans by _jet_ Namespace

Hazelcast Jet has its own namespace ***jet*** for bean definitions. You can easily 
add the namespace declaration `xmlns:jet=“http://www.hazelcast.com/schema/jet-spring”`
to the beans element in the context file so that ***jet*** namespace shortcut can 
be used as a bean declaration. Remember to add `hazelcast-jet-spring.jar` to classpath.

Here is an example schema definition:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:jet="http://www.hazelcast.com/schema/jet-spring"
       xmlns:hz="http://www.hazelcast.com/schema/spring"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
        http://www.hazelcast.com/schema/spring
        http://www.hazelcast.com/schema/spring/hazelcast-spring-3.10.xsd
        http://www.hazelcast.com/schema/jet-spring
        http://www.hazelcast.com/schema/jet-spring/hazelcast-jet-spring-0.6.xsd">
        <!-- ... -->
 </beans>
```
#### Configuring Hazelcast Jet Instance 

```xml
<jet:instance id="instance">
    <hz:config>
        <hz:spring-aware/>
        <hz:group name="jet"/>
        <hz:network port="5701" port-auto-increment="false">
            <hz:join>
                <hz:multicast enabled="false"/>
                <hz:tcp-ip enabled="true">
                    <hz:member>127.0.0.1:5701</hz:member>
                </hz:tcp-ip>
            </hz:join>
        </hz:network>
        <hz:map name="map" backup-count="3">
        </hz:map>
    </hz:config>
    <jet:instance-config cooperative-thread-Count="2"/>
    <jet:default-edge-config queue-size="2048"/>
    <jet:properties>
        <hz:property name="foo">bar</hz:property>
    </jet:properties>
</jet:instance>
```

#### Configuring Hazelcsat Jet Client

```xml
<jet:client id="jet-client">
    <jet:group name="jet"/>
    <jet:network>
        <hz:member>127.0.0.1:5701</hz:member>
    </jet:network>
    <jet:spring-aware/>
</jet:client>
```

#### Hazelcast Jet Supported Type Configurations and Examples

 - map
 - list
 
 ```xml
<jet:map instance-ref="jet-instance" name="my-map" id="my-map-bean"/>

<jet:list instance-ref="jet-client" name="my-list" id="my-list-bean"/>
```

You can obtain underlying `HazelcastInstance` as a bean and use this bean to
obtain other data types which Hazelcast supports.

 - multiMap
 - replicatedmap
 - queue
 - topic
 - set
 - executorService
 - idGenerator
 - atomicLong
 - atomicReference
 - semaphore
 - countDownLatch
 - lock
 
 ```xml
<jet:hazelcast jet-instance-ref="jet-instance" id="hazelcast-instance"/>
 
<hz:multiMap id="multiMap" instance-ref="hazelcast-instance" name="my-multiMap"/>

<hz:replicatedMap id="replicatedMap" instance-ref="hazelcast-instance" name="my-replicatedMap"/>

<hz:queue id="queue" instance-ref="hazelcast-instance" name="my-queue"/>

<hz:topic id="topic" instance-ref="hazelcast-instance" name="my-topic"/>

<hz:set id="set" instance-ref="hazelcast-instance" name="my-set"/>

<hz:executorService id="executorService" instance-ref="hazelcast-instance" name="my-executorService"/>

<hz:idGenerator id="idGenerator" instance-ref="hazelcast-instance" name="my-idGenerator"/>

<hz:atomicLong id="atomicLong" instance-ref="hazelcast-instance" name="my-atomicLong"/>

<hz:atomicReference id="atomicReference" instance-ref="hazelcast-instance" name="my-atomicReference"/>

<hz:semaphore id="semaphore" instance-ref="hazelcast-instance" name="my-semaphore"/>

<hz:countDownLatch id="countDownLatch" instance-ref="hazelcast-instance" name="my-countDownLatch"/>

<hz:lock id="lock" instance-ref="hazelcast-instance" name="my-lock"/>
```

Hazelcast Jet also supports *lazy-init*, *scope* and *depends-on* bean attributes.

```xml
<jet:instance id="instance" lazy-init="true" scope="singleton">
<!-- ... -->
</jet:instance>
<jet:client id="client" scope="prototype" depends-on="instance">
<!-- ... -->
</jet:client>
```

## Annotation-Based Configuration 

Annotation-Based Configuration does not require any XML definition. Simply create 
a class with related annotations, eg `@Configuration`. And provide JetInstance as a
bean by annotating the method with `@Bean`. 

```java
@Configuration
public class AppConfig {
    
    @Bean
    public JetInstance instance() {
        return Jet.newJetInstance();
    }
}
``` 


## Enabling SpringAware Objects

You need to configure Hazelcast Jet with `<hz:spring-aware/>` tag or set 
`SpringManagedContext` programmatically to enable spring-aware objects.
Code samples for 
[declarative](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/spring/src/main/java/jet/spring/XmlConfigurationWithSchemaSample.java)
and  
[annotation-based](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/spring/src/main/java/jet/spring/AnnotationBasedConfigurationSample.java)
configurations are available at our Code Samples repo.

You can mark your custom processors with `@SpringAware` annotation which 
gives you the ability:

 - to apply bean properties
 - to apply factory callbacks such as `ApplicationContextAware`, `BeanNameAware`
 - to apply bean post-processing annotations such as `InitializingBean`, `@PostConstruct`
 
 Refer to 
 [code samples](https://github.com/hazelcast/hazelcast-jet-code-samples/blob/master/spring/src/main/java/jet/spring/source/CustomSourceP.java)
 for more information
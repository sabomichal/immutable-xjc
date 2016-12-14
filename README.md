[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.sabomichal/immutable-xjc-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.sabomichal/immutable-xjc-plugin)
## immutable-xjc
IMMUTABLE-XJC is a JAXB 2.0 XJC plugin for making schema derived classes immutable:

* removes all setter methods
* marks class final
* creates a public constructor with all fields as parameters
* creates a protected no-arg constructor
* marks all fields within a class as final
* wraps all collection like parameters with Collections.unmodifiable or Collections.empty if null
* optionally creates builder pattern utility classes

Note: Derived classes can be further made serializable using these xjc [customizations](http://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.6/jaxb/vendorCustomizations.html#serializable).

### Release notes
#### 1.3
* builder class copy constructor added

#### 1.2
* builder class now contains initialised collection fields
* added generated 'add' methods to incrementally build up the builder collection fields

#### 1.1.1
* various abstract class compile problems fixed
* same class name builder compile problem fixed

#### 1.1
* complex xsd scenarios fixed
* boolean type default values fixed

#### 1.0.5
* xsd polymorphism compilation problems fixed

### XJC options provided by the plugin
The plugin provides an '-immutable' option which is enabled by adding its jar file to the XJC classpath. When enabled,  one additional option can be used to control the behavior of the plugin. See the examples for further information.

#### -immutable
The '-immutable' option enables the plugin making the XJC generated classes immutable.

#### -imm-builder
The '-imm-builder' option can be used to generate builder like pattern utils for each schema derived class.

#### -imm-cc
The '-imm-cc' option can only be used together with '-imm-builder' option and it is used to generate builder class copy construstructor, initialising builder with object of given class.

### Usage
#### JAXB-RI CLI
To use the JAXB-RI XJC command line interface simply add the corresponding java archives to the classpath and execute the XJC main class 'com.sun.tools.xjc.Driver'. The following example demonstrates a working command line for use with JDK 1.5 (assuming the needed dependencies are found in the current working directory).
```bash
java -cp activation-1.1.jar:\
           jaxb-api-2.0.jar:\
           stax-api-1.0.jar:\
           jaxb-impl-2.0.5.jar:\
           jaxb-xjc-2.0.5.jar:\
           immutable-xjc-plugin-1.3.1.jar\
           com.sun.tools.xjc.Driver -d /tmp/src -immutable <schema files>
```
#### Maven
Maven users simply add the IMMUTABLE-XJC plugin as a dependency to a JAXB plugin of choice. The following example demonstrates the use of the IMMUTABLE-XJC plugin with the mojo *jaxb2-maven-plugin*.
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>jaxb2-maven-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>1.3.1</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>xjc</goal>
            </goals>
            <configuration>
                <arguments>-immutable -imm-builder</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

IMMUTABLE-XJC can be used also in contract-first webservice client scenarios with wsimport. The following example demonstrates the usage of the plugin with *jaxws-maven-plugin* mojo.
```xml
<plugin>
    <groupId>org.jvnet.jax-ws-commons</groupId>
    <artifactId>jaxws-maven-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>1.3.1</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>wsimport</goal>
            </goals>
            <configuration>
                <wsdlFiles>
                    <wsdlFile>test.wsdl</wsdlFile>
                </wsdlFiles>
                <args>
                    <arg>-B-immutable -B-imm-builder</arg>
                </args>
            </configuration>
        </execution>
    </executions>
</plugin>
```
Next example demonstrates the usage of the plugin with CXF *cxf-codegen-plugin* mojo.
```xml
<plugin>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-codegen-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>1.3.1</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>wsdl2java</goal>
            </goals>
            <configuration>
                <wsdlOptions>
                    <wsdlOption>
                        <wsdl>${basedir}/wsdl/test.wsdl</wsdl>
                        <extraargs>
                            <extraarg>-xjc-immutable</extraarg>
                            <extraarg>-xjc-imm-builder</extraarg>
                        </extraargs>
                    </wsdlOption>
                </wsdlOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```
If you like it, give it a star, if you don't, write an issue.

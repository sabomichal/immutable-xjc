## immutable-xjc

IMMUTABLE-XJC is a JAXB 2.0 XJC plugin for making schema derived classes immutable. The plugin provides a '-immutable' option which is enabled by adding its jar file to the XJC classpath. When enabled, the following options can be used to control the behavior of the plugin. See the examples for further information. Derived classes can be further made serializable using these xjc [customizations](http://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.6/jaxb/vendorCustomizations.html#serializable)

#### -imm-builder
The '-imm-builder' option can be used to generate builder like pattern utils for each schema derived class.

## Usage
#### JAXB-RI CLI
To use the JAXB-RI XJC command line interface simply add the corresponding java archives to the classpath and execute the XJC main class 'com.sun.tools.xjc.Driver'. The following example demonstrates a working command line for use with JDK 1.5 (assuming the needed dependencies are found in the current working directory).
```bash
java -cp activation-1.1.jar:\
           jaxb-api-2.0.jar:\
           stax-api-1.0.jar:\
           jaxb-impl-2.0.5.jar:\
           jaxb-xjc-2.0.5.jar:\
           immutable-xjc-plugin-1.0.1.jar\
           com.sun.tools.xjc.Driver -d /tmp/src -immutable <schema files>
```
#### Maven
Maven users simply add the IMMUTABLE-XJC plugin as a dependency to a JAXB plugin of choice. The following example demonstrates the use of the IMMUTABLE-XJC plugin with the Mojo jaxb2-maven-plugin.
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>jaxb2-maven-plugin</artifactId>
    <version>1.5</version>
    <dependencies>
        <dependency>
            <groupId>eu.mtrust</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>1.0.1</version>
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

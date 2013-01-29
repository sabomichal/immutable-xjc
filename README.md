## immutable-xjc

CC-XJC is a JAXB 2.0 XJC plugin for adding a copy constructor to schema derived classes. The plugin provides a '-copy-constructor' option which is enabled by adding its jar file to the XJC classpath. When enabled, the following options can be used to control the behavior of the plugin. See the examples for further information.

-imm-builder
The '-imm-builder' option can be used to specify the visibility of generated copy methods. It takes one argument from the list [private, package, protected, public]. This option impacts the number of generated methods. Default: private.

## Usage
#### JAXB-RI CLI
To use the JAXB-RI XJC command line interface simply add the corresponding java archives to the classpath and execute the XJC main class 'com.sun.tools.xjc.Driver'. The following example demonstrates a working command line for use with JDK 1.5 (assuming the needed dependencies are found in the current working directory).

  java -cp activation-1.1.jar:\
           jaxb-api-2.0.jar:\
           stax-api-1.0.jar:\
           jaxb-impl-2.0.5.jar:\
           jaxb-xjc-2.0.5.jar:\
           immutable-xjc-plugin-1.0.1.jar\
           com.sun.tools.xjc.Driver -d /tmp/src -immutable <schema files>
           
#### Maven
Maven users simply add the CC-XJC plugin as a dependency to a JAXB plugin of choice. The following example demonstrates the use of the CC-XJC plugin with the Mojo jaxb2-maven-plugin.

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

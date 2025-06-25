[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.sabomichal/immutable-xjc-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.sabomichal/immutable-xjc-plugin) ![Java CI with Maven](https://github.com/sabomichal/immutable-xjc/workflows/Java%20CI%20with%20Maven/badge.svg)
## immutable-xjc
IMMUTABLE-XJC is a JAXB 4.0 XJC plugin for making schema derived classes immutable:

* removes all setter methods
* marks class final (can be disabled with the '-Ximm-nofinalclasses' option)
* creates a public constructor with all fields as parameters
* creates a protected no-arg constructor
* marks all fields within a class as final
* wraps all collection like parameters with Collections.unmodifiable or Collections.empty if null (unless -imm-leavecollections option is used)
* optionally creates builder pattern utility classes

Note: Derived classes can be further made serializable using these xjc [customizations](http://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.6/jaxb/vendorCustomizations.html#serializable).

### JAXB version
Current plugin version is JAXB 4.0 compatible. For JAXB 2.x please use previous major version of the plugin (1.7.x). 

### Java version
Current target Java versions is Java 11

### XJC options provided by the plugin
The plugin provides an '-Ximm' option which is enabled by adding its jar file to the XJC classpath. When enabled, additional options can be used to control the behavior of the plugin. See the examples for further information.

#### -Ximm
The '-Ximm' option enables the plugin making the XJC generated classes immutable.

#### -Ximm-builder
The '-Ximm-builder' option can be used to generate builder like pattern utils for each schema derived class.

#### -Ximm-simplebuildername
The '-Ximm-simplebuildername' option can be used to generate builders which follow a simpler naming scheme, using Foo.builder() and Foo.Builder instead of Foo.fooBuilder() and Foo.FooBuilder.

#### -Ximm-inheritbuilder
The '-Ximm-inheritbuilder' option can be used to generate builder classes that follow the same inheritance hierarchy as their subject classes.

#### -Ximm-cc
The '-Ximm-cc' option can only be used together with '-Ximm-builder' option and it is used to generate builder class copy constructor, initialising builder with object of given class.

#### -Ximm-ifnotnull
The '-Ximm-ifnotnull' option can only be used together with '-Ximm-builder' option and it is used to add an additional withAIfNotNull(A a) method for all non-primitive fields A in the generated builders.

#### -Ximm-nopubconstructor
The '-Ximm-nopubconstructor' option is used to make the constructors of the generated classes non-public.

#### -Ximm-pubconstructormaxargs
The '-Ximm-pubconstructormaxargs=n' option is used to generate public constructors with up to n arguments, when -Ximm-builder is used 

#### -Ximm-skipcollections
The '-Ximm-skipcollections' option is used to leave collections mutable

#### -Ximm-constructordefaults
The '-Ximm-constructordefaults' option is used to set default values for xs:element's and xs:attribute's in no-argument constructor. Default values must be strings or numbers, otherwise ignored.

#### -Ximm-optionalgetter
The '-Ximm-optionalgetter' option is used to wrap the return value of getters for non-required (`@XmlAttribute|Element(required = false)`) values with `java.util.Optional<OriginalRetunType>`.

#### -Ximm-nofinalclasses
The '-Ximm-nofinalclasses' option is used to leave all classes non-final.

### Usage
#### JAXB-RI CLI
To use the JAXB-RI XJC command line interface simply add the corresponding java archives to the classpath and execute the XJC main class 'com.sun.tools.xjc.XJCFacade'. The following example demonstrates a working command line for use with JDK 11+ (assuming the needed dependencies are found in the current working directory).
```sh
java.exe -classpath codemodel-4.0.2.jar:\
                    jakarta.xml.bind-api-4.0.0:\
                    jaxb-runtime-4.0.2.jar:\                    
                    jaxb-xjc-4.0.2.jar:\
                    jakarta.activation-api-2.1.0.jar:\
                    immutable-xjc-plugin.jar:\
                    commons-lang3-3.12.0.jar:\
                    com.sun.tools.xjc.XJCFacade -Ximm <schema files>
```
#### Maven
Maven users simply add the IMMUTABLE-XJC plugin as a dependency to a JAXB plugin of choice. The following example demonstrates the use of the IMMUTABLE-XJC plugin with the mojo *https://github.com/evolvedbinary/jvnet-jaxb-maven-plugin*.
```xml
<plugin>
    <groupId>com.helger.maven</groupId>
    <artifactId>jaxb30-maven-plugin</artifactId>
    <version>0.16.1</version>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>${plugin.version}</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <specVersion>4.0.2</specVersion>
                <args>
                    <arg>-Ximm</arg>
                    <arg>-Ximm-builder</arg>
                </args>
            </configuration>
        </execution>
    </executions>
</plugin>
```

IMMUTABLE-XJC can be used also in contract-first webservice client scenarios with wsimport. The following example demonstrates the usage of the plugin with *jaxws-maven-plugin* mojo.
```xml
<plugin>
    <groupId>com.sun.xml.ws</groupId>
    <artifactId>jaxws-maven-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>${plugin.version}</version>
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
                    <wsdlFile>example.wsdl</wsdlFile>
                </wsdlFiles>
                <args>
                    <arg>-B-Ximm -B-Ximm-builder</arg>
                </args>
            </configuration>
        </execution>
    </executions>
</plugin>
```
Next two examples demonstrates the usage of the plugin with CXF *cxf-codegen-plugin* and *cxf-xjc-plugin* mojo.
```xml
<plugin>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-codegen-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>${plugin.version}</version>
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
                        <wsdl>${basedir}/wsdl/example.wsdl</wsdl>
                        <extraargs>
                            <extraarg>-xjc-Ximm</extraarg>
                            <extraarg>-xjc-Ximm-builder</extraarg>
                        </extraargs>
                    </wsdlOption>
                </wsdlOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```
```xml
<plugin>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-xjc-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.sabomichal</groupId>
            <artifactId>immutable-xjc-plugin</artifactId>
            <version>${plugin.version}</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>xsd2java</goal>
            </goals>
            <configuration>
                <xsdOptions>
                    <xsdOption>
                        <xsd>${basedir}/wsdl/example.xsd</xsd>
                        <extensionArgs>
                            <extensionArg>-Ximm</extensionArg>
                            <extensionArg>-Ximm-builder</extensionArg>
                        </extensionArgs>
                    </xsdOption>
                </xsdOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Gradle
The following example demonstrates the use of the IMMUTABLE-XJC plugin with the Gradle plugin [wsdl2java](https://github.com/nilsmagnus/wsdl2java).
```groovy
plugins {
    id "no.nils.wsdl2java" version "0.12"
}

dependencies {
    wsdl2java 'com.github.sabomichal:immutable-xjc-plugin:${plugin.version}'
}

wsdl2java {
    wsdlsToGenerate = [
            ['-xjc-Ximm', '-xjc-Ximm-builder', 'src/main/resources/wsdl/example.wsdl']
        ]
    wsdlDir = file("$projectDir/src/main/resources/wsdl")
    cxfVersion = "4.0.2"
    cxfPluginVersion = "4.0.2"
}
```

### Release notes
#### 2.0
* migrated to JAXB 4.0 (Jakarta namespace mostly)
* dropped support for java 8 (11 is the target version)
* added a check for non-null required fields when using a builder

#### 1.7
* added an option to leave all classes non-final

#### 1.6
* added an option to set default values in no-arg constructors
* added an option to generate builder classes that follow the same inheritance hierarchy as their subject classes
* added an option to generate simple builder names
* dropped support for java 6

#### 1.5
* added an option to leave collections mutable
* added an option to generate public constructors only up to n arguments when builder is used

#### 1.4
* added an option to generate non-public constructors
* added an option to generate additional *withAIfNotNull(A a)* builder methods 

#### 1.3
* builder class copy constructor added

#### 1.2
* builder class now contains initialised collection fields
* added generated 'add' methods to incrementally build up the builder collection fields

Initial idea by Milos Kolencik. If you like it, give it a star, if you don't, write an issue.

<div align="center">

# immutable-xjc

**Turn your XSD into immutable Java classes, builders included.**

A JAXB 4.0 XJC plugin that makes schema-derived classes immutable, with an optional builder pattern generator.

[![Maven Central](https://img.shields.io/maven-central/v/com.github.sabomichal/immutable-xjc-plugin)](https://central.sonatype.com/artifact/com.github.sabomichal/immutable-xjc-plugin)
[![Java CI with Maven](https://github.com/sabomichal/immutable-xjc/actions/workflows/maven.yml/badge.svg)](https://github.com/sabomichal/immutable-xjc/actions/workflows/maven.yml)
![Java 11+](https://img.shields.io/badge/Java-11%2B-blue)
![JAXB 4.0](https://img.shields.io/badge/JAXB-4.0-blue)
[![License: MIT](https://img.shields.io/badge/License-MIT-green)](LICENSE.txt)

</div>

<p align="center">
  <a href="#-quick-start">Quick start</a> ·
  <a href="#%EF%B8%8F-xjc-options">Options</a> ·
  <a href="#-more-integrations">Integrations</a> ·
  <a href="https://github.com/sabomichal/immutable-xjc/releases">Releases</a>
</p>

---

Out of the box, XJC generates mutable JavaBeans: setters on every field, collections anyone can modify, and objects that can change under your feet. **immutable-xjc** takes the same schema and generates real value objects instead. You add one flag to the build you already have.

```java
// 😬 plain XJC: anyone can change anything, at any time
Metadata metadata = new Metadata();
metadata.setAuthor("Ada");
metadata.getTags().add("draft");

// 😌 with -Ximm -Ximm-builder: built once, never changed
Metadata metadata = Metadata.metadataBuilder()
        .withAuthor("Ada")
        .addTag("draft")
        .build();

metadata.getTags().add("final"); // 💥 UnsupportedOperationException
```

## 💡 Why immutable-xjc?

- 🧵 **Thread-safe by construction.** Immutable objects can be shared between threads, cached and passed around without synchronization or defensive copies.
- 🛡️ **No leaking collections.** Lists are wrapped as unmodifiable, and a missing list becomes an empty one instead of `null`. Nobody can slip an element in behind your back.
- 🏗️ **Fluent builders for free.** Readable construction with `with…`/`add…` methods. `build()` throws an exception if a required field is not set, so the object can't end up half-built.
- 🎁 **Optional instead of null.** With one more flag, getters of optional schema values return `java.util.Optional`.
- 🪶 **No runtime dependency.** The plugin only runs at build time. The generated code uses nothing but the JDK and the JAXB API.
- 🔌 **Drop-in.** Works with the JAXB, JAX-WS, Apache CXF and Gradle tooling you already use, with no changes to your schema. Marshalling and unmarshalling work as before.

## 🚀 Quick start

Add IMMUTABLE-XJC as a dependency of the JAXB Maven plugin of your choice and turn it on with `-Ximm`. This example uses the mojo [jvnet-jaxb-maven-plugin](https://github.com/evolvedbinary/jvnet-jaxb-maven-plugin):

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

Run your build. Your schema classes are now immutable. 🎉 Using JAX-WS, CXF, Gradle or the command line? See [more integrations](#-more-integrations).

## 🔒 What the plugin does

- removes all setter methods
- marks classes `final` (can be disabled with the `-Ximm-nofinalclasses` option)
- creates a public constructor with all fields as parameters
- creates a protected no-arg constructor
- marks all fields within a class as `final`
- wraps all collection-like parameters with `Collections.unmodifiable*`, or `Collections.empty*` if null (unless the `-Ximm-skipcollections` option is used)
- optionally creates builder pattern utility classes

> [!TIP]
> Derived classes can also be made serializable using these XJC [customizations](http://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.6/jaxb/vendorCustomizations.html#serializable).

## 🧩 Compatibility

| | |
|---|---|
| **JAXB** | 4.0. For JAXB 2.x, use the previous major version of the plugin (1.7.x). |
| **Java** | 11 is the current target version. |

## ⚙️ XJC options

The plugin is enabled with the `-Ximm` option once its jar is on the XJC classpath. The other options fine-tune what gets generated. See the [quick start](#-quick-start) and [more integrations](#-more-integrations) for how to pass them.

| Option | What it does |
|---|---|
| `-Ximm` | Enables the plugin, making the XJC-generated classes immutable. |
| `-Ximm-builder` | Generates builder-like pattern utils for each schema-derived class. |
| `-Ximm-simplebuildername` | Uses a simpler builder naming scheme: `Foo.builder()` and `Foo.Builder` instead of `Foo.fooBuilder()` and `Foo.FooBuilder`. |
| `-Ximm-inheritbuilder` | Generates builder classes that follow the same inheritance hierarchy as their subject classes. |
| `-Ximm-cc` | Generates a builder copy constructor that initialises the builder with an object of the given class. *Requires `-Ximm-builder`.* |
| `-Ximm-ifnotnull` | Adds an extra `withAIfNotNull(A a)` method to the generated builders for every non-primitive field `A`. *Requires `-Ximm-builder`.* |
| `-Ximm-nopubconstructor` | Makes the constructors of the generated classes non-public. |
| `-Ximm-pubconstructormaxargs=n` | Generates public constructors with up to `n` arguments when `-Ximm-builder` is used. |
| `-Ximm-skipcollections` | Leaves collections mutable. |
| `-Ximm-constructordefaults` | Sets default values for `xs:element`s and `xs:attribute`s in the no-argument constructor. Default values must be strings or numbers, anything else is ignored. |
| `-Ximm-optionalgetter` | Wraps getter return values for non-required (`@XmlAttribute\|Element(required = false)`) values in `java.util.Optional<OriginalReturnType>`. |
| `-Ximm-nofinalclasses` | Leaves all classes non-final. |

## 🔌 More integrations

### Web services (contract-first)

IMMUTABLE-XJC also works in contract-first web service client scenarios. Pick your tool:

<details>
<summary><b>JAX-WS</b>: <code>jaxws-maven-plugin</code> (wsimport)</summary>

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
</details>

<details>
<summary><b>Apache CXF</b>: <code>cxf-codegen-plugin</code> (wsdl2java)</summary>

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
</details>

<details>
<summary><b>Apache CXF</b>: <code>cxf-xjc-plugin</code> (xsd2java)</summary>

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
</details>

### Gradle

<details>
<summary>With the <a href="https://github.com/nilsmagnus/wsdl2java">wsdl2java</a> Gradle plugin</summary>

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
</details>

### Command line (JAXB-RI XJC)

<details>
<summary>Running <code>com.sun.tools.xjc.XJCFacade</code> directly</summary>

Add the corresponding Java archives to the classpath and run the XJC main class `com.sun.tools.xjc.XJCFacade`. This works with JDK 11+, assuming the needed dependencies are in the current working directory:

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
</details>

## 📦 Release notes

See [GitHub releases](https://github.com/sabomichal/immutable-xjc/releases).

---

<div align="center">

Initial idea by Milos Kolencik.<br>
If you like it, give it a ⭐. If you don't, [write an issue](https://github.com/sabomichal/immutable-xjc/issues).

</div>

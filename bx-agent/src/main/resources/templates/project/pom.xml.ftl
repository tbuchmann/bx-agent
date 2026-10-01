<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>${projectName}</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- BX Runtime (install via: mvn install in bxagent root) -->
        <dependency>
            <groupId>dev.bxagent</groupId>
            <artifactId>bx-runtime</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>

        <!-- EMF -->
        <dependency>
            <groupId>org.eclipse.emf</groupId>
            <artifactId>org.eclipse.emf.ecore</artifactId>
            <version>2.29.0</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.emf</groupId>
            <artifactId>org.eclipse.emf.ecore.xmi</artifactId>
            <version>2.17.0</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.emf</groupId>
            <artifactId>org.eclipse.emf.common</artifactId>
            <version>2.27.0</version>
        </dependency>

        <!-- Guava (BiMap for correspondence model) -->
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>33.4.0-jre</version>
        </dependency>

        <!-- Source metamodel: ${sourcePackageName} -->
<#if sourceMetamodelDep??>
        <dependency>
            <groupId>${sourceMetamodelDep.groupId()}</groupId>
            <artifactId>${sourceMetamodelDep.artifactId()}</artifactId>
            <version>${sourceMetamodelDep.version()}</version>
        </dependency>
<#else>
        <!-- TODO: install source metamodel JAR to local repo and add dependency here -->
        <!--
        <dependency>
            <groupId>TODO</groupId>
            <artifactId>${sourcePackageName}</artifactId>
            <version>1.0.0</version>
        </dependency>
        -->
</#if>

        <!-- Target metamodel: ${targetPackageName} -->
<#if targetMetamodelDep??>
        <dependency>
            <groupId>${targetMetamodelDep.groupId()}</groupId>
            <artifactId>${targetMetamodelDep.artifactId()}</artifactId>
            <version>${targetMetamodelDep.version()}</version>
        </dependency>
<#else>
        <!-- TODO: install target metamodel JAR to local repo and add dependency here -->
        <!--
        <dependency>
            <groupId>TODO</groupId>
            <artifactId>${targetPackageName}</artifactId>
            <version>1.0.0</version>
        </dependency>
        -->
</#if>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>6.0.3</version>
            <scope>test</scope>
        </dependency>
<#if benchmarxDep??>

        <!-- BenchmarX framework -->
        <dependency>
            <groupId>${benchmarxDep.groupId()}</groupId>
            <artifactId>${benchmarxDep.artifactId()}</artifactId>
            <version>${benchmarxDep.version()}</version>
            <scope>test</scope>
        </dependency>
</#if>
    </dependencies>

    <build>
        <!-- Eclipse-style flat source layout -->
        <sourceDirectory>src</sourceDirectory>
        <testSourceDirectory>src-test</testSourceDirectory>

        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.2</version>
            </plugin>
        </plugins>
    </build>
</project>

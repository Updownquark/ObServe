/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java library project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/7.4.2/userguide/building_java_projects.html
 */

import org.gradle.jvm.tasks.Jar

plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    antlr
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

group = "ObServe"
version = "2.0.0"
description = "A library of observables and many utilities for them"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
	api(project("../Qommons"))
    api("com.google.guava:guava:32.0.0-jre")
    antlr("org.antlr:antlr4:4.9.2")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
	implementation("com.miglayout:miglayout-swing:4.2")
	implementation("org.swinglabs:swingx:1.6.1")

    // Use JUnit test framework.
    testImplementation("junit:junit:4.13.2")
}

tasks {
    generateGrammarSource {
        val pkg = "org.observe.expresso"
        arguments = arguments + listOf("-package", pkg)
        outputDirectory = outputDirectory.resolve(pkg.split(".").joinToString("/"))
    }
}

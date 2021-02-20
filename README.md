[![Build Status](https://travis-ci.org/openrndr/openrndr.svg?branch=master)](https://travis-ci.org/openrndr/openrndr)
[![Download](https://api.bintray.com/packages/openrndr/openrndr/openrndr/images/download.svg) ](https://bintray.com/openrndr/openrndr/openrndr/_latestVersion)
[![Awesome Kotlin Badge](https://kotlin.link/awesome-kotlin.svg)](https://github.com/KotlinBy/awesome-kotlin)
[<img src="https://img.shields.io/badge/slack-@openrndr-yellow.svg?logo=slack">](https://openrndr.slack.com/) 

# OPENRNDR

A Kotlin/JVM library for creative coding, real-time and interactive graphics. Can currently be used on Windows, macOS and Linux/x64 to create standalone graphical applications.

Basics and use are further explained in the [OPENRNDR guide](https://guide.openrndr.org).

## Community

Visit the [OPENRNDR website](https://openrndr.org) for the latest news on OPENRNDR, showcases and events 

Join us on the [OPENRNDR forum](https://openrndr.discourse.group) for questions, tutorials and showcases.

.. or if you prefer a more direct and chatty way of conversation talk to us the [OPENRNDR Slack](https://communityinviter.com/apps/openrndr/openrndr)

## Using OPENRNDR

You are advised to use the [OPENRNDR template](https://github.com/openrndr/openrndr-template) which provides a quick start to using the library.

## Building OPENRNDR

On a system that has JDK 1.8.x or more recent installed one can run the following commands from a terminal:

```sh
cd <path-to-checkout>
./gradlew build
```

This should start the build process, which will take some time to complete.

Note that OPENRNDR does not depend on anything that is not on Maven Central, builds should be easy and predictable.

## Installing OPENRNDR as Maven artifacts

In order to use the OPENRNDR build from your applications one has to install OPENRNDR's Maven artifacts in the local Maven repository.

```sh
./gradlew -Prelease.version=0.4.0-SNAPSHOT publishToMavenLocal
```

## Building OPENRNDR from IntelliJ

This should be as easy as importing the Gradle project into IntelliJ.

On a macOS or linux system that has IntelliJ's command line tools installed one can run

```sh
cd <path-to-checkout>
idea .
```

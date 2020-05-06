# Java Project for Hashcash Edge callout

This directory contains the Java source code and pom.xml file required to
compile a simple custom policy for Apigee Edge. The policy performs a
[HashCash](http://www.hashcash.org/) verification.

## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.


## Using this policy

You do not need to build the source code in order to use the policy in Apigee Edge.
All you need is the pre-built JAR, and the appropriate configuration for the policy.
If you want to build it, feel free. Follow the instructions lower in this README.

## Building

Building from source requires Java 1.8, and Maven.

1. unpack (if you can read this, you've already done that).

2. Before building _the first time_, configure the build on your machine by loading the Apigee jars into your local cache:
  ```
  ./buildsetup.sh
  ```

3. Build with maven.
  ```
  cd project
  mvn clean package
  ```
  
  This will build the jars and also run all the tests. It will also install the
  JARs to the directory for the example api proxy.

## Project Structure

There are three sub-projects.

* hashcash-lib
* callout
* tool

The callout is the JAR that will run within Apigee Edge as a Java callout.

The tool produces a JAR that can be used from the command line to generate or
verify Hashcash.

The hashcash-lib is used within both of the preceding.


## Build Dependencies

- Apigee Edge expressions v1.0
- Apigee Edge message-flow v1.0
- testng v6.8.7 (needed only for building+running tests)
- jmockit v1.7 (needed only for building+running tests)

These jars must be available on the classpath for the compile to succeed. You do
not need to worry about these jars if you are not building from source. The
buildsetup.sh script will download the Apigee files for you automatically, and
will insert them into your maven cache. The pom file will take care of the other
Jars.


## License

This material is Copyright (c) 2016-2020 Google, LLC.  and is licensed under the
[Apache 2.0 License](LICENSE). This includes the Java code as well as the API
Proxy configuration.


## Bugs

* The hashcash-lib and tool projects lack tests.

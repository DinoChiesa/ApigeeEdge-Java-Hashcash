# Java Hashcash Edge callout

This directory contains the a simple custom policy for Apigee Edge. The
policy performs a [HashCash](http://www.hashcash.org/) verification.
It can be used to aid in limiting denial-of-service attacks or enforcing a proof-of-work on clients.

## Background

As API Adoption increases, denial-of-service and brute force bot attacks are more often targeting APIs, and the stakes are growing larger. A business that runs commerce or customer loyalty programs through their APIs stands to lose customer loyalty if attacks compromise service.

HashCash is a mechanism first conceived to raise the computational cost of sending email, and thereby reduce spam. It requires that the sender produce a value that is computationally expensive to generate, but computationally easy to verify.

It does this by requiring the client or sender to generate a "partial hash collision". 

A hash function is a cryptographic function for which it is supposed to be hard to find two inputs that produce the same output. Common hash functions are MD5 and SHA1. (Hashcash uses the SHA1 hash function).  Under Hashcash, the client must find a text that produces a hash value that conforms to a particular constraint - for example, it exhibits 4 bytes of zeros at the start. This is the "partial hash collision"

To do this, the client must try lots of test strings. The receiver of the hashcash can easily verify that the client has selected a valid test string. Just one hash computation will do. When the receiver (the API Proxy in Apigee Edge) verifies the hashcash, it verifies that the client has performed the work to find the hash collision. This is sometimes called "proof-of-work".

For more on Hashcash, you may want to read [the Wikipedia article](https://en.wikipedia.org/wiki/Hashcash).

## Using the Hashcash policy in Apigee Edge

This custom policy makes it easy to embed Hashcash verification in any API Proxy running in Apigee Edge.

You do not need to build the source code in order to use the policy in Apigee Edge. 
All you need is the pre-built JAR, and the appropriate configuration for the policy. 
If you want to build it, feel free.  The instructions are [here](project). 

Whether you use the pre-built JAR or you build it yourself, follow these instructions to use the JAR as a custom policy inside Apigee Edge: 

1. copy the jar file, available in  target/edge-custom-hashcash-callout.jar , if you have built the jar, or in [the repo](bundle/apiproxy/resources/java/edge-custom-hashcash-callout.jar) if you have not, to your apiproxy/resources/java directory. You can do this offline, or using the graphical Proxy Editor in the Apigee Edge Admin Portal.

2. also copy the dependency... the hashcash-1.0.jar ... to the same directory.
   Find this in target/lib/hashcash-1.0.jar 

2. include an XML file for the Java callout policy in your
   apiproxy/resources/policies directory. It should look
   like this:  
   ```xml
    <JavaCallout name='Java-Hashcash-1'>
        ...
      <ClassName>com.google.apigee.edgecallouts.HashcashCallout</ClassName>
      <ResourceURL>java://edge-custom-hashcash-callout.jar</ResourceURL>
    </JavaCallout>
   ```  

3. use the Edge UI, or a command-line tool like [pushapi](https://github.com/carloseberhardt/apiploy) or [apigeetool](https://github.com/apigee/apigeetool-node) or similar to
   import the proxy into an Edge organization, and then deploy the proxy . 
   Eg,    
   ```pushapi -v -d -o ORGNAME -e test -n hashcash ```

4. Use a client to generate and send http requests to the proxy you just deployed .


## Notes on Usage

There is one callout class, com.dinochiesa.edgecallouts.Hashcash.

The policy is configured via properties set in the XML.  You can set these properties: 

| property name     | status    | description                                | 
| ----------------- |-----------|--------------------------------------------| 
| action            | Required  | value must be "verify"                     |
| requiredBits      | Required  | numeric, number of bits for hash collision.|
| requiredResource  | Optional  | the resource to check in the hashcash.     |
| timeAllowance     | Optional  | number of milliseconds to allow for time skew. Defaults to 10000 (10 seconds). Use -1 to disable the time check. |


## Example Policy Configurations

### Verifying a Hashcash

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout.jar</ResourceURL>
</JavaCallout>
```

The above retrieves the hashcash from a header in the request, named "hash".



### Verifying a Hashcash with a Resource

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='requiredResource'>dchiesa@google.com</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout.jar</ResourceURL>
</JavaCallout>
```

### Verifying a Hashcash with a Resource specified in a context variable

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='requiredResource'>{client_id}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout.jar</ResourceURL>
</JavaCallout>
```

### Verifying a Hashcash with Resource and bits specified in a context variable

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>{requiredBits}</Property>
    <Property name='requiredResource'>{client_id}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout.jar</ResourceURL>
</JavaCallout>
```

### Verifying a Hashcash while disabling the time check

(NOT recommended.)

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='timeAllowance'>-1</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='requiredResource'>{client_id}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout.jar</ResourceURL>
</JavaCallout>
```

### Additional Notes

Consider whether you should configure your API Proxy to cache the
Hashcash, and reject any repeated hashcash values.

If you include the date check (this is the default behavior, and is
recommended) in the hashcash verification, then you have protection
against replay attacks due to the date, and you may not need the cache
check, if the date skew window is small enough. In any case the cache
TTL can be relatively low, as long as it is larger than the time skew
allowance.


## Example API Proxy

You can find an example proxy bundle that uses the policy, [here in this repo](bundle/apiproxy).


## Building

Building from source requires Java 1.7, and Maven. 

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
  This will build the jars and also run all the tests.



## Build Dependencies

- Apigee Edge expressions v1.0
- Apigee Edge message-flow v1.0
- Apache Commons Lang3 3.5
- testng v6.8.7 (needed only for building+running tests)
- jmockit v1.7 (needed only for building+running tests)


These jars must be available on the classpath for the compile to
succeed. You do not need to worry about these jars if you are not building from source. The buildsetup.sh script will download the Apigee files for
you automatically, and will insert them into your maven cache. The pom file will take care of the other Jars. 


## License

This material is Copyright 2016 Google, Inc.
and is licensed under the [Apache 2.0 License](LICENSE). This includes the Java code as well as the API Proxy configuration. 

## Bugs

??

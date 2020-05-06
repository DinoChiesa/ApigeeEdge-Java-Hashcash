# Apigee Edge callout for Hashcash

This directory contains the a simple custom policy for Apigee Edge. The
policy performs a [HashCash](http://www.hashcash.org/) verification.
It can be used to aid in limiting denial-of-service attacks or enforcing a proof-of-work on clients.

## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.
It's just a sample.

## Background

As API Adoption increases, denial-of-service and brute force bot attacks are more often targeting APIs, and the stakes are growing larger. A business that runs commerce or customer loyalty programs through their APIs stands to lose customer loyalty if attacks compromise service.

[HashCash](http://www.hashcash.org/) is a proof-of-work algorithm first conceived to raise the computational cost of sending email; the goal was to reduce spam email sent by bots. Hashcash requires that the sender produce a value that is computationally expensive to generate, but computationally easy to verify.

It does this by requiring the client or sender to generate a "partial hash collision".

A hash function is a cryptographic function for which it is supposed to be hard
to find two inputs that produce the same output. Common hash functions are MD5
and SHA{1,256,384,512}. Hashcash was originally specified to use the SHA1 hash
function, but since 2017, Google and other Internet luminaries have
dis-recommended SHA1 due to concerns about collision resistance. Today, it's
better to use SHA-256.

Under Hashcash, the client must find a text that
produces a hash value that conforms to a particular constraint - for example,
the hash value exhibits 20 bits of zeros at the start. This is the "partial hash
collision".

Because there is no way to know the hash value before computing it, the client
must try lots of test strings in order to find a string that generates a hash
that starts with a sequence of zeros. The receiver of the hashcash can easily
verify that the client has selected a valid test string. Just one hash
computation will do. When the receiver (the API Proxy in Apigee Edge) verifies
the hashcash, it verifies that the client has performed the work required to
find the hash collision. This is sometimes called "proof-of-work".

For more on Hashcash, you may want to read [the Wikipedia
article](https://en.wikipedia.org/wiki/Hashcash).

By including a Hashcash check in your API Proxies, you can force API clients to
demonstrate proof-of-work. This discourages bot attacks.

## Using the Hashcash policy in Apigee Edge

The custom policy included here makes it easy to embed Hashcash verification in
any API Proxy running in Apigee Edge.

You do not need to build the source code in order to use the policy in Apigee Edge.
All you need is the pre-built JAR, and the appropriate configuration for the policy.
If you want to build it, feel free.  The instructions are [here](project).

Whether you use the pre-built JAR or you build it yourself, follow these instructions to use the JAR as a custom policy inside Apigee Edge:

1. copy the jar file, available in  target/edge-custom-hashcash-callout-20200506.jar , if you have built the jar, or in [the repo](bundle/apiproxy/resources/java/edge-custom-hashcash-callout-20200506.jar) if you have not, to your apiproxy/resources/java directory. You can do this offline, or using the graphical Proxy Editor in the Apigee Edge Admin Portal.

2. also copy the dependency... the apigee-hashcash-1.0.2.jar ... to the same directory.
   Find this in target/lib/apigee-hashcash-1.0.2.jar

2. include an XML file for the Java callout policy in your
   apiproxy/resources/policies directory. It should look
   like this:
   ```xml
    <JavaCallout name='Java-Hashcash-1'>
        ...
      <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
      <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
    </JavaCallout>
   ```

3. use the Edge UI, or a command-line tool like [importAndDeploy.js](https://github.com/DinoChiesa/apigee-edge-js-examples/blob/master/importAndDeploy.js) or similar to
   import the proxy into an Edge organization, and then deploy the proxy .
   Eg,
   ```
   importAndDeploy.js -v -n -o $ORG -e $ENV -d ./bundle
   ```

4. Use a client to generate and send http requests to the proxy you just deployed.


There is [a demonstration bundle](bundle) included here that does all of this for you.


## Notes on Usage

There is one callout class, com.google.apigee.edgecallouts.pow.HashcashCallout.

The policy is configured via properties set in the XML. You can set these properties:

| property name     | status    | description                                                              |
| ----------------- |-----------|--------------------------------------------------------------------------|
| action            | Required  | value must be "verify"                                                   |
| hash              | Required  | the hashcash to verify.                                                  |
| requiredBits      | Required  | numeric, number of bits for hash collision.                              |
| function          | Optional  | the hash function. Defaults to SHA-256. Can use MD5, SHA-{1,256,384,512} |
| requiredResource  | Optional  | the resource to check in the hashcash.                                   |
| timeAllowance     | Optional  | number of milliseconds to allow for time skew: the delta between "now" and the time asserted in the hashcash solution. This defaults to 10000 (10 seconds). Use -1 to disable the time check. |

About time allowance: you will need some non-zero time allowance because it
takes time for the client to compute the hashcash.  If the number of bits is
high enough, it might often take the client more than 10 seconds to compute the
hashcash.  Adjust the timeAllowance accordingly.

## Example Policy Configurations

### Verifying a Hashcash

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
</JavaCallout>
```

The above retrieves the hashcash from a header in the request, named "hash".

"Verify" in this case means:
- applying the hash function (by default SHA-256 in this implementation) to the hashcash produces a hash that has 20 bits of leading zeros
- the timestamp in the hash is current. By default this is "not less than 10 seconds in the past".

You can use any context variable for the input hashcash value. Typically these
variables are sent in headers.


### Verifying a Hashcash with a Resource

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='requiredResource'>dchiesa@google.com</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
</JavaCallout>
```

In addition to the verification performed above, this also verifies that the
"resource" portion of the hashcash is the specified hard-coded value.
this is usually important.

### Verifying a Hashcash with a Resource, and a maximum time-delta of 5 seconds

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='timeAllowance'>5000</Property>
    <Property name='requiredResource'>dchiesa@google.com</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
</JavaCallout>
```

### Verifying a Hashcash with a Resource specified in a context variable

A good resource to use is a `client_id`, or `proxy.pathsuffix` or
perhaps some combination of those things. The following shows the use of the
`client_id`.

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='requiredResource'>{client_id}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
</JavaCallout>
```

Same as above, but with a variable.

### Verifying a Hashcash with Resource and bits specified in a context variable

This policy will use SHA-256 as the hash function, by default.

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='requiredBits'>{requiredBits}</Property>
    <Property name='requiredResource'>{client_id}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
</JavaCallout>
```

### Verifying a Hashcash computed with SHA-1 as the hash function

This policy configuration explicitly overrides the hash function so that it uses
SHA-1.

```xml
<JavaCallout name='Java-Hashcash-1'>
  <Properties>
    <Property name='action'>verify</Property>
    <Property name='hash'>{request.header.hash}</Property>
    <Property name='function'>SHA-1</Property>
    <Property name='requiredBits'>20</Property>
    <Property name='requiredResource'>{client_id}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.pow.HashcashCallout</ClassName>
  <ResourceURL>java://edge-custom-hashcash-callout-20200506.jar</ResourceURL>
</JavaCallout>
```

This one performs the same kind of verification as the previous, but uses the
SHA-1 hash function. SHA-1 is considered to be less secure that
SHA-256. Regardless of the function used in the callout, the sender (client)
would need to use the same hash function in computing its hashcash.


### Additional Notes

Consider whether you should configure your API Proxy to cache the
Hashcash, and reject any repeated hashcash values.

The implicit timestamp check in the hashcash verification provides protection
against replay attacks. For this reason, you may not need the cache
check, if the date skew window is small enough. In any case the cache
TTL can be relatively low, as long as it is larger than the time skew
allowance.


## Example API Proxy

You can find an example proxy bundle that uses the policy, [here in this repo](bundle/apiproxy).


## License

This material is Copyright 2016-2020 Google, LLC.  and is licensed under the
[Apache 2.0 License](LICENSE). This includes the Java code as well as the API
Proxy configuration.


## Support

This callout is open-source software, and is not a supported part of Apigee Edge.
If you need assistance, you can try inquiring on
[The Apigee Community Site](https://community.apigee.com).  There is no service-level
guarantee for responses to inquiries regarding this callout.


## Bugs

??

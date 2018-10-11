# API Proxy bundle to demonstrate Hashcash callout

This Apigee Edge API Proxy demonstrates the use of the custom Java policy that verifies a Hashcash.
It can be used on private cloud or public cloud instances of Edge.  It relies on [the custom Java policy](../callout) included here.


## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.
It's just an example.

## Notes on usage

You will need to compute a hashcash on the client side in order to send one to
the server. For this purpose, this repo includes a simple Java program that generates Hashcashes.

There is also a wrapper script to drive the Java program.

In all the examples that follow, you should replace the APIHOST with something like

* ORGNAME-ENVNAME.apigee.net, if running in the Apigee-managed public cloud
* VHOST_IP:VHOST_PORT, if running in a self-managed cloud


## Prepare

To provision the example API proxy into your organization, you can use the provisioning script included here:

```sh
scripts/provisionProxy.sh  -o ORGNAME -e ENVNAME -v
```

This script will use curl to import and deploy the proxy.



### Example 1: Verify a valid hashcash

First generate a hashcash:

```
./scripts/hashcashTool.sh  -r dchiesa@google.com -b 18
```
The output will be something like this:

```
1:18:161222233841:dchiesa@google.com::d5d66d53b1c04d48:fa8a1e5c10921535
```

You will notice that it takes longer to generate a hash collision with a
higher bit value.  If you pass 22 it normally takes 5 or 6 seconds.  If
you pass 24 it can take a goooood long while. Passing 32 might take
weeks of computation.

Regardless, the output of that script is a hashcash. Pass that hashcash to the proxy, along with a value for the number of bits to enforce for hash collision:

```
HASH=1:18:161222233841:dchiesa@google.com::d5d66d53b1c04d48:fa8a1e5c10921535
APIHOST=$ORG-$ENV.apigee.net
curl -i -X POST -H content-type:application/json \
  https://$APIHOST/hashcash/t1-verify-no-resource \
  -d '{
    "hash" : "'$HASH'",
    "bits" : 16
    }'
```

These parameters are then used by the HashcashCallout during its check.

Obviously, passing the enforcement value here is done for demonstration purposes. You wouldn't
normally allow actual clients to your APIs to pass this parameter.

The result will be something like this, in the case of success:

```json
{
    "hash" : "1:18:161222234025:dchiesa@google.com::8652fc6ca9f355b9:b496b8e32454001d",
    "requiredBits" : 16,
    "computedBits" : 20,
    "isValid" : "true",
    "cashDateFormatted": "2016-12-22T23:40:25.000+0000",
    "nowFormatted": "2016-12-22T23:40:39.013+0000",
    "reason": "--",
    "timeAllowance": 30000,
    "timeDelta": 14013
}
```

...or like this, in the case of time skew failure:

```json
{
    "hash" : "1:18:161222233841:dchiesa@google.com::d5d66d53b1c04d48:fa8a1e5c10921535",
    "requiredBits" : 16,
    "computedBits" : 18,
    "isValid" : "false",
    "cashDateFormatted": "2016-12-22T23:38:41.000+0000",
    "nowFormatted": "2016-12-22T23:38:56.537+0000",
    "reason": "timestamp check failed",
    "timeAllowance": 10000,
    "timeDelta": 15537
}
```

The time allowance in the policy configuration inside the API Proxy in
this example bundle is set to 60000, or 60 seconds.  If you cannot
generate the hashcash and then transmit it within 60 seconds, the
hashcash will always be rejected. If you have trouble doing the
cut/paste, then you may wish to modify the API Proxy bundle to relax the
time check.


If you try to verify a hashcash that does not meet the minimum hash collision,
the Callout policy will generate an error. For example:

```
curl -X POST -H content-type:application/json \
  https://cap500-test.apigee.net/hashcash/t1-verify-no-resource \
  -d '{
    "hash" : "1:22:161223000303:dino@example.org::43ed425802db4404:a87b708182642193",
    "bits" : 32
    }'
```

The response looks like this:

```json
{
    "hash" : "1:22:161223000303:dino@example.org::43ed425802db4404:a87b708182642193",
    "requiredBits" : 32,
    "computedBits" : 22,
    "isValid" : "false",
    "cashDateFormatted": "",
    "nowFormatted": "",
    "reason": "hash collision insufficient",
    "timeAllowance": --,
    "timeDelta": --
}
```

This response is dynamically generated from the output variables set
into the message context by the policy.  The variables are all prefixed with the string "hashcash_".
In other words you must use this from within a subsequent JS callout:


```javascript
context.getVariable('hashcash_isValid');
context.getVariable('hashcash_cashDateFormatted');
// etc
```

Of interest are:

| variable name     | description                                                      |
| ----------------- |------------------------------------------------------------------|
| hash              | value of the hash that was checked.                              |
| isValid           | true/false. Indicating whether the hashcash was valid.           |
| requiredBits      | number of bits configured in the policy.                         |
| computedBits      | number of bits computed on the received hash.                    |
| reason            | the reason the hashcash was deemed invalid. (if applicable)      |
| cashDate          | ms since epoch on the hashcash.                                  |
| cashDateFormatted | time on the hashcash, formatted. eg 2016-12-23T00:03:03.000+0000 |
| timeDelta         | ms difference between NOW and the date on the hashcash.          |
| error             | error message if the callout experienced an exception while processing. |

Also: you will see these variables in the trace window, if you trace your API proxy.




### Example 2: Verify a hashcash and check the resource

When you specify the resource in the Java callout configuration, the
callout simply performs a string comparison. This is a minor check, but
including this check into the Callout can help avoid a condition
statement in the proxy flow.

Generate the hashcash:

```
./scripts/hashcashTool.sh  -r dino@example.org -b 22
```

Verify it:

```
curl -i -X POST -H content-type:application/json \
  https://APIHOST/hashcash/t2-verify-resource \
  -d '{
    "hash" : "1:18:161222233841:dchiesa@google.com::d5d66d53b1c04d48:fa8a1e5c10921535",
    "bits" : 22,
    "resource" : "dino@example.org"
    }'
```

## Teardown

To de-provision the example API proxy from your organization, again use the provisioning script:

```sh
scripts/provisionProxy.sh -o ORGNAME -e ENVNAME -v -r
```



## Notes

1. This callout is tested to verify only version 1 hashcash.

1. Normally you would not allow the client or caller to specify the
   required bits, as is done in this example. This is done just for
   illustrative purposes.  Normally the server or receiver will
   stipulate the minimum hash collision in bits, and communicate that to
   the developer of the client.

2. This example shows passing the hashcash as a parameter inside a JSON payload.
   In case it is not obvious, you could also configure your API proxy to accept
   a hashcash parameter in a header, or in a formparam.

3. The examples here show the use of an email address for the resource. This
   is not required. In fact it might be a better idea to use the client_id
   as the resource, in the case of a Hashcash being applied to an API.
   In that case you would want to verify the client_id as well, either before
   or after verifying the hashcash. This is left as an exercise for the reader.

4. This example shows the API proxy returning verbose information to the
   caller indicating the status of the hashcash check. This is
   inappropriate in a production environment.  Normally you would want
   to emit as little information as possible. The example sends back all
   of this information just for demonstration purposes.



## Support

This callout is open-source software, and is not a supported part of Apigee Edge.
If you need assistance, you can try inquiring on
[The Apigee Community Site](https://community.apigee.com).  There is no service-level
guarantee for responses to inquiries regarding this callout.



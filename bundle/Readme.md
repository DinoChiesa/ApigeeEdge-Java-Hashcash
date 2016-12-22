# API Proxy bundle to demonstrate Hashcash callout

This Apigee Edge API Proxy demonstrates the use of the custom Java policy that verifies a Hashcash.
It can be used on private cloud or public cloud instances of Edge.  It relies on [the custom Java policy](../callout) included here.


## Notes on usage

You will need to compute a hashcash on the client side in order to send one to
the server. For this purpose, this repo includes a simple Java program that generates Hashcashes.

There is also a wrapper script to drive the Java program and curl.  

In all the examples that follow, you should replace the APIHOST with something like

* ORGNAME-ENVNAME.apigee.net, if running in the Apigee-managed public cloud
* VHOST_IP:VHOST_PORT, if running in a self-managed cloud


### Example 1: Verify a valid hashcash

First generate a hashcash:

```
./scripts/hashcashTool.sh  -r dchiesa@google.com -b 18
```
The output will be something like this:

```
1:18:161222233841:dchiesa@google.com::d5d66d53b1c04d48:fa8a1e5c10921535
```

Then, pass that hashcash to the proxy:

```
curl -i -X POST -H content-type:application/json \
  https://APIHOST/hashcash/verify \
  -d '{
    "hash" : "1:18:161222233841:dchiesa@google.com::d5d66d53b1c04d48:fa8a1e5c10921535",
    "bits" : 16
    }'
```

The result will be something like this, in the case of time skew failure: 

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

or like this, in the case of success:

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

The time allowance in the policy configuration inside the API Proxy is
set to 60000, or 60 seconds.  If you cannot generate the hashcash and
then transmit it within 60 seconds, the hashcash will always be
rejected.


## Notes

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



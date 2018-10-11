package com.google.apigee.testng.tests;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import mockit.Mock;
import mockit.MockUp;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.message.Message;

import com.google.apigee.edgecallouts.pow.HashcashCallout;
import com.google.apigee.HashCash;

// import org.codehaus.jackson.map.DeserializationConfig;
// import org.codehaus.jackson.map.ObjectMapper;
import java.util.Properties;

public class TestHashcashCallout {
    private final static String testDataDir = "src/test/resources/test-data";

    MessageContext msgCtxt;
    String messageContent;
    Message message;
    ExecutionContext exeCtxt;

    @BeforeMethod()
    public void testSetup1() {

        msgCtxt = new MockUp<MessageContext>() {
            private Map variables;
            public void $init() {
                variables = new HashMap();
            }

            @Mock()
            public <T> T getVariable(final String name){
                if (variables == null) {
                    variables = new HashMap();
                }
                return (T) variables.get(name);
            }

            @Mock()
            public boolean setVariable(final String name, final Object value) {
                if (variables == null) {
                    variables = new HashMap();
                }
                variables.put(name, value);
                return true;
            }

            @Mock()
            public boolean removeVariable(final String name) {
                if (variables == null) {
                    variables = new HashMap();
                }
                if (variables.containsKey(name)) {
                    variables.remove(name);
                }
                return true;
            }

            @Mock()
            public Message getMessage() {
                return message;
            }
        }.getMockInstance();

        exeCtxt = new MockUp<ExecutionContext>(){ }.getMockInstance();

        message = new MockUp<Message>(){
            @Mock()
            public InputStream getContentAsStream() {
                return new ByteArrayInputStream(messageContent.getBytes(StandardCharsets.UTF_8));
            }
        }.getMockInstance();
    }

    @Test
    public void testMissingConfig() {
        Properties props = new Properties();
        //props.setProperty("debug", "true");
        props.setProperty("hash", "seven");
        props.setProperty("action", "verify");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.ABORT, "result");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "Exception", "reason");

        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertEquals(error, "Improperly formed HashCash", "error");
    }

    @Test
    public void testMissingAction() {
        Properties props = new Properties();
        props.setProperty("hash", "seven");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.ABORT, "result");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "Exception", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertEquals(error, "action resolves to an empty string.", "error");
    }


    @Test
    public void test_DisableTimeCheck_MissingRequiredBits() {
        Properties props = new Properties();
        //props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("timeAllowance", "-1");
        props.setProperty("hash", "1:20:1303030600:adam@cypherspace.org::McMybZIhxKXu57jd:ckvi");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.ABORT, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "false", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "Exception", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertEquals(error, "requiredBits resolves to an empty string.", "error");
    }

    @Test
    public void testGoodConfig_BadTimestamp() {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "20");
        props.setProperty("hash", "1:20:1303030600:adam@cypherspace.org::McMybZIhxKXu57jd:ckvi");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "false", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "timestamp check failed", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");
    }

    @Test
    public void test_DisableTimeCheck() {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "20");
        props.setProperty("timeAllowance", "-1");
        props.setProperty("hash", "1:20:1303030600:adam@cypherspace.org::McMybZIhxKXu57jd:ckvi");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "true", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertNull(reason, "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");

        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        Assert.assertEquals(computedBits, "20", "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "20", "requiredBits");
    }

    @Test
    public void test_DisableTimeCheck_RequireLess() {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "12");
        props.setProperty("timeAllowance", "-1");
        props.setProperty("hash", "1:20:1303030600:adam@cypherspace.org::McMybZIhxKXu57jd:ckvi");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "true", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertNull(reason, "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");

        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        Assert.assertEquals(computedBits, "20", "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "12", "requiredBits");
    }


    @Test
    public void test_FreshHash() throws java.security.NoSuchAlgorithmException {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "18");
        props.setProperty("timeAllowance", "-1");
        HashCash hc = HashCash.mintCash("dchiesa@google.com", 22);
        System.out.printf("hash: %s\n", hc.toString());
        props.setProperty("hash", hc.toString());
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "true", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertNull(reason, "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");

        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        int cb = Integer.parseInt(computedBits);
        Assert.assertTrue(cb >= 22, "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "18", "requiredBits");

        String hash = msgCtxt.getVariable("hashcash_hash");
        Assert.assertEquals(hash, hc.toString(), "hash");
    }

    @Test
    public void test_FreshHash_ResourceMatch() throws java.security.NoSuchAlgorithmException {
        String resource = "dchiesa@google.com";
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "18");
        props.setProperty("requiredResource", resource);
        props.setProperty("timeAllowance", "-1");
        HashCash hc = HashCash.mintCash(resource, 20);
        System.out.printf("hash: %s\n", hc.toString());
        props.setProperty("hash", hc.toString());
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "true", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertNull(reason, "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");

        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        int cb = Integer.parseInt(computedBits);
        Assert.assertTrue(cb >= 20, "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "18", "requiredBits");

        String hash = msgCtxt.getVariable("hashcash_hash");
        Assert.assertEquals(hash, hc.toString(), "hash");
    }


    @Test
    public void test_FreshHash_ResourceMatch_Variables() throws java.security.NoSuchAlgorithmException {
        String resource = "dchiesa@google.com";
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "{parsedInput.bits}");
        props.setProperty("requiredResource", resource);
        props.setProperty("timeAllowance", "-1");
        HashCash hc = HashCash.mintCash(resource, 20);
        System.out.printf("hash: %s\n", hc.toString());
        props.setProperty("hash", "{parsedInput.hash}");
        HashcashCallout callout = new HashcashCallout(props);

        msgCtxt.setVariable("parsedInput.hash", hc.toString());
        msgCtxt.setVariable("parsedInput.bits", "18");

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "true", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertNull(reason, "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");

        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        int cb = Integer.parseInt(computedBits);
        Assert.assertTrue(cb >= 20, "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "18", "requiredBits");

        String hash = msgCtxt.getVariable("hashcash_hash");
        Assert.assertEquals(hash, hc.toString(), "hash");
    }

    @Test
    public void test_FreshHash_ResourceMismatch() throws java.security.NoSuchAlgorithmException {
        String resource = "dchiesa@google.com";
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "18");
        props.setProperty("requiredResource", resource);
        props.setProperty("timeAllowance", "-1");
        HashCash hc = HashCash.mintCash(resource + " something else", 20);
        System.out.printf("hash: %s\n", hc.toString());
        props.setProperty("hash", hc.toString());
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "false", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "resource mismatch", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");

        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        int cb = Integer.parseInt(computedBits);
        Assert.assertTrue(cb >= 20, "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "18", "requiredBits");

        String hash = msgCtxt.getVariable("hashcash_hash");
        Assert.assertEquals(hash, hc.toString(), "hash");
    }

    @Test
    public void test_FreshHash_InsufficientBits() throws java.security.NoSuchAlgorithmException {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("requiredBits", "24");
        props.setProperty("timeAllowance", "-1");
        HashCash hc = HashCash.mintCash("dchiesa@google.com", 20);
        System.out.printf("hash: %s\n", hc.toString());
        props.setProperty("hash", hc.toString());
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String computedBits = msgCtxt.getVariable("hashcash_computedBits");
        int cb = Integer.parseInt(computedBits);
        Assert.assertTrue(cb >= 20, "computedBits");

        String requiredBits = msgCtxt.getVariable("hashcash_requiredBits");
        Assert.assertEquals(requiredBits, "24", "requiredBits");

        String hash = msgCtxt.getVariable("hashcash_hash");
        Assert.assertEquals(hash, hc.toString(), "hash");

        String isValid, reason, error;
        if (cb >= 24) {
            // it's possible though we asked for only 20 bits, that we get 24.
            isValid = msgCtxt.getVariable("hashcash_isValid");
            Assert.assertEquals(isValid, "true", "validity");
            reason = msgCtxt.getVariable("hashcash_reason");
            Assert.assertNull(reason, "reason");
            error = msgCtxt.getVariable("hashcash_error");
            Assert.assertNull(error, "error");
        }
        else {
            isValid = msgCtxt.getVariable("hashcash_isValid");
            Assert.assertEquals(isValid, "false", "validity");
            reason = msgCtxt.getVariable("hashcash_reason");
            Assert.assertEquals(reason, "hash collision insufficient", "reason");
            error = msgCtxt.getVariable("hashcash_error");
            Assert.assertNull(error, "error");
        }

    }

    @Test
    public void test_BadFunction() {
        Properties props = new Properties();
        props.setProperty("debug", "false");
        props.setProperty("action", "verify");
        props.setProperty("function", "dino");
        props.setProperty("requiredBits", "20");
        props.setProperty("hash", "1:20:1303030600:adam@cypherspace.org::McMybZIhxKXu57jd:ckvi");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.ABORT, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "false", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "Exception", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertEquals(error, "dino MessageDigest not available", "error");
    }

    @Test
    public void test_MismatchedFunction() {
        // this will result in an "insufficient collision" state
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("function", "SHA-256");
        props.setProperty("requiredBits", "20");
        props.setProperty("hash", "1:20:1303030600:adam@cypherspace.org::McMybZIhxKXu57jd:ckvi");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "false", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "hash collision insufficient", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");
    }

    @Test
    public void test_SHA256() {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("function", "SHA-256");
        props.setProperty("timeAllowance", "-1");
        props.setProperty("requiredBits", "20");
        props.setProperty("hash", "1:20:181011202759:dchiesa@google.com::87bfce98e08cde7:54171745c04f92b8");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "true", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertNull(reason, "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");
    }

    @Test
    public void test_SHA256_Expired() {
        Properties props = new Properties();
        props.setProperty("debug", "true");
        props.setProperty("action", "verify");
        props.setProperty("function", "SHA-256");
        props.setProperty("requiredBits", "20");
        props.setProperty("hash", "1:20:181011202759:dchiesa@google.com::87bfce98e08cde7:54171745c04f92b8");
        HashcashCallout callout = new HashcashCallout(props);

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        Assert.assertEquals(actualResult, ExecutionResult.SUCCESS, "result");
        String isValid = msgCtxt.getVariable("hashcash_isValid");
        Assert.assertEquals(isValid, "false", "validity");
        String reason = msgCtxt.getVariable("hashcash_reason");
        Assert.assertEquals(reason, "timestamp check failed", "reason");
        String error = msgCtxt.getVariable("hashcash_error");
        Assert.assertNull(error, "error");
    }


}

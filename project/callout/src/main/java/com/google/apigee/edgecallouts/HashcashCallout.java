// HashcashCallout.java
//
// This is the source code for a Java callout for Apigee Edge.
// This callout adds a node into a XML document.
//
// --------------------------------------------
// This code is licensed under the Apache 2.0 license. See the LICENSE
// file that accompanies this source.
//
// ------------------------------------------------------------------

package com.google.apigee.edgecallouts;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.message.Message;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.lang3.StringUtils;
import com.google.apigee.HashCash;

public class HashcashCallout implements Execution {
    private static final String _varPrefix = "hashcash_";
    private static final String variableReferencePatternString = "(.*?)\\{([^\\{\\}]+?)\\}(.*?)";
    private static final Pattern variableReferencePattern = Pattern.compile(variableReferencePatternString);
    // NB: SimpleDateFormat is not thread-safe
    private static final FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private final static long defaultTimeSkewAllowanceMilliseconds = 10000L;

    private enum HashcashAction { Verify, Generate }
    private Map properties; // read-only

    public HashcashCallout (Map properties) {
        this.properties = properties;
    }

    private static final String varName(String s) { return _varPrefix + s; }

    private String getHash(MessageContext msgCtxt) throws Exception {
        return getSimpleOptionalProperty("hash", msgCtxt);
    }

    private int getRequiredBits(MessageContext msgCtxt) throws Exception {
        return Integer.parseInt(getSimpleRequiredProperty("requiredBits", msgCtxt),10);
    }

    private String getRequiredResource(MessageContext msgCtxt) throws Exception {
        return getSimpleOptionalProperty("requiredResource", msgCtxt);
    }

    private long getTimeAllowance(MessageContext msgCtxt) throws Exception {
        String timeAllowance = getSimpleOptionalProperty("timeAllowance", msgCtxt);
        if (timeAllowance == null) {
            return defaultTimeSkewAllowanceMilliseconds;
        }
        long longValue = Long.parseLong(timeAllowance, 10);
        return longValue;
    }

    private boolean getDebug() {
        String value = (String) this.properties.get("debug");
        if (value == null) return false;
        if (value.trim().toLowerCase().equals("true")) return true;
        return false;
    }

    private HashcashAction getAction(MessageContext msgCtxt) throws Exception {
        String action = getSimpleRequiredProperty("action", msgCtxt);
        action = action.toLowerCase();
        if (action.equals("verify")) return HashcashAction.Verify;
        if (action.equals("generate")) return HashcashAction.Generate;
        throw new IllegalStateException("action value is unknown: (" + action + ")");
    }

    private String getSimpleRequiredProperty(String propName, MessageContext msgCtxt) throws Exception {
        String value = (String) this.properties.get(propName);
        if (value == null) {
            throw new IllegalStateException(propName + " resolves to an empty string.");
        }
        value = value.trim();
        if (value.equals("")) {
            throw new IllegalStateException(propName + " resolves to an empty string.");
        }
        value = resolvePropertyValue(value, msgCtxt);
        if (value == null || value.equals("")) {
            throw new IllegalStateException(propName + " resolves to an empty string.");
        }
        return value;
    }

    private String getSimpleOptionalProperty(String propName, MessageContext msgCtxt) throws Exception {
        String value = (String) this.properties.get(propName);
        if (value == null) { return null; }
        value = value.trim();
        if (value.equals("")) { return null; }
        value = resolvePropertyValue(value, msgCtxt);
        if (value == null || value.equals("")) { return null; }
        return value;
    }

    // If the value of a property contains a pair of curlies,
    // eg, {apiproxy.name}, then "resolve" the value by de-referencing
    // the context variable whose name appears between the curlies.
    private String resolvePropertyValue(String spec, MessageContext msgCtxt) {
        Matcher matcher = variableReferencePattern.matcher(spec);
        StringBuffer sb = new StringBuffer();
        // System.out.printf("resolvePropertyValue spec(%s)\n", spec);
        while (matcher.find()) {
            
            // System.out.printf("resolvePropertyValue matcher: (%s) (%s) (%s)\n",
            //                   matcher.group(1),
            //                   matcher.group(2),
            //                   matcher.group(3));
            
            matcher.appendReplacement(sb, "");
            sb.append(matcher.group(1));
            sb.append((String) msgCtxt.getVariable(matcher.group(2)));
            sb.append(matcher.group(3));
        }
        matcher.appendTail(sb);
        
        //System.out.printf("resolvePropertyValue result: (%s)\n", sb.toString());
        
        return sb.toString();
    }


    private void recordTimeVariable(MessageContext msgContext, Date d, String label) {
        msgContext.setVariable(varName(label), d.getTime() + "");
        msgContext.setVariable(varName(label + "Formatted"), fdf.format(d));
    }


    public ExecutionResult execute (final MessageContext msgCtxt,
                                    final ExecutionContext execContext) {
        try {
            msgCtxt.removeVariable(varName("reason"));
            msgCtxt.removeVariable(varName("error"));
            msgCtxt.removeVariable(varName("stacktrace"));
            HashcashAction action = getAction(msgCtxt);
            if (action == HashcashAction.Verify) {
                msgCtxt.setVariable(varName("isValid"), "false");
                String hash = getHash(msgCtxt);
                if (StringUtils.isEmpty(hash)) {
                    msgCtxt.setVariable(varName("reason"), "hash resolves to an empty string");
                    return ExecutionResult.SUCCESS;
                }
                msgCtxt.setVariable(varName("hash"), hash);
                HashCash hc = new HashCash(hash);

                // 1. check version
                if (hc.getVersion() != 1) {
                    msgCtxt.setVariable(varName("reason"), "incorrect hashcash version");
                    return ExecutionResult.SUCCESS;
                }

                // 2. check bits
                int requiredBits = getRequiredBits(msgCtxt);
                msgCtxt.setVariable(varName("requiredBits"), String.valueOf(requiredBits));
                int computedBits = hc.getComputedBits();
                msgCtxt.setVariable(varName("computedBits"), String.valueOf(computedBits));
                if (requiredBits > computedBits) {
                    msgCtxt.setVariable(varName("reason"), "hash collision insufficient");
                    return ExecutionResult.SUCCESS;
                }

                // 3. check date
                long timeAllowance = getTimeAllowance(msgCtxt);
                msgCtxt.setVariable(varName("timeAllowance"), Long.toString(timeAllowance,10));
                if (timeAllowance < 0L) {
                    msgCtxt.setVariable(varName("timeCheckDisabled"), "true");
                }
                else {
                    Date cashDate = hc.getDate();
                    recordTimeVariable(msgCtxt, cashDate,"cashDate");

                    Date now = new Date();
                    recordTimeVariable(msgCtxt, now,"now");

                    long ms = now.getTime() - cashDate.getTime();
                    msgCtxt.setVariable(varName("timeDelta"), String.valueOf(ms));
                    // positive means cash was computed in the past
                    if (ms<0) { ms *= -1; }
                    if (ms > timeAllowance) {
                        msgCtxt.setVariable(varName("reason"), "timestamp check failed");
                        return ExecutionResult.SUCCESS;
                    }
                }

                // 4. optionally check resource
                String resource = getRequiredResource(msgCtxt);
                if (resource != null) {
                    if (!resource.equals(hc.getResource())) {
                        msgCtxt.setVariable(varName("reason"), "resource mismatch");
                        return ExecutionResult.SUCCESS;
                    }
                }

                msgCtxt.setVariable(varName("isValid"), "true");
            }
            else {
                throw new UnsupportedOperationException("not implemented yet");
            }
        }
        catch (Exception e) {
            msgCtxt.setVariable(varName("reason"), "Exception");
            if (getDebug()) {
                System.out.println(ExceptionUtils.getStackTrace(e));
            }
            String error = e.toString();
            msgCtxt.setVariable(varName("exception"), error);
            int ch = error.lastIndexOf(':');
            if (ch >= 0) {
                msgCtxt.setVariable(varName("error"), error.substring(ch+2).trim());
            }
            else {
                msgCtxt.setVariable(varName("error"), error);
            }
            msgCtxt.setVariable(varName("stacktrace"), ExceptionUtils.getStackTrace(e));
            return ExecutionResult.ABORT;
        }

        return ExecutionResult.SUCCESS;
    }
}

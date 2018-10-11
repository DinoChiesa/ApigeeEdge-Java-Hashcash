// HashcashCallout.java
//
// Copyright 2018 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.apigee.edgecallouts.pow;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.google.apigee.HashCash;
import com.google.apigee.edgecallouts.util.VariableRefResolver;
import com.google.common.base.Throwables;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HashcashCallout implements Execution {
    private static final String _varPrefix = "hashcash_";
    private static final String variableReferencePatternString = "(.*?)\\{([^\\{\\}]+?)\\}(.*?)";
    private static final Pattern variableReferencePattern = Pattern.compile(variableReferencePatternString);

    private static final DateTimeFormatter dtf = DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SX")
        .withZone(ZoneId.of("GMT"));
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
        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
            sb.append(matcher.group(1));
            sb.append((String) msgCtxt.getVariable(matcher.group(2)));
            sb.append(matcher.group(3));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void clearVariables(MessageContext msgCtxt) {
        msgCtxt.removeVariable(varName("error"));
        msgCtxt.removeVariable(varName("exception"));
        msgCtxt.removeVariable(varName("stacktrace"));
        msgCtxt.removeVariable(varName("reason"));
        msgCtxt.removeVariable(varName("isValid"));
    }

    private void recordTimeVariable(MessageContext msgContext, Instant instant, String label) {
        msgContext.setVariable(varName(label), instant.toEpochMilli() + "");
        msgContext.setVariable(varName(label + "Formatted"), dtf.format(instant));
    }


    public ExecutionResult execute (final MessageContext msgCtxt,
                                    final ExecutionContext execContext) {
        try {
            VariableRefResolver resolver = new VariableRefResolver(s -> ((String) msgCtxt.getVariable(s)));
            clearVariables(msgCtxt);
            HashcashAction action = getAction(msgCtxt);
            if (action == HashcashAction.Verify) {
                msgCtxt.setVariable(varName("isValid"), "false");
                String hash = getHash(msgCtxt);
                if (hash == null || hash.equals("")) {
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
                    Instant cashDate = hc.getDate();
                    recordTimeVariable(msgCtxt, cashDate, "cashDate");

                    Instant now = Instant.now();
                    recordTimeVariable(msgCtxt, now,"now");

                    long ms = Duration.between(cashDate, now).toMillis();
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
        System.out.println(Throwables.getStackTraceAsString(e));
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
            msgCtxt.setVariable(varName("stacktrace"), Throwables.getStackTraceAsString(e));
            return ExecutionResult.ABORT;
        }

        return ExecutionResult.SUCCESS;
    }
}

// HashcashTool.java
// ------------------------------------------------------------------
//
// a tool for generating or verifying Hashcash .
//
// Last saved: <2020-May-06 14:08:17>
// ------------------------------------------------------------------

// Copyright 2016-2020 Google LLC.
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

package com.google.apigee.tools;

import com.google.apigee.HashCash;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

public class HashcashTool {
  private static final String version = "20200506-1400";
  private static final String optString = "vc:r:b:f:"; // getopt style
  private Hashtable<String, Object> options = new Hashtable<String, Object>();

  // public HashcashTool () {} // uncomment if wanted

  public HashcashTool(String[] args) throws java.lang.Exception {
    getOpts(args);
  }

  private void getOpts(String[] args) throws java.lang.Exception {
    // Parse command line args for args in the following format:
    //   -a value -b value2 ... ...

    // sanity checks
    if (args == null) return;
    if (args.length == 0) return;
    if (optString == null) return;
    final String argPrefix = "-";
    String patternString = "^" + argPrefix + "([" + optString.replaceAll(":", "") + "])";

    java.util.regex.Pattern p = java.util.regex.Pattern.compile(patternString);

    int L = args.length;
    for (int i = 0; i < L; i++) {
      String arg = args[i];
      java.util.regex.Matcher m = p.matcher(arg);
      if (!m.matches()) {
        throw new java.lang.Exception(
            "The command line arguments are improperly formed. Use a form like '-a value' or just '-b' .");
      }

      char ch = arg.charAt(1);
      int pos = optString.indexOf(ch);

      if ((pos != optString.length() - 1) && (optString.charAt(pos + 1) == ':')) {
        if (i + 1 < L) {
          i++;
          Object current = this.options.get(m.group(1));
          ArrayList<String> newList;
          if (current == null) {
            // not a previously-seen option
            this.options.put(m.group(1), args[i]);
          } else if (current instanceof ArrayList<?>) {
            // previously seen, and already a list
            newList = (ArrayList<String>) current;
            newList.add(args[i]);
          } else {
            // we have one value, need to make a list
            newList = new ArrayList<String>();
            newList.add((String) current);
            newList.add(args[i]);
            this.options.put(m.group(1), newList);
          }
        } else {
          throw new java.lang.Exception("Incorrect arguments.");
        }
      } else {
        // a "no-value" argument, like -v for verbose
        options.put(m.group(1), (Boolean) true);
      }
    }
  }

  private static String optionAsString(Object o) {
    if (o instanceof String) {
      return (String) o;
    }
    if (o instanceof Boolean) {
      return o.toString();
    }
    return null;
  }

  private static ArrayList<String> optionAsList(Object o) {
    if (o instanceof ArrayList<?>) {
      return (ArrayList<String>) o;
    }

    ArrayList<String> list = new ArrayList<String>();

    if (o instanceof String) {
      list.add((String) o);
    }
    return list;
  }

  private void maybeShowOptions() {
    Boolean verbose = (Boolean) this.options.get("v");
    if (verbose != null && verbose) {
      System.out.println("options:");
      Enumeration e = this.options.keys();
      while (e.hasMoreElements()) {
        // iterate through Hashtable keys Enumeration
        String k = (String) e.nextElement();
        Object o = this.options.get(k);
        String v = null;
        v = (o.getClass().equals(Boolean.class)) ? "true" : (String) o;
        System.out.println("  " + k + ": " + v);
      }

      // enumerate properties here?
    }
  }

  public void run() throws NoSuchAlgorithmException {
    Boolean verbose = (Boolean) this.options.get("v");
    String resource = optionAsString(options.get("r"));
    String requiredBits = optionAsString(options.get("b"));
    String hashFunctionName = optionAsString(options.get("f"));
    int intRequiredBits = (requiredBits == null) ? 20 : Integer.parseInt(requiredBits);

    // default the function to SHA-256
    if (hashFunctionName == null || hashFunctionName.trim().equals(""))
      hashFunctionName = "SHA-256";

    if (resource != null) {
      // generate
      HashCash hashcash = HashCash.mintCash(resource, intRequiredBits, hashFunctionName);
      System.out.printf("%s\n", hashcash);
      return;
    }

    String hash = optionAsString(options.get("c"));
    if (hash != null) {
      // verify
      HashCash hashcash = new HashCash(hash, hashFunctionName);
      System.out.printf("ver: %d\n", hashcash.getVersion());
      System.out.printf("bits: %d\n", hashcash.getComputedBits());
      System.out.printf("resource: %s\n", hashcash.getResource());
      DateTimeFormatter dtf =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SX").withZone(ZoneId.of("GMT"));
      System.out.printf("date: %s\n", dtf.format(hashcash.getDate()));
      if (requiredBits == null)
        System.out.printf(
                          "%sVALID\n", (intRequiredBits > hashcash.getComputedBits()) ? "NOT" : "");
      return;
    }

    System.err.printf("unrecognized.\n");
    usage();
    return;
  }

  public static void usage() {
    System.out.printf("HashcashTool v%s: generate or verify a hashcash.\n", version);
    System.out.println(
        "To generate:\n  java HashcashTool [-v] -r <resource> [-b <bits>] [-a <alg>] [-f <func>]");
    System.out.println(
        "To verify:\n  java HashcashTool [-v] -c <hashcash> [-b <bits>] [-a <alg>] [-f <func>]");
  }

  public static void main(String[] args) {
    try {
      HashcashTool me = new HashcashTool(args);
      me.run();
    } catch (java.lang.Exception exc1) {
      System.out.println("Exception:" + exc1.toString());
      exc1.printStackTrace();
    }
  }
}

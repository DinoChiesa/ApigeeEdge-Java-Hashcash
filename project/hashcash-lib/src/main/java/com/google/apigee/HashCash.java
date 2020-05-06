// HashCash.java
//
// Taken from Gregory Rubin's implementation and modified.
//
// Copyright 2016 Google LLC.
//
// ====================================================================
// Copyright 2006 Gregory Rubin grrubin@gmail.com
// Permission is given to use, modify, and or distribute this code so
// long as this message remains attached Please see the spec at:
// http://www.hashcash.org/

package com.google.apigee;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for generation and parsing of <a href="http://www.hashcash.org/">HashCash</a><br>
 * Copyright 2006 Gregory Rubin <a href="mailto:grrubin@gmail.com">grrubin@gmail.com</a><br>
 *  Permission is given to use, modify, and or distribute this code so long as this message remains attached<br>
 * Please see the spec at: <a href="http://www.hashcash.org/">http://www.hashcash.org/</a>
 * @author grrubin@gmail.com
 * @version 1.1
 */
public class HashCash implements Comparable<HashCash> {
    public static final int DEFAULT_HASHCASH_VERSION = 1;
    private static final int MAX_BITS_VALUE = 160;
    private static final DateTimeFormatter[] FORMATS = {
      DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneId.of("GMT")),
      DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneId.of("GMT"))
    };

    private static long milliFor16 = -1;

    private String _token;
    private String _hashFn;
    private int _version;
    private int _claimedBits;
    private int _computedBits;
    private byte[] _digest;
    private Instant _instant;
    private String _resource;
    private Map<String, List<String>> _extensions;

    /**
     * Parses and validates a HashCash using the default hash function of SHA1.
     * @throws NoSuchAlgorithmException If hashFunctionName is not a supported Message Digest
     */
    public HashCash(String cash) throws NoSuchAlgorithmException {
        this(cash, "SHA1");
    }

    /**
     * Parses and validates a HashCash using the named hash function.
     * @throws NoSuchAlgorithmException If hashFunctionName is not a supported Message Digest
     */
    public HashCash(String cash, String hashFunctionName) throws NoSuchAlgorithmException {
        _token = cash;
        String[] parts = cash.split(":");

        if ((parts.length != 6) && (parts.length != 7))
            throw new IllegalArgumentException("Improperly formed HashCash");

        _version = Integer.parseInt(parts[0]);
        if(_version < 0 || _version > 1)
            throw new IllegalArgumentException("The version is not supported");

        if((_version == 0 && parts.length != 6) ||
           (_version == 1 && parts.length != 7))
            throw new IllegalArgumentException("Improperly formed HashCash");

        int index = 1;
        _claimedBits = (_version == 1)? Integer.parseInt(parts[index++]) : 0;
        _instant = parseDate(parts[index++]);
        if (_instant == null) {
            throw new IllegalArgumentException("Improperly formed Date");
        }
        _resource = parts[index++];
        _extensions = deserializeExtensions(parts[index++]);
        if (hashFunctionName==null || hashFunctionName.equals("")) hashFunctionName = "SHA1";
        _hashFn = hashFunctionName;

        MessageDigest md = getMd();
        md.update(cash.getBytes());
        _digest = md.digest();
        _computedBits = numberOfLeadingZeros(_digest);
    }

    private MessageDigest getMd() throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(_hashFn);
        return md;
    }

    private HashCash() throws NoSuchAlgorithmException { }

    /**
     * Mints a version 1 HashCash using now as the date
     * @param resource the string to be encoded in the HashCash
     * @param requiredBits the number of bits required to be zero
     * @param hashFunctionName the name of the hash function, like SHA1 or SHA-256, to be used to compute the HashCash
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, int requiredBits, String hashFunctionName) throws NoSuchAlgorithmException {
        return mintCash(resource, null, null, requiredBits, DEFAULT_HASHCASH_VERSION, hashFunctionName);
    }

    /**
     * Mints a version 1 HashCash using now as the date
     * @param resource the string to be encoded in the HashCash
     * @param requiredBits the number of collision bits required
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, int requiredBits) throws NoSuchAlgorithmException {
        return mintCash(resource, null, null, requiredBits, DEFAULT_HASHCASH_VERSION);
    }

    /**
     * Mints a  HashCash  using now as the date
     * @param resource the string to be encoded in the HashCash
     * @param version Which version to mint.  Only valid values are 0 and 1
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, int requiredBits, int version) throws NoSuchAlgorithmException {
        return mintCash(resource, null, null, requiredBits, version);
    }

    /**
     * Mints a version 1 HashCash
     * @param resource the string to be encoded in the HashCash
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Date date, int requiredBits) throws NoSuchAlgorithmException {
      return mintCash(resource, null, date.toInstant(), requiredBits, DEFAULT_HASHCASH_VERSION);
    }

    /**
     * Mints a  HashCash
     * @param resource the string to be encoded in the HashCash
     * @param version Which version to mint.  Only valid values are 0 and 1
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Date date, int requiredBits, int version)
        throws NoSuchAlgorithmException {
      return mintCash(resource, null, date.toInstant(), requiredBits, version);
    }

    /**
     * Mints a version 1 HashCash using now as the date
     * @param resource the string to be encoded in the HashCash
     * @param extensions Extra data to be encoded in the HashCash
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, int requiredBits)
        throws NoSuchAlgorithmException {
        return mintCash(resource, extensions, null, requiredBits, DEFAULT_HASHCASH_VERSION);
    }

    /**
     * Mints a  HashCash using now as the date
     * @param resource the string to be encoded in the HashCash
     * @param extensions Extra data to be encoded in the HashCash
     * @param version Which version to mint.  Only valid values are 0 and 1
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, int requiredBits, int version)
        throws NoSuchAlgorithmException {
        return mintCash(resource, extensions, null, requiredBits, version);
    }

    /**
     * Mints a version 1 HashCash
     * @param resource the string to be encoded in the HashCash
     * @param extensions Extra data to be encoded in the HashCash
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, Date date, int requiredBits)
        throws NoSuchAlgorithmException {
      return mintCash(resource, extensions, date.toInstant(), requiredBits, DEFAULT_HASHCASH_VERSION);
    }

    /**
     * Mints a  HashCash
     * @param resource the string to be encoded in the HashCash
     * @param extensions Extra data to be encoded in the HashCash
     * @param instant the moment to encode in this hashcash
     * @param requiredBits the number of bits required to be zero
     * @param version Which version to mint.  Only valid values are 0 and 1
     * @param hashFunctionName the name of the hash function, like SHA1 or SHA-256, to be used to compute the HashCash
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, Instant instant, int requiredBits, int version, String hashFn)         throws NoSuchAlgorithmException {
        if(version < 0 || version > 1)
            throw new IllegalArgumentException("version must be 0 or 1");

        if(requiredBits < 0 || requiredBits > MAX_BITS_VALUE)
            throw new IllegalArgumentException("requiredBits must be between 0 and " + MAX_BITS_VALUE);

        if(resource.contains(":"))
            throw new IllegalArgumentException("Resource may not contain a colon.");

        if (instant == null)
          instant = Instant.now();

        HashCash result = new HashCash();
        MessageDigest md = MessageDigest.getInstance(hashFn);

        result._resource = resource;
        result._hashFn = hashFn;
        result._extensions = (null == extensions ? new HashMap<String, List<String> >() : extensions);
        result._instant = instant;
        result._version = version;

        String prefix;

        switch(version) {
            case 0:
              prefix = String.format("%d:%s:%s:%s:",
                                     version,
                                     FORMATS[0].format(instant),
                                     resource,
                                     serializeExtensions(extensions));
                result._token = generateCash(prefix, requiredBits, md);
                result._claimedBits = numberOfLeadingZeros(result._digest);
                break;

            case 1:
                result._claimedBits = requiredBits;
                prefix = String.format("%d:%d:%s:%s:%s:",
                                       version,
                                       requiredBits,
                                       FORMATS[0].format(instant),
                                       resource,
                                       serializeExtensions(extensions));
                result._token = generateCash(prefix, requiredBits, md);
                break;

            default:
                throw new IllegalArgumentException("Only supported versions are 0 and 1");
        }

        md.reset();
        md.update(result._token.getBytes());
        result._digest = md.digest();
        return result;
    }

    /**
     * Mints a  HashCash
     * @param resource the string to be encoded in the HashCash
     * @param extensions Extra data to be encoded in the HashCash
     * @param version Which version to mint.  Only valid values are 0 and 1
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, Instant instant, int requiredBits, int version)
throws NoSuchAlgorithmException {
        return mintCash(resource, extensions, instant, requiredBits, version, "SHA1");
    }


    private Instant parseDate(String dateString) {
        if (dateString != null) {
            try {
                // try each date format starting with the most common one
                for (DateTimeFormatter format : FORMATS){
                    try {
                      return Instant.from(format.parse(dateString));
                    }
                    catch(DateTimeParseException ex){ /* gulp */ }
                }
            }
            catch (Exception e) {
                return null;
            }
        }
        return null;
    }


    // Accessors
    /**
     * Two objects are considered equal if they are both of type HashCash and have an identical string representation
     */
    public boolean equals(Object obj) {
        if(obj instanceof HashCash)
            return toString().equals(obj.toString());
        else
            return super.equals(obj);
    }

    /**
     * Returns the canonical string representation of the HashCash
     */
    public String toString() {
        return _token;
    }

    /**
     * Extra data encoded in the HashCash
     */
    public Map<String, List<String> > getExtensions() {
        return _extensions;
    }

    /**
     * The primary resource being protected
     */
    public String getResource() {
        return _resource;
    }

    /**
     * The minting date
     */
    public Instant getDate() {
        return _instant;
    }

    /**
     * The value of the HashCash (e.g. how many leading zero bits it has)
     */
    public int getComputedBits() {
        return _computedBits;
    }

    /**
     * The computed digest
     */
    public byte[] getDigest() {
        return _digest;
    }

    public int getClaimedBits() {
        return _claimedBits;
    }

    /**
     * Which version of HashCash is used here
     */
    public int getVersion() {
        return _version;
    }

    // Private utility functions
    /**
     * Actually tries various combinations to find a valid hash.  Form is of prefix + random_hex + ":" + random_hex
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    private static String generateCash(String prefix, int requiredBits, MessageDigest md)
        throws NoSuchAlgorithmException {
        SecureRandom rnd = SecureRandom.getInstance("SHA1PRNG");
        byte[] tmpBytes = new byte[8];
        rnd.nextBytes(tmpBytes);
        long random = bytesToLong(tmpBytes);
        rnd.nextBytes(tmpBytes);
        long counter = bytesToLong(tmpBytes);

        prefix = prefix + Long.toHexString(random) + ":";

        String temp;
        int actualBits;
        byte[] bArray;
        do {
            counter++;
            temp = prefix + Long.toHexString(counter);
            md.reset();
            md.update(temp.getBytes());
            bArray = md.digest();
            actualBits = numberOfLeadingZeros(bArray);
        } while ( actualBits < requiredBits);

        return temp;
    }

    /**
     * Converts a 8 byte array of unsigned bytes to an long
     * @param b an array of 8 unsigned bytes
     */
    private static long bytesToLong(byte[] b) {
        long l = 0;
        l |= b[0] & 0xFF;
        l <<= 8;
        l |= b[1] & 0xFF;
        l <<= 8;
        l |= b[2] & 0xFF;
        l <<= 8;
        l |= b[3] & 0xFF;
        l <<= 8;
        l |= b[4] & 0xFF;
        l <<= 8;
        l |= b[5] & 0xFF;
        l <<= 8;
        l |= b[6] & 0xFF;
        l <<= 8;
        l |= b[7] & 0xFF;
        return l;
    }

    /**
     * Serializes the extensions with (key, value) seperated by semi-colons and values seperated by commas
     */
    private static String serializeExtensions(Map<String, List<String> > extensions) {
        if(null == extensions || extensions.isEmpty())
            return "";

        StringBuffer result = new StringBuffer();
        List<String> tempList;
        boolean first = true;

        for(String key: extensions.keySet()) {
            if(key.contains(":") || key.contains(";") || key.contains("="))
                throw new IllegalArgumentException("Extension key contains an illegal character. " + key);
            if(!first)
                result.append(";");
            first = false;
            result.append(key);
            tempList = extensions.get(key);

            if(null != tempList) {
                result.append("=");
                for(int i = 0; i < tempList.size(); i++) {
                    if(tempList.get(i).contains(":") || tempList.get(i).contains(";") || tempList.get(i).contains(","))
                        throw new IllegalArgumentException("Extension value contains an illegal character. " + tempList.get(i));
                    if(i > 0)
                        result.append(",");
                    result.append(tempList.get(i));
                }
            }
        }
        return result.toString();
    }

    /**
     * Inverse of {@link #serializeExtensions(Map)}
     */
    private static Map<String, List<String> > deserializeExtensions(String extensions) {
        Map<String, List<String> > result = new HashMap<String, List<String> >();
        if(null == extensions || extensions.length() == 0)
            return result;

        String[] items = extensions.split(";");

        for(int i = 0; i < items.length; i++) {
            String[] parts = items[i].split("=", 2);
            if(parts.length == 1)
                result.put(parts[0], null);
            else
                result.put(parts[0], Arrays.asList(parts[1].split(",")));
        }

        return result;
    }

    /**
     * Counts the number of leading zeros in a byte array.
     */
    private static int numberOfLeadingZeros(byte[] values) {
        int result = 0;
        int temp = 0;
        for(int i = 0; i < values.length; i++) {

            temp = numberOfLeadingZeros(values[i]);

            result += temp;
            if(temp != 8)
                break;
        }

        return result;
    }

    /**
     * Returns the number of leading zeros in a bytes binary represenation
     */
    private static int numberOfLeadingZeros(byte value) {
        if(value < 0)
            return 0;
        if(value < 0x01)
            return 8;
        if (value < 0x02)
            return  7;
        if (value < 0x04)
            return 6;
        if (value < 0x08)
            return 5;
        if (value < 0x10)
            return 4;
        if (value < 0x20)
            return 3;
        if (value < 0x40)
            return 2;
        if (value < 0x80)
            return 1;
        return 0;
    }

    /**
     * Estimates how many milliseconds it would take to mint a cash of the specified value.
     * <ul>
     * <li>NOTE1: Minting time can vary greatly in fact, half of the time it will take half as long)
     * <li>NOTE2: The first time that an estimation function is called it is expensive (on the order of seconds).  After that, it is very quick.
     * </ul>
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static long estimateTime(int value) throws NoSuchAlgorithmException {
        initEstimates();
        return (long)(milliFor16 * Math.pow(2, value - 16));
    }

    /**
     * Estimates what value (e.g. how many bits of collision) are required for the specified  length of time.
     * <ul>
     * <li>NOTE1: Minting time can vary greatly in fact, half of the time it will take half as long)
     * <li>NOTE2: The first time that an estimation function is called it is expensive (on the order of seconds).  After that, it is very quick.
     * </ul>
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public static int estimateValue(int secs) throws NoSuchAlgorithmException  {
        initEstimates();
        int result = 0;
        long millis = secs * 1000 * 65536;
        millis /= milliFor16;

        while(millis > 1) {
            result++;
            millis /= 2;
        }

        return result;
    }

    /**
     * Seeds the estimates by determining how long it takes to calculate a 16bit collision on average.
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    private static void initEstimates() throws NoSuchAlgorithmException {
        if(milliFor16 == -1) {
            long duration;
            duration = Calendar.getInstance().getTimeInMillis();
            for(int i = 0; i < 11; i++) {
                mintCash("estimation", 16);
            }
            duration = Calendar.getInstance().getTimeInMillis() - duration;
            milliFor16 = (duration /10);
        }
    }

    /**
     * Compares the value of two HashCashes
     * @param other
     * @see java.lang.Comparable#compareTo(Object)
     */
    public int compareTo(HashCash other) {
        if(null == other)
            throw new NullPointerException();

        return Integer.valueOf(getComputedBits()).compareTo(Integer.valueOf(other.getComputedBits()));
    }
}

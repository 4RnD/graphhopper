/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.search;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Storable;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Peter Karich
 */
public class StringIndex implements Storable<StringIndex> {
    private static final long EMPTY_POINTER = 0, START_POINTER = 1;
    // store size of byte array into *one* byte and use maximum value to indicate reference (see duplicate handling)
    private static final int MAX_LENGTH = 254;
    // special length for duplicate handling
    private static final int DUP_MARKER = MAX_LENGTH + 1;
    // store key count in one byte
    static final int MAX_UNIQUE_KEYS = 255;
    boolean throwExceptionIfTooLong = false;
    private final DataAccess keys;
    // storage layout per entry:
    // vals count, val_length_0, val_0, val_length_1, val_1 ...
    // Drawback: we need to loop through the entries to get the start of val_x
    // Note, that we store duplicate values via an absolute reference (64 bytes) in smallCache and use negative val_length as marker DUP_MARKER
    private final DataAccess vals;
    // array.indexOf could be faster than hashmap.get if not too many keys
    private final Map<String, Integer> keysInMem = new LinkedHashMap<>();
    private final Map<String, Long> smallCache;
    private long bytePointer = START_POINTER;

    public StringIndex(Directory dir) {
        this(dir, 1000);
    }

    /**
     * Specify a larger cacheSize to reduce disk usage. Note that this increases the memory usage of this object.
     */
    public StringIndex(Directory dir, final int cacheSize) {
        keys = dir.find("string_index_keys");
        vals = dir.find("string_index_vals");
        smallCache = new LinkedHashMap<String, Long>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> entry) {
                return size() > cacheSize;
            }
        };
    }

    @Override
    public StringIndex create(long initBytes) {
        keys.create(initBytes);
        vals.create(initBytes);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (vals.loadExisting()) {
            if (!keys.loadExisting())
                throw new IllegalStateException("Loaded values but cannot load keys");
            bytePointer = BitUtil.LITTLE.combineIntsToLong(vals.getHeader(0), vals.getHeader(4));

            // load keys into memory
            int count = keys.getShort(0);
            long keyBytePointer = 2;
            for (int i = 0; i < count; i++) {
                int keyLength = keys.getShort(keyBytePointer);
                keyBytePointer += 2;

                byte[] keyBytes = new byte[keyLength];
                keys.getBytes(keyBytePointer, keyBytes, keyLength);
                keysInMem.put(new String(keyBytes, Helper.UTF_CS), keysInMem.size());
                keyBytePointer += keyLength;
            }
            return true;
        }

        return false;
    }

    Set<String> getKeys() {
        return keysInMem.keySet();
    }

    /**
     * This method writes the specified key-value pairs into the storage.
     *
     * @return entryPointer to later fetch the entryMap via get
     */
    public long add(Map<String, String> entryMap) {
        if (entryMap.isEmpty())
            return EMPTY_POINTER;

        long oldPointer = bytePointer;
        // while adding there could be exceptions and we need to avoid that the bytePointer is modified
        long currentPointer = bytePointer;

        // set only 1 byte for key count but ensure two as we cal DataAccess.setShort
        vals.ensureCapacity(currentPointer + 2);
        if (entryMap.size() > 127)
            throw new IllegalArgumentException("Cannot store more than 127 entries per entry");

        vals.setShort(currentPointer, (byte) entryMap.size());
        currentPointer += 1;
        for (Map.Entry<String, String> entry : entryMap.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            Integer keyIndex = keysInMem.get(key);
            if (keyIndex == null) {
                keyIndex = keysInMem.size();
                if (keyIndex + 1 > MAX_UNIQUE_KEYS)
                    throw new IllegalArgumentException("Cannot store more than " + MAX_UNIQUE_KEYS + " unique keys");
                keysInMem.put(key, keyIndex);
            }

            if (value == null || value.isEmpty()) {
                vals.ensureCapacity(currentPointer + 2);
                vals.setShort(currentPointer, (short) (0 | keyIndex));
            } else {
                Long existingRef = smallCache.get(value);
                if (existingRef != null) {
                    vals.ensureCapacity(currentPointer + 1 + 8);
                    // length of reference is 8 byte => store special case of valueBytes.length == 0
                    vals.setShort(currentPointer, (short) (DUP_MARKER << 8 | keyIndex));
                    currentPointer += 2;
                    vals.setInt(currentPointer, BitUtil.LITTLE.getIntLow(existingRef));
                    vals.setInt(currentPointer + 4, BitUtil.LITTLE.getIntHigh(existingRef));
                    currentPointer += 8;
                } else {
                    byte[] valueBytes = getBytesForString("Value for key" + key, value);
                    // only store value to cache if storing via DUP_MARKER is valuable (costs 8 bytes for the long reference)
                    if (valueBytes.length > 8)
                        smallCache.put(value, currentPointer);

                    vals.ensureCapacity(currentPointer + 2 + valueBytes.length);
                    vals.setShort(currentPointer, (short) ((valueBytes.length << 8) | keyIndex));
                    currentPointer += 2;
                    vals.setBytes(currentPointer, valueBytes, valueBytes.length);
                    currentPointer += valueBytes.length;
                }
            }
        }
        bytePointer = currentPointer;
        return oldPointer;
    }

    public String get(final long entryPointer, String key) {
        if (entryPointer < 0)
            throw new IllegalStateException("Pointer to access StringIndex cannot be negative:" + entryPointer);

        if (entryPointer == EMPTY_POINTER)
            return "";

        int keyCount = vals.getShort(entryPointer) & 0xFF;
        if (keyCount == 0)
            return null;

        Integer keyIndex = keysInMem.get(key);
        // specified key is not known to the StringIndex
        if (keyIndex == null)
            return null;

        long tmpPointer = entryPointer + 1;
        for (int i = 0; i < keyCount; i++) {
            short keyIndexAndValueLength = vals.getShort(tmpPointer);
            int currentKeyIndex = keyIndexAndValueLength & 0xFF;
            int valueLength = (keyIndexAndValueLength >> 8) & 0xFF;
            tmpPointer += 2;
            if (currentKeyIndex == keyIndex) {
                if (valueLength == DUP_MARKER) {
                    tmpPointer = BitUtil.LITTLE.combineIntsToLong(vals.getInt(tmpPointer), vals.getInt(tmpPointer + 4));
                    if (tmpPointer > bytePointer)
                        throw new IllegalStateException("dup marker " + bytePointer + " should exist but points into not yet allocated area " + tmpPointer);
                    valueLength = (vals.getShort(tmpPointer) >> 8) & 0xFF;
                    tmpPointer += 2;
                }

                byte[] stringBytes = new byte[valueLength];
                vals.getBytes(tmpPointer, stringBytes, stringBytes.length);
                return new String(stringBytes, Helper.UTF_CS);
            }
            tmpPointer += valueLength;
        }

        // value for specified key does not existing for the specified pointer
        return null;
    }

    private byte[] getBytesForString(String info, String name) {
        byte[] bytes = name.getBytes(Helper.UTF_CS);
        if (bytes.length > MAX_LENGTH) {
            String newString = new String(bytes, 0, MAX_LENGTH, Helper.UTF_CS);
            if (throwExceptionIfTooLong)
                throw new IllegalStateException(info + " is too long: " + name + " truncated to " + newString);
            return newString.getBytes(Helper.UTF_CS);
        }

        return bytes;
    }

    @Override
    public void flush() {
        // store: key count, length_0, length_1, ... length_n-1, key_0, key_1, ...
        keys.ensureCapacity(2);
        keys.setShort(0, (short) keysInMem.size());
        long keyBytePointer = 2;
        for (String key : keysInMem.keySet()) {
            byte[] keyBytes = getBytesForString("key", key);
            keys.ensureCapacity(keyBytePointer + 2 + keyBytes.length);
            keys.setShort(keyBytePointer, (short) keyBytes.length);
            keyBytePointer += 2;

            keys.setBytes(keyBytePointer, keyBytes, keyBytes.length);
            keyBytePointer += keyBytes.length;
        }
        keys.flush();

        vals.setHeader(0, BitUtil.LITTLE.getIntLow(bytePointer));
        vals.setHeader(4, BitUtil.LITTLE.getIntHigh(bytePointer));
        vals.flush();
    }

    @Override
    public void close() {
        keys.close();
        vals.close();
    }

    @Override
    public boolean isClosed() {
        return vals.isClosed() && keys.isClosed();
    }

    public void setSegmentSize(int segments) {
        keys.setSegmentSize(segments);
        vals.setSegmentSize(segments);
    }

    @Override
    public long getCapacity() {
        return vals.getCapacity() + keys.getCapacity();
    }

    public void copyTo(StringIndex stringIndex) {
        keys.copyTo(stringIndex.keys);
        vals.copyTo(stringIndex.vals);
    }
}

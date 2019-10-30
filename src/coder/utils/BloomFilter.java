package coder.utils;

import java.util.concurrent.atomic.AtomicLongArray;

public class BloomFilter {

    private static final int LONG_ADDRESSABLE_BITS = 6;
    public final AtomicLongArray bits;
    private final int size;
    private final int numHashFunctions;

    public BloomFilter(long bitSize, int numHashFunctions) { // 1<<28
        size = (int) (bitSize >>> LONG_ADDRESSABLE_BITS) + 1;
        bits = new AtomicLongArray(size);
        this.numHashFunctions = numHashFunctions;
    }

    public boolean put(long hash) {
        long bitSize = size << LONG_ADDRESSABLE_BITS;
        int hash1 = (int) hash;
        int hash2 = (int) (hash >>> 32);
        boolean bitsChanged = false;
        for (int i = 1; i <= numHashFunctions; i++) {
            int combinedHash = hash1 + (i * hash2);
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            bitsChanged |= set(combinedHash % bitSize);
        }
        return bitsChanged;
    }

    public boolean mightContain(long hash) {
        long bitSize = size << LONG_ADDRESSABLE_BITS;
        int hash1 = (int) hash;
        int hash2 = (int) (hash >>> 32);
        for (int i = 1; i <= numHashFunctions; i++) {
            int combinedHash = hash1 + (i * hash2);
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            if (!get(combinedHash % bitSize)) {
                return false;
            }
        }
        return true;
    }

    private boolean set(long bitIndex) {
        if (get(bitIndex)) {
            return false;
        }
        int longIndex = (int) (bitIndex >>> LONG_ADDRESSABLE_BITS);
        long mask = 1L << bitIndex;

        long oldValue, newValue;
        do {
            oldValue = bits.get(longIndex);
            newValue = oldValue | mask;
            if (oldValue == newValue) {
                return false;
            }
        } while (!bits.compareAndSet(longIndex, oldValue, newValue));
        return true;
    }

    private boolean get(long bitIndex) {
        return (bits.get((int) (bitIndex >>> LONG_ADDRESSABLE_BITS)) & (1L << bitIndex)) != 0;
    }

    public static long needBits(int n, double falsePRate) {
        return -(long) (n * Math.log(falsePRate) / Math.pow(Math.log(2), 2)) + (1L << LONG_ADDRESSABLE_BITS);
    }

    public static int needNumOfFunction(long m, int n) {
        if (n <= 0) {
            return 1;
        }
        int num = (int) (m / n * Math.log(2));
        return num > 1 ? num : num + 1;
    }
}

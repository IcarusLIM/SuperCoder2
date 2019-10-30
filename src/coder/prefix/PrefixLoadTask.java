package coder.prefix;

import coder.utils.BloomFilter;
import coder.utils.Longs;
import coder.utils.Murmur3Hash;
import coder.utils.Utils;
import coder.utils.io.ByteBufferFileMapper;
import coder.utils.io.MappedBufferReader;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class PrefixLoadTask implements Runnable {

    private static final byte[] EMPTY_STRING = " ".getBytes();
    ByteBufferFileMapper mapper;
    CountDownLatch cdl;
    private long[][] equalAllows;
    private int equalAllowLength;
    private AtomicInteger equalAllowCursor;
    private long[][] equalDenies;
    private int equalDenyLength;
    private AtomicInteger equalDenyCursor;
    private BloomFilter equalFilter;
    private long[][] prefixAllows;
    private int prefixAllowLength;
    private AtomicInteger prefixAllowCursor;
    private long[][] prefixDenies;
    private int prefixDenyLength;
    private AtomicInteger prefixDenyCursor;
    private BloomFilter prefixFilter;
    private Murmur3Hash hashFunction = new Murmur3Hash();

    public PrefixLoadTask(ByteBufferFileMapper mapper, CountDownLatch cdl,
                          long[][] equalAllows, AtomicInteger equalAllowCursor,
                          long[][] equalDenies, AtomicInteger equalDenyCursor,
                          BloomFilter equalFilter,
                          long[][] prefixAllows, AtomicInteger prefixAllowCursor,
                          long[][] prefixDenies, AtomicInteger prefixDenyCursor,
                          BloomFilter prefixFilter) {
        this.mapper = mapper;
        this.cdl = cdl;
        this.equalAllows = equalAllows;
        this.equalAllowLength = equalAllows[0].length;
        this.equalAllowCursor = equalAllowCursor;
        this.equalDenies = equalDenies;
        this.equalDenyLength = equalDenies[0].length;
        this.equalDenyCursor = equalDenyCursor;
        this.equalFilter = equalFilter;
        this.prefixAllows = prefixAllows;
        this.prefixAllowLength = prefixAllows[0].length;
        this.prefixAllowCursor = prefixAllowCursor;
        this.prefixDenies = prefixDenies;
        this.prefixDenyLength = prefixDenies[0].length;
        this.prefixDenyCursor = prefixDenyCursor;
        this.prefixFilter = prefixFilter;
    }

    @Override
    public void run() {
        try {
            MappedBufferReader reader;
            while ((reader = mapper.getReader()) != null) {
//                reader.load();
                String line;
                while ((line = reader.readLine()) != null) {
                    int ft = line.indexOf('\t');
                    int st = line.indexOf('\t', ft + 1);
                    String prefix = line.substring(0, ft);
                    char range = line.charAt(ft + 1);
                    int indicator = line.charAt(st + 1) == '+' ? 1 : -1;
                    if (prefix.startsWith("//")) {
                        String url1 = "http:" + prefix;
                        saveRule(range, indicator, url1);
                        String url2 = "https:" + prefix;
                        saveRule(range, indicator, url2);
                    } else {
                        saveRule(range, indicator, prefix);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        cdl.countDown();
    }

    private void saveRule(char range, int indicator, String url) {
        if (range == '=') {
            long[] md5l = hashFunction.reset().hash(url.getBytes());
            equalFilter.put(md5l[1]);
            if (indicator > 0) {
                int occupyCursor = Utils.increment(equalAllowCursor, 2);
                addRule(equalAllows, equalAllowLength, occupyCursor, md5l);
            } else {
                int occupyCursor = Utils.increment(equalDenyCursor, 2);
                addRule(equalDenies, equalDenyLength, occupyCursor, md5l);
            }
        } else {
            int index = url.lastIndexOf('/');
            hashFunction.reset().put(url.substring(0, index + 1).getBytes());
            prefixFilter.put(hashFunction.mirrorHash()[1]^(long)url.length());
            hashFunction.put(url.substring(index+1).getBytes());
            if (range == '+') { // put extra " "
                hashFunction.put(' ');
            }
            long[] md5l = hashFunction.hash();
            if (indicator > 0) {
                int occupyCursor = Utils.increment(prefixAllowCursor, 2);
                addRule(prefixAllows, prefixAllowLength, occupyCursor, md5l);
            } else {
                int occupyCursor = Utils.increment(prefixDenyCursor, 2);
                addRule(prefixDenies, prefixDenyLength, occupyCursor, md5l);
            }
        }
    }

    public void addRule(long[][] rules, int ruleArrayLength, int cursor, long[] hash) {
        int i1 = cursor / ruleArrayLength;
        cursor = cursor % ruleArrayLength;
        if (rules[i1] == null) {
            synchronized (rules) {
                if (rules[i1] == null) {
                    rules[i1] = new long[ruleArrayLength];
                }
            }
        }
        rules[i1][cursor] = hash[0];
        rules[i1][cursor + 1] = hash[1];
    }
}

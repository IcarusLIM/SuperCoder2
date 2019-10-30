package coder.domain;

import coder.utils.*;
import coder.utils.io.ByteBufferFileMapper;
import coder.utils.io.MappedBufferReader;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class DomainLoadTask implements Runnable {
    private ByteBufferFileMapper mapper;
    private CountDownLatch cdl;
    private long[][] allows;
    private int allowLength;
    private AtomicInteger allowCursor;
    private long[][] denies;
    private int denyLength;
    private AtomicInteger denyCursor;
    private BloomFilter bloomFilter;
    private Murmur3Hash hashFunction = new Murmur3Hash();

    public DomainLoadTask(ByteBufferFileMapper mapper, CountDownLatch cdl,
                          long[][] allows, AtomicInteger allowCursor,
                          long[][] denies, AtomicInteger denyCursor, BloomFilter bloomFilter) {
        this.mapper = mapper;
        this.cdl = cdl;
        this.allows = allows;
        this.allowLength = allows[0].length;
        this.allowCursor = allowCursor;
        this.denies = denies;
        this.denyLength = denies[0].length;
        this.denyCursor = denyCursor;
        this.bloomFilter = bloomFilter;
    }

    @Override
    public void run() {
        try {
            MappedBufferReader reader;
            while ((reader = mapper.getReader()) != null) {
//                reader.load();
                String line;
                while ((line = reader.readLine()) != null) {
//                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int indexOfT = line.indexOf("\t");
                    char permission = line.charAt(indexOfT + 1);
                    String postfix = line.substring(0, indexOfT);
                    if (permission == '-') {
                        int occupyCursor = Utils.increment(denyCursor, 2);
                        addRule(denies, denyLength, occupyCursor, postfix);
                    } else {
                        int occupyCursor = Utils.increment(allowCursor, 2);
                        addRule(allows, allowLength, occupyCursor, postfix);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        cdl.countDown();
    }

    public void addRule(long[][] rules, int ruleArrayLength, int cursor, String domain) {
//        long[] md5l = hashFunction.reset().hash(new String(Utils.reverseChar(domain)).getBytes());
        long[] md5l = hashFunction.reset().hash(Utils.reverseBytes(domain));
        bloomFilter.put(md5l[1]);

        int i1 = cursor / ruleArrayLength;
        cursor = cursor % ruleArrayLength;
        if (rules[i1] == null) {
            synchronized (rules) {
                if (rules[i1] == null) {
                    rules[i1] = new long[ruleArrayLength];
                }
            }
        }
        rules[i1][cursor] = md5l[0];
        rules[i1][cursor + 1] = md5l[1];
    }
}

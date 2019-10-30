package coder.domain;

import coder.AttUrl;
import coder.utils.*;
import coder.utils.io.ByteBufferFileMapper;
import coder.utils.sort.MultiLongArrays;

import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DomainFilter {

    private static final int AVERAGE_RULE_LENGTH = 18;
    private static final int LOAD_THREAD = 9;

    public long[] deny;
    private int[][] denyFastTable = null;
    public long[] allow;
    private int[][] allowFastTable = null;
    private BloomFilter bloomFilter;

    public DomainFilter(String file) {
        File f = new File(file);
        int approximate = (int) (f.length() / AVERAGE_RULE_LENGTH);
        long m = BloomFilter.needBits(approximate, Const.FALSE_POSITIVE_RATE);
        bloomFilter = new BloomFilter(m, BloomFilter.needNumOfFunction(m, approximate));
        try {
            load(file, approximate);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void load(String file, int approximate) throws IOException, InterruptedException {
        AtomicInteger denyCursor = new AtomicInteger(0);
        AtomicInteger allowCursor = new AtomicInteger(0);
        long[][] denies = new long[Const.MAX_RULE_ARRAYS][];
        long[][] allows = new long[Const.MAX_RULE_ARRAYS][];
        denies[0] = new long[(approximate / Const.RULE_SIZE_INIT_SHRINK + 1) * 2];
        allows[0] = new long[((approximate >>> 6) / Const.RULE_SIZE_INIT_SHRINK + 1) * 2];

        CountDownLatch cdl = new CountDownLatch(LOAD_THREAD);
        ByteBufferFileMapper mapper = new ByteBufferFileMapper(file, 512 << 10);
        ExecutorService exe = Executors.newFixedThreadPool(LOAD_THREAD);
        for (int i = 0; i < LOAD_THREAD; i++) {
            exe.execute(new DomainLoadTask(mapper, cdl, allows, allowCursor, denies, denyCursor, bloomFilter));
        }
        cdl.await();
        exe.shutdown();
        deny = LongArrays.mergeArray(denies, denyCursor.get());
        allow = LongArrays.mergeArray(allows, allowCursor.get());
        denies = null;
        allows = null;
        System.gc();
        MultiLongArrays.parallelSort(allow);
        MultiLongArrays.parallelSort(deny);
        initFastTable();
    }

    public void initFastTable() {
        denyFastTable = MultiLongArrays.buildFastSearchTable(deny, Const.DENY_ACCURACY, Const.DENY_MASK);
        allowFastTable = MultiLongArrays.buildFastSearchTable(allow, Const.ALLOW_ACCURACY, Const.ALLOW_MASK);
    }

    public int filter(AttUrl attUrl, Murmur3Hash hashFunction, long[][] work) {
        int indicator = filterDomain(attUrl.domain + ":" + attUrl.port, hashFunction, work);
        if (indicator != 0) return indicator;
        return filterDomain(attUrl.domain, hashFunction, work);
    }

    int filterDomain(String domain, Murmur3Hash hashFunction, long[][] md5ls) {
        byte[] reversedDomain = Utils.reverseBytes(domain);
        int len = reversedDomain.length;
        hashFunction.reset();
        int count = 0;
        for (int i = 0; i < len; ) {
            byte c;
            while (i < len && (c = reversedDomain[i]) != '.') {
                hashFunction.put(c);
                i++;
            }
            if (count + 2 > md5ls.length) {
                md5ls = expandArray(md5ls);
            }
            md5ls[count] = hashFunction.mirrorHash(md5ls[count]);
            count++;
            if (i < len) {
                hashFunction.put('.');
                i++;
                md5ls[count] = hashFunction.mirrorHash(md5ls[count]);
                count++;
            }

        }
        for (int j = count - 1; j >= 0; j--) {
            int indicator = find(md5ls[j]);
            if (indicator != 0) {
                return indicator;
            }
        }
        return 0;
    }

    private int find(long[] md5l) {
        if (bloomFilter.mightContain(md5l[1])) {
            if (MultiLongArrays.binarySearch(deny, md5l, denyFastTable, Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                return -1;
            }
            if (MultiLongArrays.binarySearch(allow, md5l, allowFastTable, Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                return 1;
            }
        }
        return 0;
    }

    public static long[][] expandArray(long[][] src) {
        long[][] dest = new long[src.length * 2][];
        for (int i = 0; i < src.length; i++) {
            dest[i] = src[i];
        }
        for (int i = src.length; i < dest.length; i++) {
            dest[i] = new long[2];
        }
        return dest;
    }
}

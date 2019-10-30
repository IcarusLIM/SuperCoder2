package coder.prefix;


import coder.AttUrl;
import coder.utils.*;
import coder.utils.io.ByteBufferFileMapper;
import coder.utils.sort.MultiLongArrays;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PrefixFilter {
    private static final int AVERAGE_RULE_LENGTH = 48;
    private static final int LOAD_THREAD = 9;

    protected BloomFilter equalFilter;
    public long[] equalAllow;
    public long[] equalDeny;
    protected int[][] equalAllowFastTable = null;
    protected int[][] equalDenyFastTable = null;
    BloomFilter prefixFilter;
    public long[] prefixAllow;
    public long[] prefixDeny;
    protected int[][] prefixAllowFastTale = null;
    protected int[][] prefixDenyFastTable = null;

    public PrefixFilter(String file) {
        File f = new File(file);
        int approximate = (int) (f.length() / AVERAGE_RULE_LENGTH);
        int equal = approximate / 2;
        int prefix = approximate / 2;
        long m = BloomFilter.needBits(equal, Const.FALSE_POSITIVE_RATE);
        equalFilter = new BloomFilter(m, BloomFilter.needNumOfFunction(m, equal));
        m = BloomFilter.needBits(prefix, Const.FALSE_POSITIVE_RATE);
        prefixFilter = new BloomFilter(m, BloomFilter.needNumOfFunction(m, prefix));
        try {
            load(file, equal, prefix);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void load(String file, int equal, int prefix) throws IOException, InterruptedException {
        AtomicInteger equalAllowCursor = new AtomicInteger(0);
        AtomicInteger equalDenyCursor = new AtomicInteger(0);
        AtomicInteger prefixAllowCursor = new AtomicInteger(0);
        AtomicInteger prefixDenyCursor = new AtomicInteger(0);
        long[][] equalAllows = new long[Const.MAX_RULE_ARRAYS][];
        long[][] equalDenies = new long[Const.MAX_RULE_ARRAYS][];
        long[][] prefixAllows = new long[Const.MAX_RULE_ARRAYS][];
        long[][] prefixDenies = new long[Const.MAX_RULE_ARRAYS][];
        equalAllows[0] = new long[((equal >>> 6) / Const.RULE_SIZE_INIT_SHRINK + 1) * 2];
        equalDenies[0] = new long[(equal / Const.RULE_SIZE_INIT_SHRINK + 1) * 2];
        prefixAllows[0] = new long[((prefix >>> 6) / Const.RULE_SIZE_INIT_SHRINK + 1) * 2];
        prefixDenies[0] = new long[(prefix / Const.RULE_SIZE_INIT_SHRINK + 1) * 2];

        ByteBufferFileMapper mapper = new ByteBufferFileMapper(file, 512 << 10);
        ExecutorService exe = Executors.newFixedThreadPool(LOAD_THREAD);
        CountDownLatch cdl = new CountDownLatch(LOAD_THREAD);
        for (int i = 0; i < LOAD_THREAD; i++) {
            exe.execute(new PrefixLoadTask(mapper, cdl,
                    equalAllows, equalAllowCursor, equalDenies, equalDenyCursor, equalFilter,
                    prefixAllows, prefixAllowCursor, prefixDenies, prefixDenyCursor, prefixFilter));
        }
        cdl.await();
        exe.shutdown();
        equalDeny = LongArrays.mergeArray(equalDenies, equalDenyCursor.get());
        prefixDeny = LongArrays.mergeArray(prefixDenies, prefixDenyCursor.get());
        equalAllow = LongArrays.mergeArray(equalAllows, equalAllowCursor.get());
        prefixAllow = LongArrays.mergeArray(prefixAllows, prefixAllowCursor.get());
        equalDenies = null;
        prefixDenies = null;
        equalAllows = null;
        prefixAllows = null;
        System.gc();
        MultiLongArrays.parallelSort(equalAllow);
        MultiLongArrays.parallelSort(equalDeny);
        MultiLongArrays.parallelSort(prefixAllow);
        MultiLongArrays.parallelSort(prefixDeny);
        initFastTable();
    }

    public void initFastTable() {
        equalDenyFastTable = MultiLongArrays.buildFastSearchTable(equalDeny, Const.DENY_ACCURACY, Const.DENY_MASK);
        equalAllowFastTable = MultiLongArrays.buildFastSearchTable(equalAllow, Const.ALLOW_ACCURACY, Const.ALLOW_MASK);
        prefixDenyFastTable = MultiLongArrays.buildFastSearchTable(prefixDeny, Const.DENY_ACCURACY, Const.DENY_MASK);
        prefixAllowFastTale = MultiLongArrays.buildFastSearchTable(prefixAllow, Const.ALLOW_ACCURACY, Const.ALLOW_MASK);
    }

    public int filter(AttUrl attUrl, Murmur3Hash hashFunction) {
        String url = attUrl.url;
        long[] work = new long[2];
        // check equalFilter (mirrorHash will restore hashFunction's origin value)
        long[] md5l = hashFunction.reset().hash(url.getBytes(), work);
        if (equalFilter.mightContain(md5l[1])) {
            if (MultiLongArrays.binarySearch(equalDeny, md5l, equalDenyFastTable, Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                return -1;
            }
            if (MultiLongArrays.binarySearch(equalAllow, md5l, equalAllowFastTable, Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                return 1;
            }
        }

        boolean rightMost = true;
        int firstSlash = attUrl.firstSlash;
        while (true) {
            int index = url.lastIndexOf('/');
            if (index < firstSlash)
                break;
            String subUrl = url.substring(0, index); // without '/'
            hashFunction.reset().put(subUrl.getBytes());
            hashFunction.put('/');
            long tmpHash = hashFunction.mirrorHash()[1];
            for (int i = url.length(); i > index; i--) {
                if (prefixFilter.mightContain(tmpHash ^ (long)i)) {
                    String r = url.substring(index + 1, i);
                    if (!rightMost || i != url.length()) {
                        long[] postFixedHash = hashFunction.mirrorHash((r + " ").getBytes(), work);
                        if (MultiLongArrays.binarySearch(prefixDeny, postFixedHash, prefixDenyFastTable,
                                Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                            return -1;
                        }
                        if (MultiLongArrays.binarySearch(prefixAllow, postFixedHash, prefixAllowFastTale,
                                Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                            return 1;
                        }
                    }
                    long[] hash = hashFunction.mirrorHash(r.getBytes(), work);
                    if (MultiLongArrays.binarySearch(prefixDeny, hash, prefixDenyFastTable, Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                        return -1;
                    }
                    if (MultiLongArrays.binarySearch(prefixAllow, hash, prefixAllowFastTale, Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                        return 1;
                    }
                }
            }
            rightMost = false;
            url = subUrl;
        }
        return 0;
    }

}

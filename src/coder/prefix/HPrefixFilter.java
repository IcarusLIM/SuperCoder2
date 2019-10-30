package coder.prefix;

import coder.AttUrl;
import coder.utils.Const;
import coder.utils.Murmur3Hash;
import coder.utils.sort.MultiLongArrays;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class HPrefixFilter extends PrefixFilter {
    private static ThreadLocal<Status> statusThreadLocal = new ThreadLocal<>();

    public HPrefixFilter(String file) {
        super(file);
    }

    @Override
    public int filter(AttUrl attUrl, Murmur3Hash hashFunction) {
        String url = attUrl.url;
        Status status = statusThreadLocal.get();
        if (status == null) {
            status = new Status();
            statusThreadLocal.set(status);
        }
        if (url.length() > status.length) {
            return super.filter(attUrl, hashFunction);
        }

        long[] md5l = hashFunction.reset().hash(url.getBytes(), new long[2]);
        if (equalFilter.mightContain(md5l[1])) {
            if (MultiLongArrays.binarySearch(equalDeny, md5l, equalDenyFastTable, Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                return -1;
            }
            if (MultiLongArrays.binarySearch(equalAllow, md5l, equalAllowFastTable, Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                return 1;
            }
        }

        String lastUrl = status.url;
        int misMatch = getMisMatch(url, lastUrl);
        status.url = url;
        if (status.start >= misMatch) {
            status.start = url.length();
            status.end = url.length();
        } else if (status.end > misMatch) {
            status.end = misMatch;
        }
        boolean rightMost = true;
        int firstSlash = attUrl.firstSlash;
        while (true) {
            int index = url.lastIndexOf('/');
            if (index < firstSlash)
                break;
            if (index < status.start) {
                Arrays.fill(status.cached, index, status.start, false);
                status.start = index;
            }
            if (url.length() > status.end) {
                Arrays.fill(status.cached, status.end, url.length(), false);
                status.end = url.length();
            }
            String subUrl = url.substring(0, index); // without '/'
            hashFunction.reset().put(subUrl.getBytes());
            hashFunction.put('/');
            long tmpHash = hashFunction.mirrorHash()[1];
            for (int i = url.length(); i > index; i--) {
                boolean might;
                if (!status.cached[i - 1]) {
                    might = prefixFilter.mightContain(tmpHash ^ (long) i);
                    status.mightContain[i - 1] = might;
                    status.cached[i - 1] = true;
                } else {
                    might = status.mightContain[i - 1];
                }
                if (might) {
                    String r = url.substring(index + 1, i);
                    if (!rightMost || i != url.length()) {
                        long[] postFixedHash = hashFunction.mirrorHash((r + " ").getBytes(), new long[2]);
                        if (MultiLongArrays.binarySearch(prefixDeny, postFixedHash, prefixDenyFastTable,
                                Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                            return -1;
                        }
                        if (MultiLongArrays.binarySearch(prefixAllow, postFixedHash, prefixAllowFastTale,
                                Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                            return 1;
                        }
                    }
                    long[] hash = hashFunction.mirrorHash(r.getBytes(), new long[2]);
                    if (MultiLongArrays.binarySearch(prefixDeny, hash, prefixDenyFastTable, Const.DENY_ACCURACY, Const.DENY_MASK) >= 0) {
                        return -1;
                    }
                    if (MultiLongArrays.binarySearch(prefixAllow, hash, prefixAllowFastTale, Const.ALLOW_ACCURACY, Const.ALLOW_MASK) >= 0) {
                        return 1;
                    }
                    status.mightContain[i-1] = false;
                }
            }
            rightMost = false;
            url = subUrl;
        }
        return 0;
    }

    private static class Status {
        public String url;
        public int start, end;
        public int length;
        public boolean[] mightContain;
        public boolean[] cached;

        public Status() {
            url = "";
            start = Integer.MAX_VALUE;
            end = Integer.MIN_VALUE;
            length = Const.WORK_ARRAY_BASIC_LENGTH * 2;
            mightContain = new boolean[length];
            cached = new boolean[length];
        }
    }

    private int getMisMatch(String s1, String s2) {
        int s1Len = s1.length();
        int s2Len = s2.length();
        int len = s1Len < s2Len ? s1Len : s2Len;
        for (int i = 0; i < len; i++) {
            if (s1.charAt(i) != s2.charAt(i))
                return i;
        }
        return len;
    }
}

package coder.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class Utils {

    public static String[] reverseArray(String[] ss) {
        for (int i = 0; i < ss.length >> 1; i++) {
            String tmp = ss[i];
            ss[i] = ss[ss.length - 1 - i];
            ss[ss.length - 1 - i] = tmp;
        }
        return ss;
    }

    public static int increment(AtomicInteger i, int increase) {
        while (true) {
            int value = i.get();
            if (i.compareAndSet(value, value + increase)) {
                return value;
            }
        }
    }

    public static char[] reverseChar(String s) {
        char[] cs = s.toCharArray();
        int last = cs.length - 1;
        int mid = cs.length / 2;
        for (int i = 0; i < mid; i++) {
            char c = cs[i];
            cs[i] = cs[last - i];
            cs[last - i] = c;
        }
        return cs;
    }

    public static byte[] reverseBytes(String s) {
        byte[] bytes = s.getBytes();
        int last = bytes.length - 1;
        int mid = bytes.length / 2;
        for (int i = 0; i < mid; i++) {
            byte c = bytes[i];
            bytes[i] = bytes[last - i];
            bytes[last - i] = c;
        }
        return bytes;
    }

}

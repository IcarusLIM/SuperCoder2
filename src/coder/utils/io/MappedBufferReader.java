package coder.utils.io;

import java.nio.MappedByteBuffer;

/**
 * read line, not decode
 */
public class MappedBufferReader {
    private static final int workLen = 40;
    private String remains;
    private MappedByteBuffer buffer;
    private char[] work;
    private boolean hasRemain;

    public MappedBufferReader(MappedByteBuffer buffer, String remains) {
        this.remains = remains;
        this.hasRemain = remains == null ? false : true;
        this.buffer = buffer;
        work = new char[workLen];
    }

    public void load() {
        buffer.load();
    }

    public String readLine() {
        for (int i = 0; i < workLen; i++) {
            if (buffer.hasRemaining()) {
                int c = buffer.get();
                if (c == '\n') {
                    return new String(work, 0, i);
                } else {
                    work[i] = (char) c;
                }
            } else {
                String s = new String(work, 0, i);
                if (hasRemain) {
                    hasRemain = false;
                    return s.length() + remains.length() > 0 ? s + remains : null;
                } else {
                    return s.length() > 0 ? s : null;
                }
            }
        }
        StringBuilder sb = new StringBuilder(new String(work));
        while (true) {
            if (buffer.hasRemaining()) {
                int c = buffer.get();
                if (c == '\n') {
                    return sb.toString();
                } else {
                    sb.append((char) c);
                }
            } else {
                if (hasRemain) {
                    hasRemain = false;
                    sb.append(remains);
                }
                return sb.toString();
            }
        }
    }

    public static String readLine(MappedByteBuffer buffer, char[] work) {
        int len = work.length;
        for (int i = 0; i < len; i++) {
            int c;
            if (!buffer.hasRemaining() || (c = buffer.get()) == '\n') {
                String s = new String(work, 0, i);
                return s.length() > 0 ? s : null;
            }
            work[i] = (char) c;
        }
        // String longer than work
        StringBuilder sb = new StringBuilder(new String(work));
        int c;
        while (buffer.hasRemaining() && (c = buffer.get()) != '\n') {
            sb.append((char) c);
        }
        return sb.toString();
    }
}

package coder.utils;

public class Murmur3Hash {
    private static final int CHUNK_SIZE = 16;
    private static final int LONG_SIZE = 8;
    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    private byte[] remain = new byte[CHUNK_SIZE];
    private int remainIndex;
    private long h1;
    private long h2;
    private int length;

    public long[] hash() {
        getHash();
        return new long[]{h1, h2};
    }

    public long[] hash(long[] work) {
        getHash();
        if (work == null) {
            work = new long[2];
        }
        work[0] = h1;
        work[1] = h2;
        return work;
    }

    public long[] hash(byte[] bs) {
        put(bs);
        return hash();
    }

    public long[] hash(byte[] bs, long[] work) {
        put(bs);
        return hash(work);
    }

    private void getHash() {
        processRemaining();
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;
    }

    public long[] mirrorHash() {
        long tmpH1 = h1;
        long tmpH2 = h2;
        int tmpLength = length;
        long[] hashCode = hash();
        h1 = tmpH1;
        h2 = tmpH2;
        length = tmpLength;
        return hashCode;
    }

    public long[] mirrorHash(long[] work) {
        long tmpH1 = h1;
        long tmpH2 = h2;
        int tmpLength = length;
        long[] hashCode = hash(work);
        h1 = tmpH1;
        h2 = tmpH2;
        length = tmpLength;
        return hashCode;
    }

    public long[] mirrorHash(byte[] bs, long[] work) {
        byte[] tmpRemain = null;
        if (remainIndex + bs.length > CHUNK_SIZE) { // remain array will be override
            tmpRemain = new byte[CHUNK_SIZE];
            System.arraycopy(remain, 0, tmpRemain, 0, CHUNK_SIZE);
        }
        int tmpRemainIndex = remainIndex;
        long tmpH1 = h1;
        long tmpH2 = h2;
        int tmpLength = length;
        put(bs);
        long[] hashCode = mirrorHash(work);
        if (tmpRemain != null) {
            remain = tmpRemain;
        }
        remainIndex = tmpRemainIndex;
        h1 = tmpH1;
        h2 = tmpH2;
        length = tmpLength;
        return hashCode;
    }

    public void put(byte b) {
        remain[remainIndex] = b;
        remainIndex++;
        if (remainIndex == CHUNK_SIZE) {
            process();
        }
    }

    public void put(long value) { // Little endian
        for (int i = 0; i < LONG_SIZE; i++) {
            put((byte) (value & 0xffL));
            value >>= 8;
        }
    }

    public void put(byte[] bs) {
        int bl = bs.length;
        if (bl < CHUNK_SIZE) {
            for (int i = 0; i < bl; i++) {
                put(bs[i]);
            }
        } else {
            int cursor = 0;
            if (remainIndex != 0) {
                while (remainIndex != CHUNK_SIZE) {
                    remain[remainIndex] = bs[cursor];
                    remainIndex++;
                    cursor++;
                }
                process();
            }
            while ((bl - cursor) >= CHUNK_SIZE) {
                long k1 = Longs.fromBytes(bs[cursor], bs[cursor + 1], bs[cursor + 2], bs[cursor + 3],
                        bs[cursor + 4], bs[cursor + 5], bs[cursor + 6], bs[cursor + 7]);
                cursor += 8;
                long k2 = Longs.fromBytes(bs[cursor], bs[cursor + 1], bs[cursor + 2], bs[cursor + 3],
                        bs[cursor + 4], bs[cursor + 5], bs[cursor + 6], bs[cursor + 7]);
                cursor += 8;
                process(k1, k2);
            }
            while (cursor < bl) {
                remain[remainIndex] = bs[cursor];
                remainIndex++;
                cursor++;
            }
        }
    }

    public void put(char c) {
        if (c <= 0x7F) {
            put((byte) c);
        } else {
            put(Character.toString(c).getBytes());
        }
    }

    public Murmur3Hash reset() {
        remainIndex = 0;
        h1 = 0;
        h2 = 0;
        length = 0;
        return this;
    }

    public void process() {
        long k1 = Longs.fromBytes(remain[0], remain[1], remain[2], remain[3], remain[4], remain[5], remain[6], remain[7]);
        long k2 = Longs.fromBytes(remain[8], remain[9], remain[10], remain[11], remain[12], remain[13], remain[14], remain[15]);
        process(k1, k2);
        remainIndex = 0;
    }

    private void process(long k1, long k2) {
        h1 ^= mixK1(k1);

        h1 = Long.rotateLeft(h1, 27);
        h1 += h2;
        h1 = h1 * 5 + 0x52dce729;

        h2 ^= mixK2(k2);

        h2 = Long.rotateLeft(h2, 31);
        h2 += h1;
        h2 = h2 * 5 + 0x38495ab5;

        length += CHUNK_SIZE;
    }

    private static long mixK1(long k1) {
        k1 *= C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= C2;
        return k1;
    }

    private static long mixK2(long k2) {
        k2 *= C2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= C1;
        return k2;
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    private void processRemaining() {
        if (remainIndex == 0) return;
        long k1 = 0;
        long k2 = 0;
        length += remainIndex;
        switch (remainIndex) {
            case 15:
                k2 ^= (long) toInt(remain[14]) << 48; // fall through
            case 14:
                k2 ^= (long) toInt(remain[13]) << 40; // fall through
            case 13:
                k2 ^= (long) toInt(remain[12]) << 32; // fall through
            case 12:
                k2 ^= (long) toInt(remain[11]) << 24; // fall through
            case 11:
                k2 ^= (long) toInt(remain[10]) << 16; // fall through
            case 10:
                k2 ^= (long) toInt(remain[9]) << 8; // fall through
            case 9:
                k2 ^= (long) toInt(remain[8]); // fall through
            case 8:
                k1 ^= Longs.fromBytes(remain[0], remain[1], remain[2], remain[3], remain[4], remain[5], remain[6], remain[7]);
                break;
            case 7:
                k1 ^= (long) toInt(remain[6]) << 48; // fall through
            case 6:
                k1 ^= (long) toInt(remain[5]) << 40; // fall through
            case 5:
                k1 ^= (long) toInt(remain[4]) << 32; // fall through
            case 4:
                k1 ^= (long) toInt(remain[3]) << 24; // fall through
            case 3:
                k1 ^= (long) toInt(remain[2]) << 16; // fall through
            case 2:
                k1 ^= (long) toInt(remain[1]) << 8; // fall through
            case 1:
                k1 ^= (long) toInt(remain[0]);
                break;
            default:
                throw new AssertionError("Should never get here.");
        }
        h1 ^= mixK1(k1);
        h2 ^= mixK2(k2);
    }

    public static int toInt(byte value) {
        return value & 0xFF;
    }
}

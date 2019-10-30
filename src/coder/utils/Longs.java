package coder.utils;

public class Longs {

    public static long fromBytes(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        return fromBytesBigEndian(b8, b7, b6, b5, b4, b3, b2, b1);
    }

    public static long fromBytesBigEndian(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        return (b1 & 0xFFL) << 56
                | (b2 & 0xFFL) << 48
                | (b3 & 0xFFL) << 40
                | (b4 & 0xFFL) << 32
                | (b5 & 0xFFL) << 24
                | (b6 & 0xFFL) << 16
                | (b7 & 0xFFL) << 8
                | (b8 & 0xFFL);
    }

    public static byte[] toBytes(long value) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (value & 0xFFL);
            value >>>= 8;
        }
        return bytes;
    }

}

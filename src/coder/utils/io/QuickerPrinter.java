package coder.utils.io;

import java.io.*;

public class QuickerPrinter {
    private static QuickerPrinter printer = new QuickerPrinter();
    private OutputStream out;

    private QuickerPrinter() {
//        out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 512 << 10);
        out = new BufferedOutputStream(System.out, 512 << 10);
    }

    public static QuickerPrinter getPrinter() {
        return printer;
    }

    synchronized public void print(String[] ss) throws IOException {
        for (int i = 0; i < ss.length; i++) {
            String s = ss[i];
            if (s == null)
                break;
            out.write(s.getBytes());
            out.write('\n');
        }
    }

    synchronized public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

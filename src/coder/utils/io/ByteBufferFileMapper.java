package coder.utils.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ByteBufferFileMapper {
    private FileChannel ic;
    private long bufferSize;
    private MappedByteBuffer buffer;
    private long fileSize;
    private int part;

    public ByteBufferFileMapper(String filePath, int bufferSize) throws IOException {
        this.bufferSize = bufferSize;
        File file = new File(filePath);
        fileSize = file.length();
        ic = new FileInputStream(file).getChannel();
        buffer = ic.map(FileChannel.MapMode.READ_ONLY, 0, nextSize());
        buffer.load();
        part = 1;
    }

    synchronized public MappedBufferReader getReader() throws IOException {
        // read over
        if(buffer==null){
            return null;
        }
        // last buffer remain
        long nextSize = nextSize();
        if (nextSize <= 0) {
            MappedBufferReader reader = new MappedBufferReader(buffer, null);
            buffer = null;
            return reader;
        }
        // has bytes not mapped
        MappedByteBuffer nextBuffer = ic.map(FileChannel.MapMode.READ_ONLY, bufferSize * part, nextSize);
        nextBuffer.load();
        part++;
        String remains = MappedBufferReader.readLine(nextBuffer, new char[40]);
        MappedBufferReader reader = new MappedBufferReader(buffer, remains);
        buffer = nextBuffer;
        return reader;
    }

    private long nextSize() {
        long base = part * bufferSize;
        long remain = fileSize - base;
        return remain > bufferSize ? bufferSize : remain;
    }
}

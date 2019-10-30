package coder;

import coder.domain.DomainFilter;
import coder.prefix.PrefixFilter;
import coder.utils.Const;
import coder.utils.Murmur3Hash;
import coder.utils.io.ByteBufferFileMapper;
import coder.utils.io.MappedBufferReader;
import coder.utils.io.QuickerPrinter;

import java.io.IOException;
import java.util.concurrent.Callable;

public class FilterTask implements Callable<CountItem> {

    private ByteBufferFileMapper mapper;
    private DomainFilter domainFilter;
    private PrefixFilter prefixFilter;
    private CountItem info = new CountItem();
    private QuickerPrinter printer = QuickerPrinter.getPrinter();
    private int cursor = 0;
    private String[] buffer = new String[Const.BUFFERED_LINES];

    public FilterTask(ByteBufferFileMapper mapper, DomainFilter domainFilter, PrefixFilter prefixFilter) {
        this.mapper = mapper;
        this.domainFilter = domainFilter;
        this.prefixFilter = prefixFilter;
    }

    @Override
    public CountItem call() {
        Murmur3Hash hashFunction = new Murmur3Hash();
        long[][] work1 = new long[Const.WORK_ARRAY_BASIC_LENGTH][];
        for (int i = 0; i < Const.WORK_ARRAY_BASIC_LENGTH; i++) {
            work1[i] = new long[2];
        }
        try {
            MappedBufferReader reader;
            while ((reader = mapper.getReader()) != null) {
                String s;
                while ((s = reader.readLine()) != null) {
//                    if (s.isEmpty() || s.startsWith("#")) continue;
                    AttUrl u = AttUrl.newAndCheck(s);
                    if (u == null) continue;
                    int retCode = domainFilter.filter(u, hashFunction, work1);
                    if (retCode != -1) {
                        int oldRetCode = retCode;
                        retCode = prefixFilter.filter(u, hashFunction);
                        if (retCode == 0) {
                            retCode = oldRetCode;
                        }
                        if (retCode == 0) {
                            info.count_of_nohit += 1;
                            retCode = 1;
                        }
                    }
                    if (retCode == 1) {
                        info.count_of_allowed += 1;
                        long value = Long.parseLong(u.strValue, 16);
                        info.xor_of_allowed_value ^= value;
                        print(u.strValue);
                    } else if (retCode == -1) {
                        info.count_of_disallowed += 1;
                        info.xor_of_disallowed_value ^= Long.parseLong(u.strValue, 16);
                    }
                }
            }
            printer.print(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return info;
    }

    public void print(String s) throws IOException {
        if (cursor == Const.BUFFERED_LINES) {
            printer.print(buffer);
            buffer = new String[Const.BUFFERED_LINES];
            cursor = 0;
        }
        buffer[cursor] = s;
        cursor++;
    }
}

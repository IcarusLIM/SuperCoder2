package coder;

import coder.domain.DomainFilter;
import coder.domain.HDomainFilter;
import coder.prefix.HPrefixFilter;
import coder.prefix.PrefixFilter;
import coder.utils.Const;
import coder.utils.io.ByteBufferFileMapper;
import coder.utils.io.QuickerPrinter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    private static final int THREAD_POOL_SIZE = 9;

    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {

        Long start = System.currentTimeMillis();

        File urls = new File(args[2]);
        if (urls.length() < 276000000l) {
            Const.DENY_ACCURACY = Const.ALLOW_ACCURACY;
            Const.DENY_MASK = Const.ALLOW_MASK;
            Const.FALSE_POSITIVE_RATE = 0.05;
        }

        DomainFilter domainFilter;
        if (args[3].equals("H") || args[3].equals("S")) {
            domainFilter = new HDomainFilter(args[0]);

        } else {
            domainFilter = new DomainFilter(args[0]);
        }
        PrefixFilter prefixFilter;
        if (args[3].equals("S")) {
            prefixFilter = new HPrefixFilter(args[1]);
        } else {
            prefixFilter = new PrefixFilter(args[1]);
        }

        int count_of_allowed = 0;
        int count_of_disallowed = 0;
        int count_of_nohit = 0;
        long xor_of_allowed_value = 0;
        long xor_of_disallowed_value = 0;

        ByteBufferFileMapper mapper = new ByteBufferFileMapper(args[2], 512 << 10);
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<CountItem>> result = new ArrayList<>();
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            result.add(threadPool.submit(new FilterTask(mapper, domainFilter, prefixFilter)));
        }
        for (Future<CountItem> i : result) { // will ensure every thread return
            CountItem item = i.get();
            count_of_allowed += item.count_of_allowed;
            count_of_disallowed += item.count_of_disallowed;
            count_of_nohit += item.count_of_nohit;
            xor_of_allowed_value ^= item.xor_of_allowed_value;
            xor_of_disallowed_value ^= item.xor_of_disallowed_value;
        }
        threadPool.shutdownNow();
        QuickerPrinter.getPrinter().flush();
        System.out.println(count_of_allowed);
        System.out.println(count_of_disallowed);
        System.out.println(count_of_nohit);
        System.out.format("%08x\n", xor_of_allowed_value);
        System.out.format("%08x\n", xor_of_disallowed_value);

        System.err.println("Cost: " + (System.currentTimeMillis() - start));
    }
}

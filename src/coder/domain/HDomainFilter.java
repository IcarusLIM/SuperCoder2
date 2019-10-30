package coder.domain;

import coder.AttUrl;
import coder.utils.Murmur3Hash;

public class HDomainFilter extends DomainFilter {
    private static ThreadLocal<Status> statusThreadLocal = new ThreadLocal<>();

    public HDomainFilter(String file) {
        super(file);
    }

    @Override
    public int filter(AttUrl attUrl, Murmur3Hash hashFunction, long[][] work) {
        Status status = statusThreadLocal.get();
        if (status == null) {
            status = new Status();
            statusThreadLocal.set(status);
        }
        String domain = attUrl.domain;
        int port = attUrl.port;
        if (status.cachedDomain.equals(domain)) {
            if (status.cachedPort != port) {
                status.cachedPort = port;
                status.cachedPortIndicator = filterDomain(domain + ":" + port, hashFunction, work);
            }
            if (status.cachedPortIndicator != 0)
                return status.cachedPortIndicator;
            if (status.indicatorOutDate) {
                status.cachedIndicator = filterDomain(domain, hashFunction, work);
                status.indicatorOutDate = false;
            }
            return status.cachedIndicator;
        } else {
            status.cachedDomain = domain;
            status.cachedPort = port;
            status.cachedPortIndicator = filterDomain(domain + ":" + port, hashFunction, work);
            if (status.cachedPortIndicator != 0) {
                status.indicatorOutDate = true;
                return status.cachedPortIndicator;
            }
            status.cachedIndicator = filterDomain(domain, hashFunction, work);
            status.indicatorOutDate = false;
            return status.cachedIndicator;
        }
    }

    public static class Status {
        public String cachedDomain = "";
        public int cachedPort = 0;
        public int cachedPortIndicator = 0;
        public int cachedIndicator = 0;
        public boolean indicatorOutDate = true;
    }
}

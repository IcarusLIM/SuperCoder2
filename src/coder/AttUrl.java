package coder;

public class AttUrl {

    public static final int MAX_URL_Length = 2047;
    public final String scheme;
    public final String domain;
    public final int port;
    public final int firstSlash;
    public final String path;
    public final String url;
    public final String strValue;

    private AttUrl(String u, String value) {
        this.url = u;
        int p1, p2, p3;
        p1 = u.indexOf("://");
        p2 = u.indexOf(":", p1 + 3);
        p3 = u.indexOf("/", p1 + 3);
        firstSlash = p3;
        scheme = u.substring(0, p1);
        path = u.substring(p3);
        if (p2 != -1 && p2 < p3) {
            domain = u.substring(p1 + 3, p2);
            port = Integer.parseInt(u.substring(p2 + 1, p3));
        } else {
            this.domain = u.substring(p1 + 3, p3);
            if (scheme.equals("http")) {
                this.port = 80;
            } else if (scheme.equals("https")) {
                this.port = 443;
            } else {
                this.port = -1;
            }
        }
        this.strValue = value;
    }

    public static AttUrl newAndCheck(String line) {
        int i = line.indexOf("\t");
        String url = line.substring(0, i);
        return new AttUrl(url, line.substring(i + 1, i + 9));
    }

}
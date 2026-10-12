package org.love2d.android;

import java.io.*;
import java.net.*;

/** Test the real downloader with deterministic HTTP responses, without a network. */
public final class DownloadsTest {
    static int checks, connections;
    static void fail(String url, long limit) throws Exception {
        try { Downloads.fetch(url, new ByteArrayOutputStream(), limit); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError("Expected download failure for " + url);
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") || protocol.equals("http") ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                connections++;
                return new HttpURLConnection(url) {
                    @Override public int getResponseCode() {
                        if (url.getPath().startsWith("/redirect")) return 302;
                        if (url.getPath().equals("/loop") || url.getPath().equals("/missing-location")) return 307;
                        if (url.getPath().equals("/rate")) return 403;
                        if (url.getPath().equals("/missing")) return 404;
                        return 200;
                    }
                    @Override public String getHeaderField(String name) {
                        if (!name.equals("Location")) return null;
                        if (url.getPath().equals("/redirect-insecure")) return "http://example.com/good";
                        if (url.getPath().equals("/redirect-good")) return "/good";
                        if (url.getPath().equals("/loop")) return "/loop";
                        return null;
                    }
                    @Override public int getContentLength() { return url.getPath().equals("/oversize") ? 1000 : -1; }
                    @Override public InputStream getInputStream() { return new ByteArrayInputStream("hello".getBytes()); }
                    @Override public void disconnect() { }
                    @Override public boolean usingProxy() { return false; }
                    @Override public void connect() { }
                };
            }
        } : null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Downloads.fetch("https://example.com/redirect-good", out, 5);
        if (!out.toString("UTF-8").equals("hello")) throw new AssertionError(); checks++;
        fail("http://example.com/good", 10);
        fail("https://example.com/redirect-insecure", 10);
        fail("https://example.com/missing-location", 10);
        fail("https://example.com/loop", 10);
        fail("https://example.com/rate", 10);
        fail("https://example.com/missing", 10);
        fail("https://example.com/oversize", 10);
        fail("https://example.com/good", 4);
        Thread.currentThread().interrupt();
        fail("https://example.com/good", 10); Thread.interrupted();
        File hash = File.createTempFile("lmm-hash", ".txt");
        try {
            try (FileOutputStream file = new FileOutputStream(hash)) { file.write("hello".getBytes("UTF-8")); }
            if (!Downloads.sha256(hash).equals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")) throw new AssertionError(); checks++;
        } finally { hash.delete(); }
        System.out.println("Passed " + checks + " HTTPS download and checksum checks.");
    }
}

package org.love2d.android;

import java.io.*;
import java.net.*;
import java.util.*;

/** Bounded HTTPS downloads with explicit redirect and status handling. */
public final class Downloads {
    public static void fetch(String url, OutputStream out, long limit) throws IOException {
        URL current = new URL(url);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!current.getProtocol().equals("https")) throw new IOException("Downloads require HTTPS");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "LMM-Android-Updater/1");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect is missing its destination");
                    current = new URL(current, location);
                    continue;
                }
                if (code != 200) {
                    String detail = (code == 403 || code == 429) ? " (GitHub may be rate limited; try later)" : "";
                    throw new IOException("Download returned HTTP " + code + detail);
                }
                if (connection.getContentLength() > limit) throw new IOException("Download is too large");
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[32768];
                    long total = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Download cancelled");
                        total += n;
                        if (total > limit) throw new IOException("Download exceeds size limit");
                        out.write(buffer, 0, n);
                    }
                }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many download redirects");
    }

    public static String json(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fetch(url, out, 2 * 1024 * 1024);
        return out.toString("UTF-8");
    }

    public static String sha256(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
}

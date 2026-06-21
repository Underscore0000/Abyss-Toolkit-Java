package com.abyss.toolkit.service;

import java.io.*;
import java.net.*;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

public class ScanServices {

    private static final Map<Integer, String> WELL_KNOWN_PORTS = new HashMap<>();
    static {
        WELL_KNOWN_PORTS.put(21, "FTP"); WELL_KNOWN_PORTS.put(22, "SSH"); WELL_KNOWN_PORTS.put(23, "Telnet");
        WELL_KNOWN_PORTS.put(25, "SMTP"); WELL_KNOWN_PORTS.put(53, "DNS"); WELL_KNOWN_PORTS.put(80, "HTTP");
        WELL_KNOWN_PORTS.put(110, "POP3"); WELL_KNOWN_PORTS.put(111, "RPC"); WELL_KNOWN_PORTS.put(135, "MSRPC");
        WELL_KNOWN_PORTS.put(139, "NetBIOS"); WELL_KNOWN_PORTS.put(143, "IMAP"); WELL_KNOWN_PORTS.put(443, "HTTPS");
        WELL_KNOWN_PORTS.put(445, "SMB"); WELL_KNOWN_PORTS.put(993, "IMAPS"); WELL_KNOWN_PORTS.put(995, "POP3S");
        WELL_KNOWN_PORTS.put(1433, "MSSQL"); WELL_KNOWN_PORTS.put(1521, "Oracle"); WELL_KNOWN_PORTS.put(3306, "MySQL");
        WELL_KNOWN_PORTS.put(3389, "RDP"); WELL_KNOWN_PORTS.put(5432, "PostgreSQL"); WELL_KNOWN_PORTS.put(5900, "VNC");
        WELL_KNOWN_PORTS.put(6379, "Redis"); WELL_KNOWN_PORTS.put(8080, "HTTP-Alt"); WELL_KNOWN_PORTS.put(8443, "HTTPS-Alt");
        WELL_KNOWN_PORTS.put(9200, "Elasticsearch"); WELL_KNOWN_PORTS.put(27017, "MongoDB");
    }

    public static class PortScanner {
        public record PortResult(int port, boolean open, String service, String banner, long latencyMs, Integer ttl, String extra) {}

        public static List<PortResult> quickScan(String host, Consumer<Integer> onProgress) {
            int[] common = {21,22,23,25,53,80,110,111,135,139,143,443,445,993,995,1433,1521,3306,3389,5432,5900,6379,8080,8443,9200,27017};
            List<PortResult> results = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(common.length);
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger done = new AtomicInteger();
            for (int port : common) {
                futures.add(pool.submit(() -> {
                    boolean open = false;
                    String banner = "";
                    long start = System.currentTimeMillis();
                    try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
                        Future<Void> connectFuture = channel.connect(new InetSocketAddress(host, port));
                        connectFuture.get(800, TimeUnit.MILLISECONDS);
                        open = true;
                        banner = grabBanner(host, port, 500);
                    } catch (Exception ignored) {}
                    long latency = System.currentTimeMillis() - start;
                    if (open) {
                        results.add(new PortResult(port, true, WELL_KNOWN_PORTS.getOrDefault(port, "unknown"),
                                banner, latency, null, ""));
                    }
                    if (onProgress != null) {
                        onProgress.accept((int) ((done.incrementAndGet() * 100.0) / common.length));
                    }
                }));
            }
            futures.forEach(f -> { try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {} });
            pool.shutdown();
            results.sort(Comparator.comparingInt(PortResult::port));
            return results;
        }

        public static List<PortResult> scan(String host, int startPort, int endPort, int timeoutMs, int threads, Consumer<Integer> onProgress) {
            List<PortResult> results = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(threads, 500)));
            List<Future<?>> futures = new ArrayList<>();
            int total = endPort - startPort + 1;
            AtomicInteger done = new AtomicInteger();

            for (int port = startPort; port <= endPort; port++) {
                final int p = port;
                futures.add(pool.submit(() -> {
                    boolean open = false;
                    String banner = "";
                    long start = System.currentTimeMillis();
                    try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
                        Future<Void> connectFuture = channel.connect(new InetSocketAddress(host, p));
                        connectFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
                        open = true;
                        banner = grabBanner(host, p, Math.min(timeoutMs, 3000));
                    } catch (Exception ignored) {}
                    long latency = System.currentTimeMillis() - start;
                    if (open) {
                        results.add(new PortResult(p, true, WELL_KNOWN_PORTS.getOrDefault(p, "unknown"),
                                banner, latency, null, ""));
                    }
                    if (onProgress != null) {
                        onProgress.accept((int) ((done.incrementAndGet() * 100.0) / total));
                    }
                }));
            }

            futures.forEach(f -> { try { f.get(60, TimeUnit.SECONDS); } catch (Exception ignored) {} });
            pool.shutdown();
            results.sort(Comparator.comparingInt(PortResult::port));
            return results;
        }

        private static String grabBanner(String host, int port, int timeout) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeout);
                socket.setSoTimeout(timeout);
                if (port == 80 || port == 8080 || port == 8000) {
                    socket.getOutputStream().write(("HEAD / HTTP/1.0\r\nHost: " + host + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[512];
                int n = reader.read(buf);
                if (n > 0) sb.append(buf, 0, n);
                String raw = sb.toString().trim();
                String server = extractHeader(raw, "Server:");
                String powered = extractHeader(raw, "X-Powered-By:");
                if (!server.isEmpty()) return "Server: " + server + (powered.isEmpty() ? "" : " | " + powered);
                return raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
            } catch (Exception e) { return ""; }
        }
        private static String extractHeader(String text, String header) {
            int idx = text.indexOf(header);
            if (idx < 0) return "";
            int end = text.indexOf("\n", idx);
            if (end < 0) return text.substring(idx + header.length()).trim();
            return text.substring(idx + header.length(), end).trim();
        }
    }

    public static class HostDiscovery {
        public record HostStatus(String ip, String hostname, long latencyMs, Integer ttl, String mac) {}

        public static List<HostStatus> discover(String cidr, int timeoutMs, Consumer<Integer> onProgress) {
            String[] parts = cidr.split("/");
            String base = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 24;
            String[] octets = base.split("\\.");
            String network = octets[0] + "." + octets[1] + "." + octets[2] + ".";
            int hostCount = Math.min((int) Math.pow(2, 32 - prefix), 254);
            List<HostStatus> results = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(64);
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger done = new AtomicInteger();
            Map<String, String> arpCache = getArpCache();
            for (int i = 1; i <= hostCount; i++) {
                final String ip = network + i;
                futures.add(pool.submit(() -> {
                    long start = System.currentTimeMillis();
                    boolean online = false;
                    String hostname = "";
                    try {
                        InetAddress addr = InetAddress.getByName(ip);
                        online = addr.isReachable(timeoutMs);
                        if (online) { hostname = addr.getCanonicalHostName(); if (hostname.equals(ip)) hostname = ""; }
                    } catch (IOException ignored) {}
                    long latency = System.currentTimeMillis() - start;
                    if (online) {
                        String mac = arpCache.getOrDefault(ip, "N/A");
                        results.add(new HostStatus(ip, hostname, latency, null, mac));
                    }
                    if (onProgress != null) onProgress.accept((int) ((done.incrementAndGet() * 100.0) / hostCount));
                }));
            }
            futures.forEach(f -> { try { f.get(60, TimeUnit.SECONDS); } catch (Exception ignored) {} });
            pool.shutdown();
            results.sort(Comparator.comparing(HostStatus::ip));
            return results;
        }

        private static Map<String, String> getArpCache() {
            Map<String, String> cache = new HashMap<>();
            try {
                Process p = Runtime.getRuntime().exec("arp -a");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3 && parts[0].matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                        cache.put(parts[0], parts[1]);
                    }
                }
            } catch (Exception ignored) {}
            return cache;
        }
    }

    public static class DnsTools {
        public static List<String> lookupA(String host) {
            try { List<String> out = new ArrayList<>();
                for (InetAddress a : InetAddress.getAllByName(host)) if (a.getAddress().length == 4) out.add(a.getHostAddress());
                return out;
            } catch (Exception e) { return List.of("Failed: " + e.getMessage()); }
        }
        public static List<String> lookupAAAA(String host) {
            try { List<String> out = new ArrayList<>();
                for (InetAddress a : InetAddress.getAllByName(host)) if (a.getAddress().length == 16) out.add(a.getHostAddress());
                return out;
            } catch (Exception e) { return List.of("Failed: " + e.getMessage()); }
        }
        public static List<String> lookupMX(String host) { return lookup(host, "MX"); }
        public static List<String> lookupTXT(String host) { return lookup(host, "TXT"); }
        public static List<String> lookupNS(String host) { return lookup(host, "NS"); }
        public static List<String> lookupCNAME(String host) { return lookup(host, "CNAME"); }
        public static List<String> lookupSOA(String host) { return lookup(host, "SOA"); }
        public static List<String> lookupSRV(String host) { return lookup(host, "SRV"); }

        private static List<String> lookup(String host, String recordType) {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
                javax.naming.directory.Attributes attrs = ctx.getAttributes(host, new String[]{recordType});
                List<String> out = new ArrayList<>();
                javax.naming.NamingEnumeration<?> all = attrs.get(recordType) == null ? null : attrs.get(recordType).getAll();
                if (all != null) { while (all.hasMore()) out.add(String.valueOf(all.next())); }
                return out;
            } catch (Exception e) { return List.of("Lookup failed: " + e.getMessage()); }
        }
    }

    public static class SecurityHeadersAnalyzer {
        public record HeaderCheck(String header, boolean present, String value, String recommendation) {}
        public record HeaderReport(List<HeaderCheck> checks, int score, List<String> warnings) {}
        private static final List<String> CHECKED = List.of(
                "Content-Security-Policy", "Strict-Transport-Security", "X-Frame-Options",
                "X-Content-Type-Options", "Referrer-Policy", "Permissions-Policy");

        public static HeaderReport analyze(String url) throws IOException, InterruptedException {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(8)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.startsWith("http") ? url : "https://" + url))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            List<HeaderCheck> checks = new ArrayList<>();
            int score = 0;
            List<String> warnings = new ArrayList<>();
            for (String h : CHECKED) {
                Optional<String> val = resp.headers().firstValue(h);
                boolean present = val.isPresent();
                if (present) score += 16;
                else warnings.add("Missing: " + h);
                checks.add(new HeaderCheck(h, present, val.orElse("-"), present ? "OK" : "MISSING"));
            }
            return new HeaderReport(checks, Math.min(score, 100), warnings);
        }
    }

    public static class HttpsInspector {
        public record HttpsReport(boolean httpsAvailable, boolean redirectsFromHttp,
                                   boolean certValid, String expiration, String issuer,
                                   String subject, List<String> san, String protocol, String cipherSuite) {}

        public static HttpsReport inspect(String host) {
            boolean httpsAvailable = false, redirects = false, valid = false;
            String expiration = "-", issuer = "-", subject = "-", protocol = "-", cipherSuite = "-";
            List<String> san = new ArrayList<>();
            try {
                URL httpUrl = new URL("http://" + host);
                HttpURLConnection httpConn = (HttpURLConnection) httpUrl.openConnection();
                httpConn.setInstanceFollowRedirects(false);
                httpConn.setConnectTimeout(6000);
                int code = httpConn.getResponseCode();
                String location = httpConn.getHeaderField("Location");
                redirects = code >= 300 && code < 400 && location != null && location.startsWith("https");
                httpConn.disconnect();
            } catch (IOException ignored) {}
            try {
                URL httpsUrl = new URL("https://" + host);
                HttpsURLConnection conn = (HttpsURLConnection) httpsUrl.openConnection();
                conn.setConnectTimeout(6000);
                conn.connect();
                httpsAvailable = true;
                SSLSession session = conn.getSSLSession().orElse(null);
                if (session != null) {
                    protocol = session.getProtocol();
                    cipherSuite = session.getCipherSuite();
                    Certificate[] certs = session.getPeerCertificates();
                    if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                        x509.checkValidity(); valid = true;
                        expiration = x509.getNotAfter().toString();
                        issuer = x509.getIssuerX500Principal().getName();
                        subject = x509.getSubjectX500Principal().getName();
                        try {
                            Collection<List<?>> sanExt = x509.getSubjectAlternativeNames();
                            if (sanExt != null) { for (List<?> entry : sanExt) if (entry.size() > 1) san.add(entry.get(1).toString()); }
                        } catch (Exception ignored) {}
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
            return new HttpsReport(httpsAvailable, redirects, valid, expiration, issuer, subject, san, protocol, cipherSuite);
        }
    }

    public static class TechFingerprinter {
        public static List<String> fingerprint(String url) {
            List<String> found = new ArrayList<>();
            try {
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(url.startsWith("http") ? url : "https://" + url))
                        .timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                String server = resp.headers().firstValue("Server").orElse("");
                String body = resp.body();
                if (server.toLowerCase().contains("apache")) found.add("Apache");
                if (server.toLowerCase().contains("nginx")) found.add("Nginx");
                if (server.toLowerCase().contains("iis")) found.add("IIS");
                if (body.contains("wp-content") || body.contains("wp-includes")) found.add("WordPress");
                if (body.contains("data-reactroot") || body.contains("react")) found.add("React");
                if (body.contains("ng-version")) found.add("Angular");
                if (body.contains("__vue__") || body.contains("data-v-")) found.add("Vue");
                if (found.isEmpty()) found.add("No recognizable tech found");
            } catch (Exception e) { found.add("Error: " + e.getMessage()); }
            return found;
        }
    }

    public static class BannerGrabber {
        public static String grab(String host, int port, int timeoutMs) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                if (port == 80 || port == 8080 || port == 8000) {
                    socket.getOutputStream().write(("HEAD / HTTP/1.0\r\nHost: " + host + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[2048];
                int n = reader.read(buf);
                if (n > 0) sb.append(buf, 0, n);
                return sb.toString().trim();
            } catch (IOException e) { return "No banner: " + e.getMessage(); }
        }
    }
}
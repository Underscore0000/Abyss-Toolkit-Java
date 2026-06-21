package com.abyss.toolkit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class AnalysisServices {

    public static class WhoisLookup {
        public static String lookup(String domain) {
            try {
                String referral = query("whois.iana.org", domain);
                String server = referral.lines().filter(l -> l.toLowerCase().startsWith("refer:"))
                        .map(l -> l.split(":", 2)[1].trim()).findFirst().orElse(null);
                if (server == null || server.isBlank()) return referral;
                return query(server, domain);
            } catch (IOException e) { return "Whois failed: " + e.getMessage(); }
        }
        private static String query(String whoisServer, String domain) throws IOException {
            try (Socket socket = new Socket(whoisServer, 43)) {
                socket.setSoTimeout(10000);
                socket.getOutputStream().write((domain + "\r\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                return sb.toString();
            }
        }
        public static Map<String, String> extractSummary(String raw) {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("Registrar", extract(raw, "(?i)registrar:\\s*(.+)"));
            out.put("Creation Date", extract(raw, "(?i)creation date:\\s*(.+)"));
            out.put("Expiration Date", extract(raw, "(?i)(registry expiry date|expiry date|expiration date):\\s*(.+)"));
            out.put("Name Servers", extractNS(raw));
            out.put("Status", extract(raw, "(?i)status:\\s*(.+)"));
            return out;
        }
        private static String extract(String text, String regex) {
            Matcher m = Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(m.groupCount()).trim() : "Not found";
        }
        private static String extractNS(String text) {
            List<String> ns = new ArrayList<>();
            Matcher m = Pattern.compile("(?i)(name server|nserver):\\s*(.+)").matcher(text);
            while (m.find()) ns.add(m.group(2).trim());
            return ns.isEmpty() ? "Not found" : String.join(", ", ns);
        }
    }

    public static class DnsEnumeration {
        public static Map<String, List<String>> enumerate(String host) {
            Map<String, List<String>> out = new LinkedHashMap<>();
            out.put("A", ScanServices.DnsTools.lookupA(host));
            out.put("AAAA", ScanServices.DnsTools.lookupAAAA(host));
            out.put("MX", ScanServices.DnsTools.lookupMX(host));
            out.put("TXT", ScanServices.DnsTools.lookupTXT(host));
            out.put("NS", ScanServices.DnsTools.lookupNS(host));
            out.put("CNAME", ScanServices.DnsTools.lookupCNAME(host));
            return out;
        }
    }

    public static class GeoIpLookup {
        public record GeoInfo(String country, String region, String city, String isp, String asn) {}
        public static GeoInfo lookup(String ip) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder(URI.create("http://ip-api.com/json/" + ip + "?fields=country,regionName,city,isp,as"))
                        .timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode node = new ObjectMapper().readTree(resp.body());
                return new GeoInfo(
                        node.path("country").asText("Unknown"),
                        node.path("regionName").asText("Unknown"),
                        node.path("city").asText("Unknown"),
                        node.path("isp").asText("Unknown"),
                        node.path("as").asText("Unknown")
                );
            } catch (Exception e) { return new GeoInfo("Error: " + e.getMessage(), "", "", "", ""); }
        }
    }

    public static class HashUtil {
        public static String hashText(String text, String algorithm) {
            try {
                MessageDigest md = MessageDigest.getInstance(algorithm);
                return toHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        }
        public static String hashFile(Path path, String algorithm) {
            try (InputStream is = Files.newInputStream(path)) {
                if ("CRC32".equalsIgnoreCase(algorithm)) {
                    CRC32 crc = new CRC32();
                    byte[] buf = new byte[8192];
                    int n; while ((n = is.read(buf)) != -1) crc.update(buf, 0, n);
                    return Long.toHexString(crc.getValue());
                }
                MessageDigest md = MessageDigest.getInstance(algorithm);
                byte[] buf = new byte[8192];
                int n; while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
                return toHex(md.digest());
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        }
        public static boolean compare(String a, String b) { return a != null && a.equalsIgnoreCase(b); }
        private static String toHex(byte[] bytes) { StringBuilder sb = new StringBuilder(); for (byte b : bytes) sb.append(String.format("%02x", b)); return sb.toString(); }
    }

    public static class EncoderDecoder {
        public static String base64Encode(String input) { return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8)); }
        public static String base64Decode(String input) { try { return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8); } catch (Exception e) { return "Invalid"; } }
        public static String urlEncode(String input) { return URLEncoder.encode(input, StandardCharsets.UTF_8); }
        public static String urlDecode(String input) { try { return URLDecoder.decode(input, StandardCharsets.UTF_8); } catch (Exception e) { return "Invalid"; } }
        public static String hexEncode(String input) { StringBuilder sb = new StringBuilder(); for (byte b : input.getBytes(StandardCharsets.UTF_8)) sb.append(String.format("%02x", b)); return sb.toString(); }
        public static String hexDecode(String input) {
            try { byte[] out = new byte[input.length()/2]; for (int i=0; i<input.length(); i+=2) out[i/2]=(byte)((Character.digit(input.charAt(i),16)<<4)+Character.digit(input.charAt(i+1),16)); return new String(out, StandardCharsets.UTF_8); }
            catch (Exception e) { return "Invalid"; }
        }
    }

    public static class JwtViewer {
        public record JwtParts(String header, String payload, String signature, boolean validSignature) {}
        public static JwtParts decode(String token) {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return new JwtParts("Invalid", "Invalid", "", false);
            return new JwtParts(decodeSegment(parts[0]), decodeSegment(parts[1]), parts.length > 2 ? parts[2] : "(none)", parts.length == 3);
        }
        private static String decodeSegment(String segment) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(pad(segment));
                Object json = new ObjectMapper().readValue(decoded, Object.class);
                return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(json);
            } catch (Exception e) { return "Decode error: " + e.getMessage(); }
        }
        private static String pad(String s) { int rem = s.length() % 4; return rem == 0 ? s : s + "=".repeat(4 - rem); }
    }

    public static class LogAnalyzer {
        public record LogReport(long totalLines, Map<String, Integer> ipFrequency, Map<String, Integer> statusCodes, List<String> highlights) {}
        private static final Pattern COMBINED = Pattern.compile("^(\\S+) \\S+ \\S+ \\[(.+?)] \"(\\S+) (\\S+) \\S+\" (\\d{3}) (\\S+)");
        public static LogReport analyzeAccessLog(List<String> lines) {
            Map<String, Integer> ipFreq = new TreeMap<>(), statusCodes = new TreeMap<>();
            List<String> highlights = new ArrayList<>();
            for (String line : lines) {
                Matcher m = COMBINED.matcher(line);
                if (m.find()) {
                    String ip = m.group(1), status = m.group(5);
                    ipFreq.merge(ip, 1, Integer::sum);
                    statusCodes.merge(status, 1, Integer::sum);
                    if (status.startsWith("5")) highlights.add("5xx from " + ip + " -> " + m.group(4));
                    if (status.equals("404")) highlights.add("404 from " + ip + " -> " + m.group(4));
                }
            }
            return new LogReport(lines.size(), ipFreq, statusCodes, highlights);
        }

        private static final Pattern SSH_FAIL = Pattern.compile("Failed password for (?:invalid user )?(\\S+) from (\\S+)");
        private static final Pattern SSH_OK = Pattern.compile("Accepted password for (\\S+) from (\\S+)");
        public record SshLoginReport(int failed, int success, Map<String, Integer> byIp, Map<String, Integer> byUser) {}
        public static SshLoginReport analyzeSshLog(List<String> lines) {
            int failed=0, success=0; Map<String,Integer> byIp=new TreeMap<>(), byUser=new TreeMap<>();
            for (String line : lines) {
                Matcher fm = SSH_FAIL.matcher(line);
                Matcher sm = SSH_OK.matcher(line);
                if (fm.find()) { failed++; byIp.merge(fm.group(2),1,Integer::sum); byUser.merge(fm.group(1),1,Integer::sum); }
                else if (sm.find()) { success++; byIp.merge(sm.group(2),1,Integer::sum); byUser.merge(sm.group(1),1,Integer::sum); }
            }
            return new SshLoginReport(failed, success, byIp, byUser);
        }

        public static List<String> search(List<String> lines, String term) {
            return lines.stream().filter(l -> l.toLowerCase().contains(term.toLowerCase())).toList();
        }
    }
}
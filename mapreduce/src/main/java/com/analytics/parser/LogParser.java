package com.analytics.parser;

import com.analytics.model.LogRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance parser for Apache Combined Log Format records.
 * Performs cryptographic SHA-256 IP hashing, user-agent enrichment,
 * and synthetic GeoIP country derivation.
 */
public class LogParser {

    // Regex matching Combined Log Format: IP ident auth [timestamp] "METHOD URL PROTOCOL" status bytes "ref" "ua"
    private static final Pattern COMBINED_LOG_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+\\[([\\w:/]+\\s+[+\\-]\\d{4})\\]\\s+\"(\\S+)\\s+([^\\s]+)\\s+([^\"]+)\"\\s+(\\d{3})\\s+(\\d+|-)\\s+\"([^\"]*)\"\\s+\"([^\"]*)\"$"
    );

    private static final DateTimeFormatter INPUT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private static final DateTimeFormatter OUTPUT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    /**
     * Parses a raw log line into a strongly-typed LogRecord.
     * Returns null if line fails regex or contains unparseable values.
     */
    public static LogRecord parseLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = COMBINED_LOG_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String rawIp = matcher.group(1);
            String timestampStr = matcher.group(4);
            String method = matcher.group(5).toUpperCase();
            String fullUrl = matcher.group(6);
            String statusCodeStr = matcher.group(8);
            String bytesStr = matcher.group(9);
            String referrer = matcher.group(10);
            String userAgent = matcher.group(11);

            // 1. SHA-256 Hash IP for Privacy (Never store raw IP)
            String ipHash = hashSha256(rawIp);

            // 2. Parse and normalize timestamp to UTC format yyyy-MM-dd HH:mm:ss
            OffsetDateTime odt = OffsetDateTime.parse(timestampStr, INPUT_TIME_FORMATTER);
            LocalDateTime utcTime = odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            String formattedTime = utcTime.format(OUTPUT_TIME_FORMATTER);

            // 3. Extract URL path and query string
            String url = fullUrl;
            String queryString = "-";
            int qIndex = fullUrl.indexOf('?');
            if (qIndex != -1) {
                url = fullUrl.substring(0, qIndex);
                queryString = fullUrl.substring(qIndex + 1);
            }

            // 4. Status code and bytes
            int statusCode = Integer.parseInt(statusCodeStr);
            long bytesSent = bytesStr.equals("-") ? 0L : Long.parseLong(bytesStr);

            // 5. Derive Browser, OS, and Device Type
            String browser = deriveBrowser(userAgent);
            String os = deriveOS(userAgent);
            String deviceType = deriveDeviceType(userAgent);

            // 6. Derive Country from IP prefix
            String country = deriveCountry(rawIp);

            // 7. Flag static assets
            boolean isStatic = LogRecord.isStaticUrl(url);

            return new LogRecord(
                    ipHash,
                    formattedTime,
                    method,
                    url,
                    queryString,
                    statusCode,
                    bytesSent,
                    referrer,
                    userAgent,
                    browser,
                    os,
                    deviceType,
                    country,
                    isStatic
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Hashes string with SHA-256 returning hex string.
     */
    public static String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String deriveBrowser(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return "Unknown";
        if (userAgent.contains("Googlebot")) return "Googlebot";
        if (userAgent.contains("bingbot")) return "Bingbot";
        if (userAgent.contains("Baiduspider")) return "Baiduspider";
        if (userAgent.contains("curl")) return "curl";
        if (userAgent.contains("python-requests")) return "python-requests";
        if (userAgent.contains("Edg/") || userAgent.contains("Edge/")) return "Edge";
        if (userAgent.contains("Chrome/")) return "Chrome";
        if (userAgent.contains("Firefox/")) return "Firefox";
        if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) return "Safari";
        return "Other";
    }

    public static String deriveOS(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return "Unknown";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iOS")) return "iOS";
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS")) return "macOS";
        if (userAgent.contains("Linux") && !userAgent.contains("Android")) return "Linux";
        return "Other";
    }

    public static String deriveDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return "Desktop";
        if (userAgent.contains("bot") || userAgent.contains("Bot") || userAgent.contains("spider")
                || userAgent.contains("curl") || userAgent.contains("python")) {
            return "Bot";
        }
        if (userAgent.contains("iPad") || userAgent.contains("Tablet") || userAgent.contains("SM-X")) {
            return "Tablet";
        }
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            return "Mobile";
        }
        return "Desktop";
    }

    public static String deriveCountry(String ip) {
        if (ip == null || ip.isEmpty()) return "Unknown";
        int dotIndex = ip.indexOf('.');
        if (dotIndex == -1) return "Unknown";
        String firstOctet = ip.substring(0, dotIndex);

        switch (firstOctet) {
            case "13": return "US";
            case "103": return "IN";
            case "81": return "GB";
            case "194": return "DE";
            case "142": return "CA";
            case "133": return "JP";
            case "80": return "FR";
            case "139": return "AU";
            case "177": return "BR";
            case "41": return "ZA";
            default: return "US";
        }
    }
}


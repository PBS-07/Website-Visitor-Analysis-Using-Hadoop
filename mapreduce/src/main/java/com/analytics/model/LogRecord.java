package com.analytics.model;

import java.io.Serializable;

/**
 * Data Model representing a cleaned and enriched web log record.
 * Conforms to PRD Section 5.2 schema.
 */
public class LogRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String visitorIpHash;
    private String eventTime; // yyyy-MM-dd HH:mm:ss
    private String method;
    private String url;
    private String queryString;
    private int statusCode;
    private long bytesSent;
    private String referrer;
    private String userAgent;
    private String browser;
    private String os;
    private String deviceType;
    private String country;
    private boolean isStaticAsset;

    public LogRecord() {
    }

    public LogRecord(String visitorIpHash, String eventTime, String method, String url,
                     String queryString, int statusCode, long bytesSent, String referrer,
                     String userAgent, String browser, String os, String deviceType,
                     String country, boolean isStaticAsset) {
        this.visitorIpHash = visitorIpHash;
        this.eventTime = eventTime;
        this.method = method;
        this.url = url;
        this.queryString = queryString != null && !queryString.isEmpty() ? queryString : "-";
        this.statusCode = statusCode;
        this.bytesSent = bytesSent;
        this.referrer = referrer != null && !referrer.isEmpty() ? referrer : "-";
        this.userAgent = userAgent;
        this.browser = browser;
        this.os = os;
        this.deviceType = deviceType;
        this.country = country;
        this.isStaticAsset = isStaticAsset;
    }

    /**
     * Converts the record to Tab-Separated Values (TSV) format for HDFS /weblogs/clean/ storage.
     */
    public String toTabSeparatedString() {
        return String.join("\t",
                visitorIpHash,
                eventTime,
                method,
                url,
                queryString,
                String.valueOf(statusCode),
                String.valueOf(bytesSent),
                referrer,
                userAgent,
                browser,
                os,
                deviceType,
                country
        );
    }

    /**
     * Parses a Tab-Separated line from HDFS /weblogs/clean/.
     */
    public static LogRecord fromTabSeparatedString(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 13) {
            return null;
        }

        LogRecord record = new LogRecord();
        record.setVisitorIpHash(parts[0]);
        record.setEventTime(parts[1]);
        record.setMethod(parts[2]);
        record.setUrl(parts[3]);
        record.setQueryString(parts[4]);
        record.setStatusCode(Integer.parseInt(parts[5]));
        record.setBytesSent(Long.parseLong(parts[6]));
        record.setReferrer(parts[7]);
        record.setUserAgent(parts[8]);
        record.setBrowser(parts[9]);
        record.setOs(parts[10]);
        record.setDeviceType(parts[11]);
        record.setCountry(parts[12]);
        record.setStaticAsset(isStaticUrl(parts[3]));

        return record;
    }

    public static boolean isStaticUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".png") ||
               lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
               lower.endsWith(".svg") || lower.endsWith(".ico") || lower.endsWith(".woff") ||
               lower.endsWith(".woff2") || lower.endsWith(".ttf");
    }

    // Getters and Setters
    public String getVisitorIpHash() { return visitorIpHash; }
    public void setVisitorIpHash(String visitorIpHash) { this.visitorIpHash = visitorIpHash; }

    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public long getBytesSent() { return bytesSent; }
    public void setBytesSent(long bytesSent) { this.bytesSent = bytesSent; }

    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public boolean isStaticAsset() { return isStaticAsset; }
    public void setStaticAsset(boolean staticAsset) { isStaticAsset = staticAsset; }
}


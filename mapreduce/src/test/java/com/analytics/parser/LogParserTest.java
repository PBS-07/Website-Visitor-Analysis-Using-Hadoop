package com.analytics.parser;

import com.analytics.model.LogRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LogParserTest {

    @Test
    public void testValidCombinedLogParsing() {
        String logLine = "203.0.113.42 - - [10/Nov/2026:09:15:32 +0000] \"GET /products/laptop HTTP/1.1\" 200 5123 \"https://www.google.com/\" \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0\"";

        LogRecord record = LogParser.parseLine(logLine);
        assertNotNull(record, "Valid line should not parse to null");

        // Verify SHA-256 IP privacy hash
        assertNotNull(record.getVisitorIpHash());
        assertEquals(64, record.getVisitorIpHash().length(), "SHA-256 hash must be 64 hex chars");
        assertNotEquals("203.0.113.42", record.getVisitorIpHash(), "Raw IP must never be exposed");

        // Verify UTC timestamp
        assertEquals("2026-11-10 09:15:32", record.getEventTime());

        // Verify HTTP parameters
        assertEquals("GET", record.getMethod());
        assertEquals("/products/laptop", record.getUrl());
        assertEquals("-", record.getQueryString());
        assertEquals(200, record.getStatusCode());
        assertEquals(5123L, record.getBytesSent());
        assertEquals("https://www.google.com/", record.getReferrer());

        // Verify Device & User-Agent Enrichment
        assertEquals("Chrome", record.getBrowser());
        assertEquals("Windows", record.getOs());
        assertEquals("Desktop", record.getDeviceType());
        assertFalse(record.isStaticAsset());
    }

    @Test
    public void testQueryStringExtraction() {
        String logLine = "13.45.67.89 - - [10/Nov/2026:10:00:00 +0000] \"GET /search?q=hadoop+tutorial&category=books HTTP/1.1\" 200 1200 \"-\" \"curl/8.4.0\"";

        LogRecord record = LogParser.parseLine(logLine);
        assertNotNull(record);
        assertEquals("/search", record.getUrl());
        assertEquals("q=hadoop+tutorial&category=books", record.getQueryString());
        assertEquals("curl", record.getBrowser());
        assertEquals("Bot", record.getDeviceType());
        assertEquals("US", record.getCountry());
    }

    @Test
    public void testCountryDerivationFromIp() {
        assertEquals("US", LogParser.deriveCountry("13.1.2.3"));
        assertEquals("IN", LogParser.deriveCountry("103.55.22.11"));
        assertEquals("GB", LogParser.deriveCountry("81.12.34.56"));
        assertEquals("DE", LogParser.deriveCountry("194.88.99.1"));
        assertEquals("CA", LogParser.deriveCountry("142.10.20.30"));
        assertEquals("JP", LogParser.deriveCountry("133.5.6.7"));
    }

    @Test
    public void testDeviceAndOsDerivation() {
        // Mobile iOS
        String mobileUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
        assertEquals("Safari", LogParser.deriveBrowser(mobileUa));
        assertEquals("iOS", LogParser.deriveOS(mobileUa));
        assertEquals("Mobile", LogParser.deriveDeviceType(mobileUa));

        // Tablet Android
        String tabletUa = "Mozilla/5.0 (Linux; Android 13; SM-X900) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
        assertEquals("Chrome", LogParser.deriveBrowser(tabletUa));
        assertEquals("Android", LogParser.deriveOS(tabletUa));
        assertEquals("Tablet", LogParser.deriveDeviceType(tabletUa));

        // Bot
        String botUa = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        assertEquals("Googlebot", LogParser.deriveBrowser(botUa));
        assertEquals("Bot", LogParser.deriveDeviceType(botUa));
    }

    @Test
    public void testStaticAssetDetection() {
        assertTrue(LogRecord.isStaticUrl("/css/main.css"));
        assertTrue(LogRecord.isStaticUrl("/js/app.bundle.js"));
        assertTrue(LogRecord.isStaticUrl("/images/logo.png"));
        assertTrue(LogRecord.isStaticUrl("/favicon.ico"));
        assertFalse(LogRecord.isStaticUrl("/products/laptop"));
        assertFalse(LogRecord.isStaticUrl("/"));
        assertFalse(LogRecord.isStaticUrl("/cart"));
    }

    @Test
    public void testMalformedLinesReturnNull() {
        // Corrupt timestamp
        assertNull(LogParser.parseLine("13.1.2.3 - - 10/Nov/2026:09:15:32 +0000 \"GET / HTTP/1.1\" 200 100 \"-\" \"-\""));
        // Truncated request line
        assertNull(LogParser.parseLine("13.1.2.3 - - [10/Nov/2026:09:15:32 +0000] \"GET /"));
        // Non-numeric status code
        assertNull(LogParser.parseLine("13.1.2.3 - - [10/Nov/2026:09:15:32 +0000] \"GET / HTTP/1.1\" STATUS 100 \"-\" \"-\""));
        // Garbage string
        assertNull(LogParser.parseLine("GARBAGE_LINE_RANDOM_TEXT_NOT_A_LOG"));
        // Null and empty
        assertNull(LogParser.parseLine(null));
        assertNull(LogParser.parseLine("   "));
    }
}

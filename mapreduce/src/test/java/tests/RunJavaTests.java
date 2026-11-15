package tests;

import com.analytics.model.LogRecord;
import com.analytics.parser.LogParser;

public class RunJavaTests {

    public static void main(String[] args) {
        System.out.println("=== Running Java LogParser Verification Tests ===");
        int passed = 0;
        int failed = 0;

        // Test 1: Combined Log Format parsing & SHA-256 IP hashing
        try {
            String line = "203.0.113.42 - - [10/Nov/2026:09:15:32 +0000] \"GET /products/laptop HTTP/1.1\" 200 5123 \"https://www.google.com/\" \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0\"";
            LogRecord record = LogParser.parseLine(line);
            assert record != null;
            assert record.getVisitorIpHash().length() == 64;
            assert !record.getVisitorIpHash().equals("203.0.113.42");
            assert record.getEventTime().equals("2026-11-10 09:15:32");
            assert record.getUrl().equals("/products/laptop");
            assert record.getStatusCode() == 200;
            assert record.getBrowser().equals("Chrome");
            assert record.getOs().equals("Windows");
            assert record.getDeviceType().equals("Desktop");
            assert !record.isStaticAsset();
            System.out.println("  [PASS] Test 1: Valid Combined Log parsing & SHA-256 IP privacy hashing");
            passed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] Test 1: " + t.getMessage());
            failed++;
        }

        // Test 2: Country derivation from IP octet
        try {
            assert LogParser.deriveCountry("13.1.2.3").equals("US");
            assert LogParser.deriveCountry("103.45.67.89").equals("IN");
            assert LogParser.deriveCountry("81.99.11.22").equals("GB");
            assert LogParser.deriveCountry("194.2.3.4").equals("DE");
            assert LogParser.deriveCountry("142.1.1.1").equals("CA");
            System.out.println("  [PASS] Test 2: Country IP prefix derivation");
            passed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] Test 2: " + t.getMessage());
            failed++;
        }

        // Test 3: Static asset detection
        try {
            assert LogRecord.isStaticUrl("/css/main.css");
            assert LogRecord.isStaticUrl("/js/app.js");
            assert LogRecord.isStaticUrl("/images/logo.png");
            assert !LogRecord.isStaticUrl("/products/laptop");
            assert !LogRecord.isStaticUrl("/");
            System.out.println("  [PASS] Test 3: Static asset classification");
            passed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] Test 3: " + t.getMessage());
            failed++;
        }

        // Test 4: Dead-letter rejection of malformed lines
        try {
            assert LogParser.parseLine("CORRUPTED_LINE_JUNK") == null;
            assert LogParser.parseLine("13.1.2.3 - - 10/Nov/2026:09:15:32 +0000 \"GET /\" 200 100 \"-\" \"-\"") == null;
            assert LogParser.parseLine(null) == null;
            System.out.println("  [PASS] Test 4: Malformed lines dead-letter rejection");
            passed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] Test 4: " + t.getMessage());
            failed++;
        }

        System.out.println("\nJava Test Results: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }
}


#!/usr/bin/env python3
"""
Unit and statistical distribution tests for generator/log_generator.py.
Compatible with both pytest and standard python -m unittest.
"""

import datetime
import math
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path

# Add project root to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from generator.log_generator import (
    DEFAULT_CONFIG,
    LogGenerator,
    parse_size_str,
)

# Apache Combined Log Format regex
COMBINED_LOG_REGEX = re.compile(
    r'^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s+'       # IP
    r'(\S+)\s+'                                       # Ident (typically -)
    r'(\S+)\s+'                                       # Authuser (typically -)
    r'\[(\d{2}/[A-Za-z]{3}/\d{4}:\d{2}:\d{2}:\d{2}\s+[+\-]\d{4})\]\s+' # [timestamp]
    r'"([A-Z]+)\s+([^\s]+)\s+(HTTP/\d\.\d)"\s+'       # "METHOD URL PROTOCOL"
    r'(\d{3})\s+'                                     # status code
    r'(\d+|-)\s+'                                     # bytes
    r'"([^"]*)"\s+'                                   # "referrer"
    r'"([^"]*)"$'                                     # "user-agent"
)


class TestLogGenerator(unittest.TestCase):

    def test_parse_size_str(self):
        """Tests parsing of human-readable byte sizes."""
        self.assertEqual(parse_size_str("100B"), 100)
        self.assertEqual(parse_size_str("10KB"), 10 * 1024)
        self.assertEqual(parse_size_str("50MB"), 50 * 1024 * 1024)
        self.assertEqual(parse_size_str("1GB"), 1024 * 1024 * 1024)
        with self.assertRaises(ValueError):
            parse_size_str("invalid_size")
        with self.assertRaises(ValueError):
            parse_size_str("100TB")

    def test_clean_lines_match_combined_log_format(self):
        """Tests that when malformed_rate=0, all lines match Combined Log Format."""
        gen = LogGenerator(seed=123, malformed_rate=0.0)
        base_date = datetime.date(2026, 11, 10)

        for _ in range(500):
            ts = gen.generate_timestamp(base_date)
            line = gen.generate_valid_line(ts)
            match = COMBINED_LOG_REGEX.match(line)
            self.assertIsNotNone(match, f"Line failed Combined Log regex: {line}")
            
            ip, ident, auth, timestamp, method, url, proto, status, bytes_val, ref, ua = match.groups()
            self.assertEqual(ident, "-")
            self.assertEqual(auth, "-")
            self.assertIn(method, ["GET", "POST", "HEAD", "PUT", "DELETE"])
            self.assertEqual(proto, "HTTP/1.1")
            self.assertTrue(100 <= int(status) <= 599)
            if bytes_val != "-":
                self.assertTrue(int(bytes_val) >= 0)

    def test_malformed_lines_injected(self):
        """Tests that malformed lines are generated and fail the Combined Log regex."""
        gen = LogGenerator(seed=456, malformed_rate=1.0)
        base_date = datetime.date(2026, 11, 10)

        malformed_count = 0
        for _ in range(100):
            ts = gen.generate_timestamp(base_date)
            line = gen.generate_malformed_line(ts)
            if not COMBINED_LOG_REGEX.match(line):
                malformed_count += 1

        self.assertGreater(malformed_count, 90, "At least 90% of injected malformed lines must fail regex")

    def test_zipf_page_popularity(self):
        """Tests that page requests follow Zipfian frequency ranking."""
        gen = LogGenerator(seed=789, malformed_rate=0.0, bot_share=0.0)
        base_date = datetime.date(2026, 11, 10)

        url_counts = {}
        for _ in range(5000):
            ts = gen.generate_timestamp(base_date)
            line = gen.generate_valid_line(ts)
            match = COMBINED_LOG_REGEX.match(line)
            url = match.group(6)
            url_counts[url] = url_counts.get(url, 0) + 1

        # The root page '/' (weight 100) should have more visits than '/products' (weight 75)
        # and significantly more than long-tail page '/contact' (weight 10)
        self.assertIn("/", url_counts)
        self.assertIn("/products", url_counts)
        self.assertIn("/contact", url_counts)
        self.assertGreater(url_counts["/"], url_counts["/products"])
        self.assertGreater(url_counts["/products"], url_counts["/contact"])

    def test_diurnal_traffic_distribution(self):
        """Tests diurnal curve has daytime peak and nighttime trough."""
        gen = LogGenerator(seed=101)
        peak_weight = gen.get_diurnal_weight(14) # 2 PM UTC
        trough_weight = gen.get_diurnal_weight(3) # 3 AM UTC

        self.assertGreater(peak_weight, trough_weight * 2.0, "Daytime peak must be > 2x night trough")

        base_date = datetime.date(2026, 11, 10)
        hours = [gen.generate_timestamp(base_date).hour for _ in range(3000)]
        day_count = sum(1 for h in hours if 11 <= h <= 17)
        night_count = sum(1 for h in hours if 1 <= h <= 6)

        self.assertGreater(day_count, night_count, "Daytime hours must generate more logs than nighttime")

    def test_bot_traffic_share(self):
        """Tests that bot share is approximately equal to configured percentage."""
        target_bot_share = 0.15
        gen = LogGenerator(seed=202, bot_share=target_bot_share, malformed_rate=0.0)
        base_date = datetime.date(2026, 11, 10)

        bot_count = 0
        total = 3000
        bot_signatures = ["Googlebot", "bingbot", "Baiduspider", "curl", "python-requests"]

        for _ in range(total):
            ts = gen.generate_timestamp(base_date)
            line = gen.generate_valid_line(ts)
            match = COMBINED_LOG_REGEX.match(line)
            ua = match.group(11)
            if any(sig in ua for sig in bot_signatures):
                bot_count += 1

        measured_share = bot_count / total
        self.assertAlmostEqual(measured_share, target_bot_share, delta=0.04)

    def test_country_ip_prefixes(self):
        """Tests that generated IPs conform to expected synthetic country prefixes."""
        gen = LogGenerator(seed=303)
        valid_prefixes = {c[1] for c in DEFAULT_CONFIG["countries"]}

        for _ in range(500):
            ip = gen.generate_ip()
            first_octet = ip.split(".")[0]
            self.assertIn(first_octet, valid_prefixes)

    def test_deterministic_seed(self):
        """Tests that same seed produces identical log lines."""
        gen1 = LogGenerator(seed=999)
        gen2 = LogGenerator(seed=999)
        base_date = datetime.date(2026, 11, 10)

        lines1 = [gen1.generate_log_line(gen1.generate_timestamp(base_date)) for _ in range(50)]
        lines2 = [gen2.generate_log_line(gen2.generate_timestamp(base_date)) for _ in range(50)]

        self.assertEqual(lines1, lines2)

    def test_dataset_generation_file_creation(self):
        """Tests dataset generation with date-partitioned folder structure."""
        with tempfile.TemporaryDirectory() as tmpdir:
            gen = LogGenerator(seed=42)
            start_date = datetime.date(2026, 11, 1)
            lines, bytes_written = gen.generate_dataset(
                output_dir=Path(tmpdir),
                start_date=start_date,
                num_days=3,
                lines_per_day=50,
            )

            self.assertEqual(lines, 150)
            self.assertGreater(bytes_written, 0)

            # Check partitions exist
            for d in range(3):
                dt_str = (start_date + datetime.timedelta(days=d)).strftime("%Y-%m-%d")
                partition_path = Path(tmpdir) / f"dt={dt_str}" / "access.log"
                self.assertTrue(partition_path.exists(), f"Partition {partition_path} missing")
                with open(partition_path, "r", encoding="utf-8") as f:
                    part_lines = f.readlines()
                    self.assertEqual(len(part_lines), 50)


if __name__ == "__main__":
    unittest.main()

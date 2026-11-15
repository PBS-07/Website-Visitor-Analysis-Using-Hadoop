#!/usr/bin/env python3
"""
Synthetic Web Server Access Log Generator
Produces realistic Apache Combined Log Format data with:
- Configurable volume (target file size or line counts)
- Zipf-distributed page popularity
- Diurnal (hourly) traffic patterns
- Configurable bot traffic share
- Realistic HTTP status codes and response byte sizes
- Realistic User-Agents (Desktop, Mobile, Tablet, Bot)
- Deterministic IP-to-Country synthetic mapping
- Controlled malformed line injection for dead-letter testing
"""

import argparse
import datetime
import math
import os
import random
import re
import sys
from pathlib import Path

# Embedded default configuration mirroring config.yaml
DEFAULT_CONFIG = {
    "seed": 42,
    "bot_share": 0.10,
    "malformed_rate": 0.005,
    "zipf_alpha": 1.3,
    "pages": [
        ("/", 100),
        ("/products", 75),
        ("/products/laptop", 50),
        ("/products/phone", 45),
        ("/products/headphones", 35),
        ("/products/tablet", 30),
        ("/cart", 40),
        ("/checkout", 20),
        ("/login", 25),
        ("/register", 15),
        ("/blog", 30),
        ("/blog/hadoop-performance-tuning", 20),
        ("/about", 15),
        ("/contact", 10),
        ("/api/v1/search", 35),
        ("/api/v1/recommendations", 25),
    ],
    "static_assets": [
        ("/css/main.css", 60),
        ("/css/theme.css", 40),
        ("/js/app.js", 60),
        ("/js/analytics.js", 50),
        ("/images/logo.png", 40),
        ("/images/hero-banner.jpg", 30),
        ("/favicon.ico", 35),
    ],
    "error_urls": [
        ("/admin/login.php", 15),
        ("/wp-login.php", 10),
        ("/.env", 8),
        ("/legacy/catalog-v1", 12),
        ("/hidden/backup.zip", 5),
    ],
    "status_weights": [
        (200, 0.84),
        (301, 0.03),
        (304, 0.05),
        (400, 0.01),
        (403, 0.01),
        (404, 0.04),
        (500, 0.015),
        (502, 0.003),
        (503, 0.002),
    ],
    "methods": [
        ("GET", 0.82),
        ("POST", 0.12),
        ("HEAD", 0.04),
        ("PUT", 0.01),
        ("DELETE", 0.01),
    ],
    "referrers": [
        ("-", 30),
        ("https://www.google.com/", 35),
        ("https://www.bing.com/", 10),
        ("https://duckduckgo.com/", 5),
        ("https://t.co/", 8),
        ("https://www.linkedin.com/", 5),
        ("https://www.reddit.com/", 4),
        ("https://news.ycombinator.com/", 3),
    ],
    "countries": [
        ("US", "13", 35),
        ("IN", "103", 25),
        ("GB", "81", 12),
        ("DE", "194", 8),
        ("CA", "142", 6),
        ("JP", "133", 5),
        ("FR", "80", 4),
        ("AU", "139", 3),
        ("BR", "177", 2),
    ],
    "user_agents": {
        "desktop": [
            ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", 35),
            ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", 15),
            ("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0", 10),
            ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15", 8),
            ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", 4),
        ],
        "mobile": [
            ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1", 12),
            ("Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36", 10),
        ],
        "tablet": [
            ("Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1", 3),
            ("Mozilla/5.0 (Linux; Android 13; SM-X900) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", 2),
        ],
        "bot": [
            ("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", 40),
            ("Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)", 25),
            ("Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)", 15),
            ("curl/8.4.0", 10),
            ("python-requests/2.31.0", 10),
        ],
    },
}

MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

def parse_size_str(size_str: str) -> int:
    """Parses human-readable size strings like '100MB', '1GB', '500KB' into bytes."""
    size_str = size_str.strip().upper()
    units = {"B": 1, "KB": 1024, "MB": 1024**2, "GB": 1024**3}
    match = re.match(r"^(\d+(?:\.\d+)?)\s*([A-Z]+)$", size_str)
    if not match:
        raise ValueError(f"Invalid size string: {size_str}")
    val, unit = float(match.group(1)), match.group(2)
    if unit not in units:
        raise ValueError(f"Unknown size unit: {unit}")
    return int(val * units[unit])

class LogGenerator:
    def __init__(self, seed: int = 42, bot_share: float = 0.10, malformed_rate: float = 0.005):
        self.seed = seed
        self.bot_share = bot_share
        self.malformed_rate = malformed_rate
        self.rng = random.Random(seed)
        
        # Precompute weighted choices
        self._setup_distributions()

    def _setup_distributions(self):
        # Country IP prefixes
        self.countries = [c[0] for c in DEFAULT_CONFIG["countries"]]
        self.country_prefixes = [c[1] for c in DEFAULT_CONFIG["countries"]]
        self.country_weights = [c[2] for c in DEFAULT_CONFIG["countries"]]

        # URLs
        pages = DEFAULT_CONFIG["pages"]
        static = DEFAULT_CONFIG["static_assets"]
        errors = DEFAULT_CONFIG["error_urls"]
        
        all_urls = pages + static + errors
        self.urls = [u[0] for u in all_urls]
        self.url_weights = [u[1] for u in all_urls]

        # Status codes
        self.status_codes = [s[0] for s in DEFAULT_CONFIG["status_weights"]]
        self.status_weights = [s[1] for s in DEFAULT_CONFIG["status_weights"]]

        # Methods
        self.methods = [m[0] for m in DEFAULT_CONFIG["methods"]]
        self.method_weights = [m[1] for m in DEFAULT_CONFIG["methods"]]

        # Referrers
        self.referrers = [r[0] for r in DEFAULT_CONFIG["referrers"]]
        self.referrer_weights = [r[1] for r in DEFAULT_CONFIG["referrers"]]

        # User agents by category
        self.human_uas = []
        self.human_weights = []
        for cat in ["desktop", "mobile", "tablet"]:
            for ua, w in DEFAULT_CONFIG["user_agents"][cat]:
                self.human_uas.append(ua)
                self.human_weights.append(w)

        self.bot_uas = [b[0] for b in DEFAULT_CONFIG["user_agents"]["bot"]]
        self.bot_weights = [b[1] for b in DEFAULT_CONFIG["user_agents"]["bot"]]

    def generate_ip(self) -> str:
        """Generates a realistic synthetic IPv4 address deterministically tied to a country prefix."""
        prefix = self.rng.choices(self.country_prefixes, weights=self.country_weights, k=1)[0]
        b = self.rng.randint(1, 254)
        c = self.rng.randint(1, 254)
        d = self.rng.randint(1, 254)
        return f"{prefix}.{b}.{c}.{d}"

    def get_diurnal_weight(self, hour: int) -> float:
        """Returns relative traffic weight for a given hour (0-23) based on diurnal cycle."""
        # Daytime peak around 14:00 UTC, trough around 03:00 UTC
        peak = 14.0
        sigma = 4.5
        val = 0.2 + 0.8 * math.exp(-((hour - peak) ** 2) / (2 * (sigma ** 2)))
        return val

    def generate_timestamp(self, base_date: datetime.date) -> datetime.datetime:
        """Generates a timestamp within base_date sampled according to the diurnal cycle."""
        hour_weights = [self.get_diurnal_weight(h) for h in range(24)]
        hour = self.rng.choices(range(24), weights=hour_weights, k=1)[0]
        minute = self.rng.randint(0, 59)
        second = self.rng.randint(0, 59)
        return datetime.datetime(base_date.year, base_date.month, base_date.day, hour, minute, second)

    def generate_valid_line(self, timestamp: datetime.datetime) -> str:
        """Generates a single valid Apache Combined Log Format string."""
        ip = self.generate_ip()
        
        # Decide if bot or human
        is_bot = (self.rng.random() < self.bot_share)
        if is_bot:
            ua = self.rng.choices(self.bot_uas, weights=self.bot_weights, k=1)[0]
            # Bots often request robots.txt or sitemap or specific pages
            if self.rng.random() < 0.25:
                url = self.rng.choice(["/robots.txt", "/sitemap.xml"])
            else:
                url = self.rng.choices(self.urls, weights=self.url_weights, k=1)[0]
            method = "GET"
            ref = "-"
        else:
            ua = self.rng.choices(self.human_uas, weights=self.human_weights, k=1)[0]
            url = self.rng.choices(self.urls, weights=self.url_weights, k=1)[0]
            method = self.rng.choices(self.methods, weights=self.method_weights, k=1)[0]
            ref = self.rng.choices(self.referrers, weights=self.referrer_weights, k=1)[0]

        # Determine status code based on URL
        if url in [e[0] for e in DEFAULT_CONFIG["error_urls"]]:
            status = self.rng.choice([404, 403, 500])
        elif url in ["/robots.txt", "/sitemap.xml"]:
            status = 200
        else:
            status = self.rng.choices(self.status_codes, weights=self.status_weights, k=1)[0]

        # Determine response size
        if status in [301, 304, 404]:
            bytes_sent = self.rng.randint(0, 400)
        elif status >= 500:
            bytes_sent = self.rng.randint(200, 1500)
        elif url.endswith((".css", ".js")):
            bytes_sent = self.rng.randint(8000, 65000)
        elif url.endswith((".png", ".jpg")):
            bytes_sent = self.rng.randint(20000, 250000)
        else:
            bytes_sent = self.rng.randint(1200, 18000)

        # Format timestamp: 10/Nov/2026:09:15:32 +0000
        month_str = MONTH_NAMES[timestamp.month - 1]
        formatted_time = f"{timestamp.day:02d}/{month_str}/{timestamp.year}:{timestamp.hour:02d}:{timestamp.minute:02d}:{timestamp.second:02d} +0000"

        # Construct Combined Log Format
        line = f'{ip} - - [{formatted_time}] "{method} {url} HTTP/1.1" {status} {bytes_sent} "{ref}" "{ua}"'
        return line

    def generate_malformed_line(self, timestamp: datetime.datetime) -> str:
        """Injects a malformed log line to test cleaner dead-letter handling (FR-5)."""
        valid = self.generate_valid_line(timestamp)
        error_type = self.rng.randint(1, 5)

        if error_type == 1:
            # Corrupted timestamp (missing brackets)
            return valid.replace("[", "").replace("]", "")
        elif error_type == 2:
            # Truncated HTTP request
            parts = valid.split('"')
            if len(parts) >= 2:
                parts[1] = "INVALID_REQUEST_LINE_WITHOUT_METHOD"
                return '"'.join(parts)
            return "MALFORMED_LINE_TRUNCATED"
        elif error_type == 3:
            # Non-numeric status code
            return re.sub(r'HTTP/1\.1" \d+ ', 'HTTP/1.1" BADSTATUS ', valid)
        elif error_type == 4:
            # Missing quotes on User-Agent
            return valid[:-1] # strip trailing quote
        else:
            # Pure binary junk
            return "###CORRUPT_BUFFER_OVERFLOW_PACKET_DUMP###"

    def generate_log_line(self, timestamp: datetime.datetime) -> str:
        """Generates either a valid or malformed log line based on configured rate."""
        if self.rng.random() < self.malformed_rate:
            return self.generate_malformed_line(timestamp)
        return self.generate_valid_line(timestamp)

    def generate_dataset(
        self,
        output_dir: Path,
        start_date: datetime.date,
        num_days: int = 7,
        target_bytes: int = None,
        lines_per_day: int = None,
    ):
        """Generates date-partitioned log files matching PRD Section 4.3 layout."""
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        print(f"Starting log generation:")
        print(f"  Output directory: {output_dir}")
        print(f"  Date range: {start_date} to {start_date + datetime.timedelta(days=num_days-1)} ({num_days} days)")
        print(f"  Bot share: {self.bot_share * 100:.1f}%")
        print(f"  Malformed error rate: {self.malformed_rate * 100:.2f}%")
        if target_bytes:
            print(f"  Target total size: {target_bytes / (1024*1024):.2f} MB")

        total_bytes_written = 0
        total_lines_written = 0

        # If target_bytes is specified, apportion evenly across days
        daily_target_bytes = (target_bytes // num_days) if target_bytes else None

        for d in range(num_days):
            current_date = start_date + datetime.timedelta(days=d)
            dt_str = current_date.strftime("%Y-%m-%d")
            day_dir = output_dir / f"dt={dt_str}"
            day_dir.mkdir(parents=True, exist_ok=True)
            log_file = day_dir / "access.log"

            day_bytes = 0
            day_lines = 0

            with open(log_file, "w", encoding="utf-8", newline="\n") as f:
                while True:
                    # Check stopping condition for the day
                    if daily_target_bytes and day_bytes >= daily_target_bytes:
                        break
                    if lines_per_day and day_lines >= lines_per_day:
                        break
                    if not daily_target_bytes and not lines_per_day:
                        if day_lines >= 10000: # sensible default
                            break

                    ts = self.generate_timestamp(current_date)
                    line = self.generate_log_line(ts) + "\n"
                    f.write(line)

                    day_bytes += len(line.encode("utf-8"))
                    day_lines += 1

            total_bytes_written += day_bytes
            total_lines_written += day_lines
            print(f"  [Day {d+1}/{num_days}] {dt_str}: {day_lines:,} lines ({day_bytes / (1024*1024):.2f} MB) -> {log_file}")

        print(f"Generation Complete! Total: {total_lines_written:,} lines ({total_bytes_written / (1024*1024):.2f} MB)")
        return total_lines_written, total_bytes_written

def main():
    parser = argparse.ArgumentParser(description="Synthetic Apache Combined Log Generator for Hadoop Pipeline")
    parser.add_argument("--size", type=str, default="50MB", help="Target dataset size (e.g., 10MB, 100MB, 1GB, 5GB)")
    parser.add_argument("--days", type=int, default=7, help="Number of daily partitions to generate")
    parser.add_argument("--lines", type=int, default=None, help="Specific line count per day (overrides --size)")
    parser.add_argument("--start-date", type=str, default="2026-11-01", help="Start date (YYYY-MM-DD)")
    parser.add_argument("--bot-share", type=float, default=0.10, help="Fraction of bot traffic (0.0 - 1.0)")
    parser.add_argument("--malformed-rate", type=float, default=0.005, help="Fraction of malformed lines (0.0 - 1.0)")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for deterministic generation")
    parser.add_argument("--out-dir", type=str, default="./data/raw", help="Target output directory")

    args = parser.parse_args()

    start_date = datetime.date.fromisoformat(args.start_date)
    target_bytes = parse_size_str(args.size) if not args.lines else None

    gen = LogGenerator(
        seed=args.seed,
        bot_share=args.bot_share,
        malformed_rate=args.malformed_rate,
    )

    gen.generate_dataset(
        output_dir=Path(args.out_dir),
        start_date=start_date,
        num_days=args.days,
        target_bytes=target_bytes,
        lines_per_day=args.lines,
    )

if __name__ == "__main__":
    main()


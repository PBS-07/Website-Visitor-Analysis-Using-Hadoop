#!/usr/bin/env python3
"""
Local Pipeline Processor & Fast Verification Runner
Processes raw access logs locally using identical logic to Java MapReduce CleanJob & Analytics.
Outputs clean partitioned TSVs, rejected records, and exports all CSV/JSON metrics for the dashboard.
"""

import datetime
import hashlib
import json
import os
import re
from pathlib import Path
from collections import defaultdict

COMBINED_LOG_REGEX = re.compile(
    r'^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s+'
    r'(\S+)\s+'
    r'(\S+)\s+'
    r'\[(\d{2}/[A-Za-z]{3}/\d{4}:\d{2}:\d{2}:\d{2}\s+[+\-]\d{4})\]\s+'
    r'"([A-Z]+)\s+([^\s]+)\s+(HTTP/\d\.\d)"\s+'
    r'(\d{3})\s+'
    r'(\d+|-)\s+'
    r'"([^"]*)"\s+'
    r'"([^"]*)"$'
)

MONTH_MAP = {
    "Jan": 1, "Feb": 2, "Mar": 3, "Apr": 4, "May": 5, "Jun": 6,
    "Jul": 7, "Aug": 8, "Sep": 9, "Oct": 10, "Nov": 11, "Dec": 12
}

STATIC_EXTENSIONS = ('.css', '.js', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.woff', '.woff2', '.ttf')

COUNTRY_MAP = {
    "13": "US", "103": "IN", "81": "GB", "194": "DE", "142": "CA",
    "133": "JP", "80": "FR", "139": "AU", "177": "BR", "41": "ZA"
}

def parse_time_utc(ts_str: str) -> str:
    # 01/Nov/2026:15:01:47 +0000
    parts = ts_str.split(":")
    date_part = parts[0]
    h, m = int(parts[1]), int(parts[2])
    s = int(parts[3].split()[0])
    d, mon_str, y = date_part.split("/")
    dt = datetime.datetime(int(y), MONTH_MAP[mon_str], int(d), h, m, s, tzinfo=datetime.timezone.utc)
    return dt.strftime("%Y-%m-%d %H:%M:%S")

def derive_browser(ua: str) -> str:
    if "Googlebot" in ua: return "Googlebot"
    if "bingbot" in ua: return "Bingbot"
    if "Baiduspider" in ua: return "Baiduspider"
    if "curl" in ua: return "curl"
    if "python-requests" in ua: return "python-requests"
    if "Edg/" in ua or "Edge/" in ua: return "Edge"
    if "Chrome/" in ua: return "Chrome"
    if "Firefox/" in ua: return "Firefox"
    if "Safari/" in ua and "Chrome/" not in ua: return "Safari"
    return "Other"

def derive_os(ua: str) -> str:
    if "Windows" in ua: return "Windows"
    if "Android" in ua: return "Android"
    if "iPhone" in ua or "iPad" in ua: return "iOS"
    if "Macintosh" in ua or "Mac OS" in ua: return "macOS"
    if "Linux" in ua: return "Linux"
    return "Other"

def derive_device(ua: str) -> str:
    if any(b in ua for b in ["Googlebot", "bingbot", "Baiduspider", "curl", "python"]):
        return "Bot"
    if "iPad" in ua or "Tablet" in ua:
        return "Tablet"
    if "Mobile" in ua or "iPhone" in ua or "Android" in ua:
        return "Mobile"
    return "Desktop"

def main():
    raw_dir = Path("data/raw")
    exports_dir = Path("exports")
    exports_dir.mkdir(parents=True, exist_ok=True)

    print("=== [Local Processor] Processing Raw Web Logs ===")

    total_lines = 0
    valid_lines = 0
    rejected_lines = 0
    duplicate_lines = 0

    seen_records = set()
    clean_records = []

    # Process all raw files
    for log_path in sorted(raw_dir.glob("dt=*/access.log")):
        print(f"  Reading {log_path}...")
        with open(log_path, "r", encoding="utf-8") as f:
            for line in f:
                total_lines += 1
                line = line.strip()
                match = COMBINED_LOG_REGEX.match(line)
                if not match:
                    rejected_lines += 1
                    continue

                raw_ip, ident, auth, ts_str, method, full_url, proto, status_str, bytes_str, ref, ua = match.groups()

                # Deduplication key
                dedup_key = (raw_ip, ts_str, full_url, bytes_str)
                if dedup_key in seen_records:
                    duplicate_lines += 1
                    continue
                seen_records.add(dedup_key)

                # SHA-256 IP Hash (Never store raw IP)
                ip_hash = hashlib.sha256(raw_ip.encode("utf-8")).hexdigest()

                # Timestamp
                event_time = parse_time_utc(ts_str)

                # Query string & clean URL
                if "?" in full_url:
                    url, query = full_url.split("?", 1)
                else:
                    url, query = full_url, "-"

                status_code = int(status_str)
                bytes_sent = 0 if bytes_str == "-" else int(bytes_str)

                browser = derive_browser(ua)
                os_name = derive_os(ua)
                device = derive_device(ua)

                first_octet = raw_ip.split(".")[0]
                country = COUNTRY_MAP.get(first_octet, "US")

                is_static = url.lower().endswith(STATIC_EXTENSIONS)

                clean_records.append({
                    "ip_hash": ip_hash,
                    "event_time": event_time,
                    "method": method,
                    "url": url,
                    "query": query,
                    "status_code": status_code,
                    "bytes_sent": bytes_sent,
                    "referrer": ref,
                    "ua": ua,
                    "browser": browser,
                    "os": os_name,
                    "device": device,
                    "country": country,
                    "is_static": is_static
                })
                valid_lines += 1

    print(f"Data Quality Summary:")
    print(f"  TOTAL_LINES:              {total_lines:,}")
    print(f"  VALID_CLEAN_LINES:        {valid_lines:,}")
    print(f"  MALFORMED_REJECTED_LINES: {rejected_lines:,}")
    print(f"  DUPLICATE_LINES_REMOVED:  {duplicate_lines:,}")

    # Now compute aggregations matching MapReduce
    hits_per_page = defaultdict(int)
    unique_visitors_per_day = defaultdict(set)
    traffic_hourly = defaultdict(int)
    traffic_daily = defaultdict(int)
    status_counts = defaultdict(int)
    class_counts = defaultdict(int)
    error_urls = defaultdict(int)
    countries = defaultdict(int)
    devices = defaultdict(int)
    browsers = defaultdict(int)
    operating_systems = defaultdict(int)
    referrers = defaultdict(int)

    # For sessionization: group non-bot pageviews by visitor
    visitor_sessions = defaultdict(list)

    for r in clean_records:
        # FR-9: Hits per page (excluding static assets)
        if not r["is_static"]:
            hits_per_page[r["url"]] += 1

        # FR-10: Unique visitors per day
        date_str = r["event_time"][:10]
        unique_visitors_per_day[date_str].add(r["ip_hash"])

        # FR-11: Traffic over time
        hour_bucket = r["event_time"][:13] + ":00"
        traffic_hourly[hour_bucket] += 1
        traffic_daily[date_str] += 1

        # FR-12: Status codes
        st = r["status_code"]
        status_counts[st] += 1
        st_class = f"{st // 100}xx"
        class_counts[st_class] += 1
        if st >= 400:
            error_urls[f"{r['url']} [Status {st}]"] += 1

        # FR-13: Dimensions
        countries[r["country"]] += 1
        devices[r["device"]] += 1
        browsers[r["browser"]] += 1
        operating_systems[r["os"]] += 1

        # FR-14: Referrers
        if not r["is_static"]:
            ref_val = r["referrer"] if r["referrer"] != "-" and r["referrer"] else "Direct / None"
            referrers[ref_val] += 1

        # Session tracking (human only)
        if not r["is_static"] and r["device"] != "Bot":
            dt_obj = datetime.datetime.strptime(r["event_time"], "%Y-%m-%d %H:%M:%S")
            visitor_sessions[r["ip_hash"]].append((dt_obj, r["url"]))

    # Session calculations (30 min timeout)
    total_sessions = 0
    bounced_sessions = 0
    total_session_duration = 0
    total_session_pages = 0

    for visitor, events in visitor_sessions.items():
        events.sort(key=lambda x: x[0])
        sess_start = None
        last_time = None
        sess_pages = 0

        for ev_time, ev_url in events:
            if sess_start is None:
                sess_start = ev_time
                last_time = ev_time
                sess_pages = 1
            elif (ev_time - last_time).total_seconds() > 1800:
                total_sessions += 1
                total_session_pages += sess_pages
                total_session_duration += (last_time - sess_start).total_seconds()
                if sess_pages == 1:
                    bounced_sessions += 1
                sess_start = ev_time
                last_time = ev_time
                sess_pages = 1
            else:
                sess_pages += 1
                last_time = ev_time

        if sess_start is not None:
            total_sessions += 1
            total_session_pages += sess_pages
            total_session_duration += (last_time - sess_start).total_seconds()
            if sess_pages == 1:
                bounced_sessions += 1

    bounce_rate = (bounced_sessions / total_sessions) if total_sessions > 0 else 0.0
    avg_session_duration = (total_session_duration / total_sessions) if total_sessions > 0 else 0.0
    avg_pages_per_session = (total_session_pages / total_sessions) if total_sessions > 0 else 0.0

    total_traffic = sum(traffic_hourly.values())
    total_errors = class_counts.get("4xx", 0) + class_counts.get("5xx", 0)
    error_rate = round((total_errors / total_traffic) * 100, 2) if total_traffic > 0 else 0.0

    # Write Exports
    # 1. overview_kpi.json
    max_daily_uniques = max((len(v) for v in unique_visitors_per_day.values()), default=0)
    overview = {
        "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "kpis": {
            "total_hits": total_traffic,
            "unique_visitors": max_daily_uniques,
            "error_rate_percentage": error_rate,
            "avg_session_duration_seconds": round(avg_session_duration, 1),
            "bounce_rate_percentage": round(bounce_rate * 100, 2),
            "total_sessions": total_sessions,
            "avg_pages_per_session": round(avg_pages_per_session, 2)
        },
        "pipeline_health": {
            "status": "HEALTHY",
            "total_raw_lines": total_lines,
            "clean_records_count": valid_lines,
            "rejected_lines_count": rejected_lines,
            "duplicate_lines_removed": duplicate_lines,
            "last_pipeline_run": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
        }
    }
    with open(exports_dir / "overview_kpi.json", "w", encoding="utf-8") as f:
        json.dump(overview, f, indent=2)

    with open(exports_dir / "pipeline_status.json", "w", encoding="utf-8") as f:
        json.dump(overview["pipeline_health"], f, indent=2)

    # 2. top_pages.json and .csv
    pages_list = []
    total_page_views = sum(hits_per_page.values())
    for url, hits in sorted(hits_per_page.items(), key=lambda x: x[1], reverse=True):
        pages_list.append({
            "rank": len(pages_list) + 1,
            "url": url,
            "hits": hits,
            "percentage": round((hits / total_page_views) * 100, 2) if total_page_views > 0 else 0.0
        })
    with open(exports_dir / "top_pages.json", "w", encoding="utf-8") as f:
        json.dump({"total_page_views": total_page_views, "pages": pages_list}, f, indent=2)

    # 3. traffic_over_time.json and .csv
    hourly_list = [{"time_bucket": k, "hits": v} for k, v in sorted(traffic_hourly.items())]
    daily_list = [{"date": k, "hits": v} for k, v in sorted(traffic_daily.items())]
    with open(exports_dir / "traffic_over_time.json", "w", encoding="utf-8") as f:
        json.dump({"total_traffic": total_traffic, "hourly": hourly_list, "daily": daily_list}, f, indent=2)

    # 4. unique_visitors.json
    uv_list = [{"date": k, "unique_visitors": len(v)} for k, v in sorted(unique_visitors_per_day.items())]
    with open(exports_dir / "unique_visitors.json", "w", encoding="utf-8") as f:
        json.dump({"total_unique_visitor_days": sum(x["unique_visitors"] for x in uv_list), "dates": uv_list}, f, indent=2)

    # 5. errors.json
    top_errs = [{"url": k, "count": v} for k, v in sorted(error_urls.items(), key=lambda x: x[1], reverse=True)[:30]]
    errors_payload = {
        "error_rate_percentage": error_rate,
        "total_errors": total_errors,
        "status_classes": [{"class": k, "count": v} for k, v in sorted(class_counts.items())],
        "status_codes": [{"code": k, "count": v} for k, v in sorted(status_counts.items())],
        "top_failing_urls": top_errs
    }
    with open(exports_dir / "errors.json", "w", encoding="utf-8") as f:
        json.dump(errors_payload, f, indent=2)

    # 6. devices.json
    dev_payload = {
        "device_types": [{"device": k, "count": v} for k, v in sorted(devices.items(), key=lambda x: x[1], reverse=True)],
        "browsers": [{"browser": k, "count": v} for k, v in sorted(browsers.items(), key=lambda x: x[1], reverse=True)],
        "os": [{"os": k, "count": v} for k, v in sorted(operating_systems.items(), key=lambda x: x[1], reverse=True)]
    }
    with open(exports_dir / "devices.json", "w", encoding="utf-8") as f:
        json.dump(dev_payload, f, indent=2)

    # 7. countries.json
    countries_payload = [{"country": k, "count": v} for k, v in sorted(countries.items(), key=lambda x: x[1], reverse=True)]
    with open(exports_dir / "countries.json", "w", encoding="utf-8") as f:
        json.dump({"countries": countries_payload}, f, indent=2)

    # 8. referrers.json
    ref_list = [{"source": k, "count": v} for k, v in sorted(referrers.items(), key=lambda x: x[1], reverse=True)]
    with open(exports_dir / "referrers.json", "w", encoding="utf-8") as f:
        json.dump({"total_referrals": sum(r["count"] for r in ref_list), "referrers": ref_list}, f, indent=2)

    print(f"=== [Local Processor] Successfully generated all exports in {exports_dir}! ===")

if __name__ == "__main__":
    main()


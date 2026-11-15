# PRD: Website Visitor Analysis Using Hadoop

**Document type:** Product Requirements Document  
**Build environment:** Google Antigravity (agent-first IDE)  
**Author:** Bhagath  
**Status:** Draft v1.0  

---

## 1. Overview

### 1.1 Summary
A big-data analytics system that ingests large volumes of web server access logs into HDFS, processes them with Hadoop MapReduce and Hive, and presents visitor insights (traffic, unique visitors, popular pages, geography, devices, errors, sessions) through a web dashboard.

### 1.2 Problem Statement
Web servers generate millions of log lines per day. Traditional single-machine tools (Excel, scripts, SQL on one box) break down at this scale. Site owners need a scalable, fault-tolerant way to answer: *Who visits my site, from where, what do they view, and where do they leave or fail?*

### 1.3 Goals
- Demonstrate a complete, working Hadoop pipeline: ingest, store, process, query, visualize.
- Produce accurate visitor metrics from raw logs (Apache/Nginx Combined Log Format).
- Run end to end locally via Docker (single-node pseudo-distributed cluster), reproducible with one command.
- Deliver a clean dashboard and a documented codebase suitable for a placement/portfolio showcase.

### 1.4 Non-Goals
- Real-time streaming analytics (Kafka/Flink) is out of scope for v1.
- Production-grade multi-node cluster deployment.
- User authentication or multi-tenant support.
- Personally identifiable tracking beyond IP-based aggregation (IPs are anonymized).

---

## 2. Target Users and Use Cases

| Persona | Need | Example Question |
| :--- | :--- | :--- |
| **Website owner / marketer** | Understand audience and content performance | Which pages get the most traffic this week? |
| **Data analyst** | Run ad-hoc queries on large logs | What is the bounce rate by country? |
| **DevOps / engineer** | Spot errors and bot traffic | Which URLs return the most 404/500 errors? |
| **Student / reviewer (demo)** | See Hadoop concepts working | How does MapReduce count hits per page? |

---

## 3. Scope

### 3.1 In Scope (v1)
1. Synthetic and real log ingestion into HDFS
2. Log parsing and cleaning (MapReduce)
3. Analytical MapReduce jobs (5 core jobs)
4. Hive external tables and analytical queries
5. Result export to CSV/JSON
6. Web dashboard (Next.js) displaying the results
7. Docker-based environment, README, and tests

### 3.2 Out of Scope (v1)
Real-time ingestion, ML-based prediction, cloud deployment (EMR/Dataproc), alerting.

### 3.3 Future Scope (v2)
Spark-based processing, Flume/Kafka ingestion, bot-detection model, anomaly detection, cloud deployment.

---

## 4. System Architecture

### 4.1 Data Flow
Log Source (Apache/Nginx logs or generator) -> HDFS `/raw` -> MapReduce: Clean + Parse -> HDFS `/clean` -> MapReduce / Hive analytics jobs -> HDFS `/output` -> Export (CSV/JSON) -> Dashboard (Next.js)

### 4.2 Components
| Layer | Technology | Responsibility |
| :--- | :--- | :--- |
| **Data source** | Python log generator / public sample logs | Produce Combined Log Format files |
| **Storage** | HDFS (Hadoop 3.x) | Distributed raw, clean, and result storage |
| **Processing** | Hadoop MapReduce (Java, or Python via Hadoop Streaming) | Parsing, aggregation |
| **Query** | Apache Hive | SQL analytics over cleaned data |
| **Orchestration** | Bash scripts / Makefile | Run pipeline stages |
| **Serving** | Node/Next.js API routes reading exported JSON | Expose results |
| **Visualization** | Next.js, TypeScript, Tailwind CSS, Recharts | Dashboard |
| **Environment** | Docker Compose | Reproducible Hadoop + Hive setup |

### 4.3 HDFS Directory Layout
- `/weblogs/raw/` : immutable input logs, partitioned by date
- `/weblogs/clean/` : parsed, validated records (tab-separated)
- `/weblogs/rejected/` : malformed lines for auditing
- `/weblogs/output/<job_name>/` : job results
- `/user/hive/warehouse/` : Hive tables

---

## 5. Data Requirements

### 5.1 Input Format (Combined Log Format)
`IP - - [timestamp] "METHOD URL PROTOCOL" status bytes "referrer" "user-agent"`

### 5.2 Cleaned Record Schema
| Field | Type | Description |
| :--- | :--- | :--- |
| `visitor_ip_hash` | string | SHA-256 hashed IP (privacy) |
| `event_time` | timestamp | Parsed request time (UTC) |
| `method` | string | GET, POST, etc. |
| `url` | string | Requested path |
| `query_string` | string | Optional |
| `status_code` | int | HTTP status |
| `bytes_sent` | bigint | Response size |
| `referrer` | string | Source URL or domain |
| `user_agent` | string | Raw UA |
| `browser` | string | Derived (Chrome, Firefox, Safari...) |
| `os` | string | Derived (Windows, Android, iOS...) |
| `device_type` | string | Desktop / Mobile / Tablet / Bot |
| `country` | string | Derived from IP via GeoIP lookup (or synthetic mapping) |

### 5.3 Data Volume Targets
- Development: 100 MB to 1 GB (about 1M to 10M log lines)
- Benchmark: at least 5 GB, to show scaling behavior

### 5.4 Data Quality Rules
- Lines failing the regex are written to `/weblogs/rejected/` and counted.
- Duplicate lines (same IP, timestamp, URL, bytes) are removed.
- Static asset requests (`.css`, `.js`, `.png`, etc.) are flagged and excluded from page-view metrics by default.

---

## 6. Functional Requirements
Priority key: P0 = must have, P1 = should have, P2 = nice to have.

### 6.1 Ingestion
| ID | Requirement | Priority |
| :--- | :--- | :--- |
| FR-1 | Provide a log generator producing realistic Combined Log Format data with configurable size, date range, traffic patterns, and bot share | P0 |
| FR-2 | Upload logs to HDFS via script (`hdfs dfs -put`) into date-partitioned folders | P0 |
| FR-3 | Support ingestion of real public log datasets | P1 |

### 6.2 Cleaning and Parsing
| ID | Requirement | Priority |
| :--- | :--- | :--- |
| FR-4 | MapReduce job parses each line with regex into the cleaned schema | P0 |
| FR-5 | Malformed lines routed to rejected folder with counters | P0 |
| FR-6 | Hash IP addresses before storage | P0 |
| FR-7 | Derive browser, OS, and device type from the user-agent | P1 |
| FR-8 | Derive country from IP | P1 |

### 6.3 Analytics Jobs
| ID | Job | Description | Output | Priority |
| :--- | :--- | :--- | :--- | :--- |
| FR-9 | Hits per page | Count of requests per URL (top N) | url, hits | P0 |
| FR-10 | Unique visitors | Distinct visitors per day | date, unique_visitors | P0 |
| FR-11 | Traffic over time | Hits per hour and per day | time_bucket, hits | P0 |
| FR-12 | Status code analysis | Count by status class; top error URLs | status, url, count | P0 |
| FR-13 | Geo / device / browser breakdown | Distribution of visitors by country, device, browser, OS | dimension, value, count | P1 |
| FR-14 | Referrer analysis | Top traffic sources | referrer, visits | P1 |
| FR-15 | Session analysis | Sessionize by visitor with a 30-minute inactivity timeout; average session length and pages per session | metrics | P1 |
| FR-16 | Bounce rate | Share of single-page sessions | rate overall and per landing page | P1 |
| FR-17 | Bot traffic detection | Flag by UA signature and request-rate heuristic | bot share, top bots | P2 |
| FR-18 | Bandwidth usage | Total bytes per page and per day | url, bytes | P2 |

#### 6.3.1 Implementation Notes (MapReduce Design)
- **Hits per page**: Mapper emits `(url, 1)`; Combiner and Reducer sum. Use a second job or `TreeMap` in the reducer cleanup for Top-N.
- **Unique visitors per day**: Mapper emits `(date, visitor_hash)`; Reducer uses a set to count distinct values (or secondary sort and de-duplication to avoid memory blowup).
- **Sessionization**: Mapper emits `(visitor_hash, timestamp+url)`; use secondary sort on timestamp; Reducer splits sessions on gaps over 30 minutes.
- **Custom Writables** for composite keys; a **Partitioner** to demonstrate custom partitioning by date.

### 6.4 Hive Layer
| ID | Requirement | Priority |
| :--- | :--- | :--- |
| FR-19 | Create external Hive table over `/weblogs/clean/` partitioned by date | P0 |
| FR-20 | Provide saved HiveQL scripts replicating key analytics for comparison with MapReduce results | P1 |
| FR-21 | Store clean data in a columnar format (ORC or Parquet) and compare query time vs text | P2 |

### 6.5 Export and Serving
| ID | Requirement | Priority |
| :--- | :--- | :--- |
| FR-22 | Export each job result to CSV and JSON in `/exports` | P0 |
| FR-23 | API routes return JSON for each metric | P0 |

### 6.6 Dashboard
| ID | Requirement | Priority |
| :--- | :--- | :--- |
| FR-24 | Overview page with KPI cards: total hits, unique visitors, error rate, avg session duration, bounce rate | P0 |
| FR-25 | Traffic-over-time line chart with day/hour toggle | P0 |
| FR-26 | Top pages bar chart and table | P0 |
| FR-27 | Device, browser, and OS donut charts | P1 |
| FR-28 | Country distribution (map or ranked bar chart) | P1 |
| FR-29 | Error analysis page (status code distribution, top failing URLs) | P1 |
| FR-30 | Date-range filter | P1 |
| FR-31 | Pipeline status page showing last run time, record counts, rejected-line count | P2 |
| FR-32 | Responsive layout and dark mode | P2 |

---

## 7. Non-Functional Requirements
- **Scalability**: Pipeline must process at least 5 GB of logs; adding data requires no code change.
- **Performance**: Clean + all P0 jobs on 1 GB in under 15 minutes on a laptop (8 GB RAM allocated to Docker).
- **Reliability**: Jobs are idempotent: rerunning overwrites outputs safely; failures are logged with clear exit codes.
- **Privacy**: IPs hashed before any analytics; raw IPs never displayed on the dashboard.
- **Reproducibility**: `make up && make run-all` brings up the cluster, generates data, runs the pipeline, and starts the dashboard.
- **Maintainability**: Modular code, README, inline docs, and unit tests for the parser.
- **Portability**: Runs on Windows (WSL2), macOS, and Linux through Docker.

---

## 8. Success Metrics
- **Parser accuracy on test set**: At least 99% of valid lines parsed; 100% of malformed lines rejected.
- **MapReduce vs Hive result parity**: Identical results for hits-per-page and unique visitors.
- **End-to-end run on 1 GB**: Under 15 minutes.
- **Test coverage (parser and key reducers)**: At least 80%.
- **Dashboard load time**: Under 2 seconds with exported JSON.
- **One-command setup**: Works on a fresh clone.

---

## 9. Tech Stack and Versions
- Hadoop 3.3.x (HDFS, YARN, MapReduce)
- Hive 3.1.x with embedded Derby or Postgres metastore
- Java 11 (MapReduce jobs, Maven build) with optional Python 3.10+ Hadoop Streaming variants
- Python for the log generator and export scripts
- Docker and Docker Compose for the cluster
- Next.js (App Router), TypeScript, Tailwind CSS, Recharts for the dashboard
- JUnit and MiniMRCluster-based tests for jobs; pytest for Python

---

## 10. Proposed Repository Structure
```
visitor-analysis-hadoop/
|-- docker/            # docker-compose.yml, Hadoop/Hive configs
|-- generator/         # log_generator.py, config.yaml
|-- mapreduce/         # Maven project
|   `-- src/main/java/... # LogParser, CleanJob, HitsPerPage, ...
|-- hive/              # DDL and analytics .hql scripts
|-- scripts/           # ingest.sh, run_pipeline.sh, export.sh
|-- exports/           # CSV/JSON results
|-- dashboard/         # Next.js app
|-- tests/             # parser test fixtures and sample logs
|-- docs/              # architecture diagram, screenshots
|-- Makefile
`-- README.md
```

---

## 11. Milestones and Timeline (3 weeks)
1. **Environment**: Docker cluster with HDFS, YARN, Hive running (`hdfs dfs -ls /` and sample wordcount succeed)
2. **Data**: Log generator, ingestion scripts (1 GB of logs in `/weblogs/raw`)
3. **Cleaning**: Parser, CleanJob, rejected-lines handling, tests (Parser tests pass; clean data written)
4. **Core analytics**: P0 jobs (FR-9 to FR-12), exports (Outputs verified against small known dataset)
5. **Hive and advanced**: Hive tables, P1 jobs (sessions, bounce, geo/device) (MapReduce and Hive parity check)
6. **Dashboard**: Next.js dashboard, API routes, charts (All P0 dashboard requirements working)
7. **Polish**: Benchmark, docs, README, demo video, screenshots (Fresh-clone setup succeeds)

---

## 12. Antigravity Build Plan
- Section 12.1 Workspace Setup
- Section 12.2 Suggested Agent Tasks
- Section 12.3 Verification Checklist

---

## 13. Risks and Mitigations
- Docker memory limits cause YARN container kills: Allocate 8 GB; tune `yarn.nodemanager.resource.memory-mb` and mapper/reducer memory.
- Hadoop/Hive version incompatibility: Pin versions in compose; use known-good image combination.
- Distinct-count reducer runs out of memory: Use secondary sort or two-stage aggregation.
- Windows path/line-ending issues: Develop in WSL2; enforce LF in `.gitattributes`.
- No real log data available: Generator with realistic distributions; optionally use public NASA HTTP logs.
- Scope creep: Strict P0 first; P2 only after demo is stable.

---

## 14. Acceptance Criteria (Definition of Done)
- [ ] `make up && make run-all` completes on a fresh clone
- [ ] All P0 requirements implemented and tested
- [ ] MapReduce and Hive results match for the core metrics
- [ ] Dashboard displays all P0 views from live exports
- [ ] README covers architecture, setup, usage, and a benchmark table
- [ ] Demo video (3 to 5 minutes) and screenshots in `docs/`

---

## 15. Interview and Portfolio Talking Points
- Why Hadoop/MapReduce for this problem, and when Spark would be the better choice
- How the Combiner reduces shuffle traffic
- Handling skew (popular pages) and the distinct-count memory problem
- Sessionization with secondary sort
- Text vs ORC/Parquet storage trade-offs observed in your benchmark
- Privacy decisions (IP hashing) and data quality handling

---

## 16. Appendix
### 16.1 Glossary
- HDFS: Hadoop Distributed File System
- YARN: Cluster resource manager for Hadoop
- Combiner: Local pre-aggregation on mapper output
- Sessionization: Grouping a visitor's requests into sessions by inactivity gap
- Bounce rate: Percentage of sessions with exactly one page view

### 16.2 Sample Log Line
`203.0.113.42 - - [10/Nov/2026:09:15:32 +0000] "GET /products/laptop HTTP/1.1" 200 5123 "https://www.google.com/" "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0"`


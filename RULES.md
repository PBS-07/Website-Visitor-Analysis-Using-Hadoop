# Repository Rules & Engineering Constraints

This repository follows strict architectural and data engineering rules per `docs/PRD.md`:

1. **Technology Versions**:
   - Hadoop: 3.3.x (HDFS, YARN, MapReduce).
   - Apache Hive: 3.1.x.
   - Java: 11 (Maven build for MapReduce jobs).
   - Python: 3.10+ (Data generator, export scripts, test fixtures).
   - Node: Next.js (App Router), TypeScript, Tailwind CSS, Recharts.

2. **Data Privacy (Zero-Leakage Policy)**:
   - Every visitor IP address MUST be hashed with SHA-256 before writing to `/weblogs/clean/` or any analytical output.
   - Raw IP addresses MUST NEVER be displayed on the web dashboard or stored in plain text outputs.

3. **Reproducibility & Idempotency**:
   - Single-command orchestration: `make up && make run-all` must execute the pipeline end to end from a fresh clone.
   - All shell scripts (`ingest.sh`, `run_pipeline.sh`, `export.sh`) and MapReduce jobs must be idempotent: rerunning them overwrites or refreshes outputs safely without failure or duplicate accumulation.
   - No hardcoded machine-specific absolute paths or credentials.

4. **Repository Structure**:
   - Strictly follow the repository layout specified in PRD Section 10:
     - `docker/`: Compose setup and Hadoop/Hive configurations.
     - `generator/`: Synthetic log generator and configuration YAML.
     - `mapreduce/`: Maven project with source code for MapReduce jobs.
     - `hive/`: DDL schemas and HiveQL analytics scripts.
     - `scripts/`: Shell automation scripts.
     - `exports/`: CSV and JSON analytical results for the dashboard.
     - `dashboard/`: Next.js web application.
     - `tests/`: Automated unit and integration test fixtures.
     - `docs/`: Architecture diagrams, PRD, and screenshots.
     - `Makefile` and `README.md`.

5. **Testing & Quality Assurance**:
   - Every module must ship with automated unit tests (minimum 80% coverage on core logic).
   - Python modules tested via `pytest`.
   - Java MapReduce modules tested via JUnit and MRUnit / MiniMRCluster fixtures.
   - Parity verification between MapReduce and Hive queries must pass 100%.


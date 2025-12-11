# JADE Distributed Agent-Based Network Monitoring And Response System

A multi-agent system built on **JADE** (Java Agent Development Environment) for distributed resource monitoring, anomaly detection, and autonomous system remediation across networked hosts.

## Project Overview

This system deploys intelligent monitoring agents on multiple machines to collect system metrics, detect anomalies through correlation analysis, and autonomously react by throttling or killing resource-hungry processes. A central coordinator aggregates metrics and makes intelligent decisions about when to deploy mobile audit agents for detailed investigation.

## Features

- **Distributed Monitoring**: Continuous collection of CPU, RAM, disk I/O, and network metrics from 5+ nodes every 5 seconds
- **Autonomous Local Reactions**: Agents throttle/kill processes when thresholds exceeded (CPU >95%, RAM <512MB, etc.)
- **Correlation-Based Analysis**: Central agent analyzes correlations between nodes to identify network-wide patterns
- **Mobile Audit Agents**: Deploy dynamic inspectors to collect deep system data (top processes, detailed metrics)
- **PostgreSQL Persistence**: All metrics, actions, and audit reports logged with timestamps
- **Cooldown & Rate Limiting**: Prevents action spam (10s per action, 30s per audit)
- **Grafana Visualization**: Real-time dashboards showing metrics, actions, and system state
- **Smart Clustering**: DFS-based clustering identifies correlated agents for coordinated auditing

### 1. **MonitoringAgent** (Local Analysis & Reaction)
Runs on each host (agent1-agent5). Responsibilities:
- **Data Collection** (every 5 seconds):
  - CPU load (via `/proc/stat`)
  - Free memory (via JMX)
  - Free disk space
  - Disk I/O utilization
  - Network traffic (bytes in/out)
  
- **Local Analysis**: Classifies severity (Low/Medium/High), compares against thresholds
- **Local Reaction** (iterative):
  - **CPU >95%**: Throttles top processes with `cpulimit` (target 40%)
  - **CPU Critical**: Kills top offenders if CPU doesn't decrease
  - **RAM <512MB**: Kills memory-heavy processes
  - **Disk I/O >80%**: Restarts or kills I/O services
  - **Network Congestion**: Applies traffic shaping via `tc`
- **Cooldown**: 10-second between actions per resource type
- **Blacklist**: Protects system/jade processes

### 2. **CentralAgent** (Correlation & Orchestration)
Runs on central host. Responsibilities:
- **Metrics Aggregation** (every 10 seconds): Pulls last 60 seconds of CPU data from all agents
- **Correlation Analysis**: 
  - Calculates Pearson correlation between all agent pairs (O(n²))
  - Identifies clusters of correlated agents (threshold: 0.75)
  - Uses DFS to find connected components
- **Intelligent Audit Deployment**:
  - **Single Agent**: Audits if avg CPU >80% OR peak >90%
  - **Cluster (2+ agents)**: Audits if ≥2 members have high CPU (avg>70% OR peak>85%)
  - **Cooldown**: 30-second per-agent cooldown between audits

### 3. **AuditAgent** (Mobile Deep Inspection)
Deployed dynamically by CentralAgent. Responsibilities:
- Collects detailed CPU metrics, top 3 processes, memory usage
- Sends JSON report back to CentralAgent
- Data stored in `audit_reports` table with timestamp

### 4. **PostgreSQL Database**
Persistent storage for all system events:
- **`metrics` table**: CPU, RAM, disk, I/O, network per agent + severity
- **`actions` table**: All local reactions (throttle/kill/restart) with reason
- **`audit_reports` table**: Deep audit snapshots from AuditAgent

## Quick Start

### Prerequisites
- Java 11+, PostgreSQL 13+, JADE 4.6.0+
- Linux (Debian), GNS3 VM, Cisco router simulator

### On Central Host (192.168.1.100):
```bash
# Download PostgreSQL driver
wget https://jdbc.postgresql.org/download/postgresql-42.7.0.jar

# Start database
sudo pg_ctlcluster 13 main start
sudo -u postgres psql -c "CREATE DATABASE network_monitor;"
sudo -u postgres psql -c "CREATE USER agent_user WITH PASSWORD '0000';"
sudo -u postgres psql -c "GRANT ALL ON DATABASE network_monitor TO agent_user;"

# Compile & run
javac -cp /opt/jade/jade/lib/jade.jar:postgresql-42.7.0.jar ./myagents/CentralAgent.java
java -cp .:/opt/jade/jade/lib/jade.jar:postgresql-42.7.0.jar jade.Boot \
  -agents "CentralAgent:myagents.CentralAgent"
```

### On Each Monitoring Host (agent1-agent5):
```bash
# Compile
javac -cp /opt/jade/jade/lib/jade.jar:/usr/share/java/postgresql.jar ./myagents/MonitoringAgent.java

# Run (use tmux for parallel windows)
java -cp .:/opt/jade/jade/lib/jade.jar:/usr/share/java/postgresql.jar jade.Boot \
  -container -host 192.168.1.100 \
  -agents "agent1:myagents.MonitoringAgent"
```

## System Operation with 5 Agents

```
Phase 1 - Data Collection (every 5s):
  Agent1-5 measure metrics → send CSV to CentralAgent → stored in DB

Phase 2 - Correlation Analysis (every 10s):
  Pull 60s of CPU data → filter agents with ≥5 samples → calc 10 Pearson correlations
  Build graph (corr > 0.75) → cluster via DFS

Phase 3 - Audit Decision:
  For each cluster:
    - Count high-CPU members (avg>70% OR peak>85%)
    - If ≥2 members OR single-agent: check cooldown → deploy AuditAgent
    - All 5 agents potentially audited in one cycle

Phase 4 - Deployment & Reaction:
  AuditAgent-xxx captures top processes → sends JSON report
  MonitoringAgent executes local actions (throttle/kill/restart)
  All stored in database with timestamps
``` 



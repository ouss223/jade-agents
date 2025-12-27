# JADE Distributed Agent-Based Network Monitoring System

A multi-agent system built on **JADE** for distributed resource monitoring, anomaly detection, and autonomous system remediation across networked hosts.

## System Components

### MonitoringAgent (Local Analysis & Reaction)
Runs on each host collecting metrics every 5 seconds:
- CPU load, memory, disk I/O, network traffic
- Local severity classification
- Autonomous reactions: throttle/kill processes
- 10-second action cooldown

### CentralAgent (Correlation & Orchestration)
Central coordinator:
- Aggregates metrics every 10 seconds from 60-second window
- Calculates Pearson correlations between agents
- DFS-based clustering for correlated agents
- Intelligent audit deployment with 30-second cooldown

### AuditAgent (Mobile Deep Inspection)
Deployed dynamically for detailed investigation:
- Collects process details and independent metrics
- Sends JSON reports to CentralAgent
- Stored in database with full traceability

### PostgreSQL Database
Persistent storage:
- **metrics**: CPU, RAM, disk, I/O, network per agent
- **actions**: Local reactions with reasons
- **audit_reports**: Audit snapshots

## Key Features

- Distributed monitoring without single point of failure
- Real-time correlation analysis
- Mobile agents for deep inspection
- Persistent event logging
- Grafana real-time dashboards
- Cloudflare tunnel for external access
- Autonomous reaction to anomalies
- Rate limiting and cooldown mechanisms

## Sequence Diagram
![Dashboard Chart](media/chart.png)

## Deployment


### Compile Agents
```bash
chmod +x ./scripts/compile_agents.sh
./scripts/compile_agents.sh
```

### Launch Agents
```bash
# Central
chmod +x ./scripts/run_central.sh
./scripts/run_central.sh

# Each monitor
chmod +x ./scripts/run_monitoring.sh
./scripts/run_monitoring.sh agent1 192.168.1.100
```

### Grafana Tunnel
```bash
chmod +x ./scripts/start_grafana_tunnel.sh
./scripts/start_grafana_tunnel.sh http://localhost:3000
```

## Documentation

- [Infrastructure Setup](demonstrations/02_setup/infrastructure_setup.md) - GNS3 configuration
- [Network Architecture](demonstrations/03_network/network_architecture.md) - Topology details
- [Agent Deployment](demonstrations/04_agents/agents_deployment.md) - Launching agents
- [Database & Grafana](demonstrations/05_db_grafana/database_grafana.md) - Persistence & visualization
- [Test Results](demonstrations/06_tests/testing_results.md) - Validation results
- [Problems & Solutions](demonstrations/01_problems/problems_and_solutions.md) - Issues encountered
- [Network Configuration](configs/agents-network/README.md) - IP setup
- [Router Configuration](configs/router/README.md) - Cisco IOSv setup
- [Grafana Configuration](grafana/README.md) - Dashboards
- [Stress Tests](stress_tests/README.md) - Load testing

## Technologies

- JADE 4.6.0+ (multi-agent framework)
- PostgreSQL 13+ (persistence)
- Grafana (visualization)
- Debian (VMs)
- GNS3 (network simulation)
- Java 11+ (implementation)

# Index and Overview

## Demonstration Contents

### 01. Problems and Solutions
Complete documentation of problems encountered during deployment and solutions applied:
- GNS3 interface instabilities
- Network performance limitations
- Disk resource management
- System error corrections

### 02. GNS3 Infrastructure
Detailed guide for setting up and configuring the infrastructure:
- Starting GNS3 VM on VMware
- Importing equipment models
- Creating the network topology
- Configuring Debian machines
- Connectivity testing

### 03. Network Architecture
Description of the GNS3 architecture set up:
- Global topology and components
- IP address configuration
- Performance observed
- Connectivity modes

### 04. JADE Agent Deployment
Complete procedure for compiling and launching agents:
- Compilation of Java classes
- Launching CentralAgent
- Deploying MonitoringAgents
- Connectivity verification
- Inter-agent communication

### 05. PostgreSQL and Grafana
Configuration of storage and visualization system:
- PostgreSQL database initialization
- Table schema (metrics, actions, audit_reports)
- Grafana installation and configuration
- Visualization dashboards
- External access via Cloudflare tunnel

### 06. Tests and Results
Load test results and validations:
- Network connectivity tests
- CPU, RAM, disk and network stress tests
- Distributed correlation and audit
- Anomaly reports

## Multimedia Resources Used

All references to resources come exclusively from the `/media/` directory containing:
- Screenshots of GNS3 infrastructure
- Grafana charts
- Test scenario videos
- Configuration interfaces

## Recommended Navigation

1. Start with **01_problems** to understand challenges encountered
2. Check **02_setup** for installation details
3. Study **03_network** for general architecture
4. Follow **04_agents** for software deployment
5. Examine **05_db_grafana** for persistence and visualization
6. Analyze **06_tests** for results and validations

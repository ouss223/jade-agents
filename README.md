# Distributed Network Monitoring System

## Overview
This project implements a distributed network monitoring system using JADE and gns3 . It collects CPU, RAM, disk, and network metrics from multiple nodes, performs correlation analysis, and dispatches mobile AuditAgents for deep audits when anomalies or high resource usage are detected.

## Features
- Continuous collection of system metrics from multiple nodes.
- Correlation-based detection of resource usage patterns.
- Mobile AuditAgents for in-depth local audits.
- PostgreSQL database storage for metrics, audit reports, and actions.
- Cooldown mechanism to prevent repeated audits on the same node.

## Components
- **CentralAgent**: Receives metrics and audit reports, performs correlation analysis, dispatches AuditAgents.
- **AuditAgent**: Mobile agent that performs local system audits, collects top processes, CPU, and memory usage.

## Database
- **metrics**: Stores node metrics (CPU, RAM, disk, network, and severities).
- **actions**: Stores actions taken on nodes (process killed, reason, etc.).
- **audit_reports**: Stores detailed audit reports from AuditAgents.

## Setup
1. Install PostgreSQL and create database `network_monitor` with user `agent_user`.
2. Add PostgreSQL JDBC driver to your project.
3. Compile the Java agents with JADE libraries.
4. Start JADE runtime and deploy CentralAgent.

## Usage
1. Deploy the `CentralAgent` on the main container.
2. Deploy other agents on nodes to send metrics.
3. CentralAgent will automatically perform correlation analysis and dispatch AuditAgents when needed.

## Requirements
- Java 8+
- JADE framework
- PostgreSQL
- gns3vm 



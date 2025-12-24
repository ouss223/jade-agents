# Deployment Scripts

## Overview

Automated scripts for compilation, and deployment of JADE agents.

## Scripts

- **compile_agents.sh** - Compile Java agents (monitoring|central|all)
- **run_central.sh** - Launch CentralAgent (central_ip)
- **run_monitoring.sh** - Launch MonitoringAgent (agent_id, central_ip)
- **start_grafana_tunnel.sh** - Start Cloudflare tunnel (grafana_url)

## Quick Usage

```bash

# Compile
./compile_agents.sh central
./compile_agents.sh monitoring

# Run
./run_central.sh &
./run_monitoring.sh agent1 192.168.1.100 &
./start_grafana_tunnel.sh
```

Each script includes parameter documentation. check the header comments for details.

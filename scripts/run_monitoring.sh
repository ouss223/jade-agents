#!/bin/bash

# Launch MonitoringAgent on a monitoring host
#
# Parameters:
#   $1: Agent identifier (optional, default: agent1)
#       Examples: agent1, agent2, agent3, agent4, agent5
#   $2: Central host IP address (optional, default: 192.168.1.100)
#
# Usage:
#   ./run_monitoring.sh agent1
#   ./run_monitoring.sh agent2 192.168.1.100
#   ./run_monitoring.sh

set -e

AGENT_ID="${1:-agent1}"
CENTRAL_HOST="${2:-192.168.1.100}"
JADE_LIB="/opt/jade/jade/lib/jade.jar"
POSTGRES_DRIVER="/usr/share/java/postgresql.jar"

java -cp ./:$JADE_LIB:$POSTGRES_DRIVER jade.Boot -container -host $CENTRAL_HOST -agents "$AGENT_ID:myagents.MonitoringAgent $AGENT_ID"

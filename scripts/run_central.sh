#!/bin/bash

# Launch CentralAgent on central host
#
# Parameters:
#   $1: Central host IP address (optional, default: 192.168.1.100)
#
# Usage:
#   ./run_central.sh
#   ./run_central.sh 192.168.1.100

set -e

CENTRAL_HOST="${1:-192.168.1.100}"
JADE_LIB="/opt/jade/jade/lib/jade.jar"
POSTGRES_DRIVER="/usr/share/java/postgresql.jar"

java -cp ./:$JADE_LIB:$POSTGRES_DRIVER jade.Boot -agents "CentralAgent:myagents.CentralAgent"

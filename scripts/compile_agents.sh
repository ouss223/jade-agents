#!/bin/bash

# JADE Agents Compilation Script
# Compiles all agent classes with required dependencies
#
# Parameters:
#   $1: Compilation target (optional)
#       - 'monitoring': Compile only MonitoringAgent
#       - 'central': Compile CentralAgent and AuditAgent
#
# Usage:
#   ./compile_agents.sh monitoring
#   ./compile_agents.sh central
#   ./compile_agents.sh

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
AGENTS_DIR="$REPO_ROOT/agents"
MYAGENTS_DIR="$AGENTS_DIR/myagents"

JADE_LIB="${JADE_HOME:-/opt/jade}/jade/lib/jade.jar"
POSTGRES_DRIVER="${POSTGRES_DRIVER:-/usr/share/java/postgresql.jar}"

if [ ! -f "$JADE_LIB" ]; then
  JADE_LIB="$REPO_ROOT/lib/jade.jar"
fi

if [ ! -f "$POSTGRES_DRIVER" ]; then
    POSTGRES_DRIVER="./lib/postgresql-42.7.0.jar"
fi

if [ ! -f "$JADE_LIB" ]; then
    echo "[ERROR] JADE library not found" >&2
    exit 1
fi

CLASSPATH="$JADE_LIB:$POSTGRES_DRIVER:$AGENTS_DIR"

case "$1" in
  monitoring)
    javac -cp "$CLASSPATH" -d "$AGENTS_DIR" "$AGENTS_DIR/MonitoringAgent.java" || exit 1
    echo "Compiled: MonitoringAgent"
    ;;
  central)
    javac -cp "$CLASSPATH" -d "$AGENTS_DIR" "$AGENTS_DIR/CentralAgent.java" || exit 1
    javac -cp "$CLASSPATH" -d "$AGENTS_DIR" "$AGENTS_DIR/AuditAgent.java" || exit 1
    echo "Compiled: CentralAgent, AuditAgent"
    ;;
  *)
    echo "Usage: $0 {monitoring|central}" >&2
    exit 1
    ;;
esac

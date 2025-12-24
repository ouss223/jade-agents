#!/bin/bash

# Start Cloudflare tunnel for external Grafana access
#
# Parameters:
#   $1: Grafana URL (optional, default: http://localhost:3000)
#
# Usage:
#   ./start_grafana_tunnel.sh
#   ./start_grafana_tunnel.sh http://localhost:3000
#   ./start_grafana_tunnel.sh http://192.168.1.100:3000

set -e

TUNNEL_URL="${1:-http://localhost:3000}"

cloudflared tunnel --url $TUNNEL_URL

#!/bin/bash
set -e

# K3S Installation Script for Cloud Instances
# This script is templated by Terraform

NODE_INDEX=${node_index}
CLUSTER_NAME=${cluster_name}
K3S_VERSION=${k3s_version}
IS_SERVER=${is_server}
SERVER_IP="${server_ip}"
K3S_TOKEN="${k3s_token}"
DISABLE_COMPONENTS="${disable_components}"
SERVER_FLAGS="${server_flags}"
AGENT_FLAGS="${agent_flags}"

# Update system
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y curl wget

# Generate K3S token if not provided (for server)
if [ "$IS_SERVER" = "true" ] && [ -z "$K3S_TOKEN" ]; then
  K3S_TOKEN=$(openssl rand -hex 32)
fi

# Install K3S
export INSTALL_K3S_VERSION="$K3S_VERSION"
export K3S_NODE_NAME="${CLUSTER_NAME}-node-${NODE_INDEX}"

if [ "$IS_SERVER" = "true" ]; then
  # Server installation
  export K3S_TOKEN
  INSTALL_K3S_EXEC="--token $K3S_TOKEN"

  if [ -n "$DISABLE_COMPONENTS" ]; then
    INSTALL_K3S_EXEC="$INSTALL_K3S_EXEC --disable $DISABLE_COMPONENTS"
  fi

  if [ -n "$SERVER_FLAGS" ]; then
    INSTALL_K3S_EXEC="$INSTALL_K3S_EXEC $SERVER_FLAGS"
  fi
  
  export INSTALL_K3S_EXEC

  curl -sfL https://get.k3s.io | sh -s - server --cluster-init

  # Wait for K3S to be ready
  timeout 300 bash -c 'until systemctl is-active --quiet k3s; do sleep 2; done'
  timeout 300 bash -c 'until kubectl get nodes 2>/dev/null; do sleep 2; done'
else
  # Agent installation
  if [ -z "$K3S_TOKEN" ]; then
    echo "Error: K3S_TOKEN is required for agent nodes"
    exit 1
  fi
  
  if [ -z "$SERVER_IP" ]; then
    echo "Error: SERVER_IP is required for agent nodes"
    exit 1
  fi
  
  export K3S_URL="https://${SERVER_IP}:6443"
  export K3S_TOKEN
  
  if [ -n "$AGENT_FLAGS" ]; then
    export INSTALL_K3S_EXEC="$AGENT_FLAGS"
  fi
  
  curl -sfL https://get.k3s.io | sh -s - agent
  
  # Wait for K3S agent to be ready
  timeout 300 bash -c 'until systemctl is-active --quiet k3s-agent; do sleep 2; done'
fi

echo "K3S installation completed for ${CLUSTER_NAME}-node-${NODE_INDEX}"

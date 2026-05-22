#!/bin/bash
# setup_coturn.sh - Installs and configures a coturn STUN/TURN server for Enclave

set -e

echo "Updating packages..."
sudo apt-get update && sudo apt-get install -y coturn

echo "Enabling coturn daemon..."
sudo sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/g' /etc/default/coturn

echo "Configuring /etc/turnserver.conf..."
sudo cp /etc/turnserver.conf /etc/turnserver.conf.backup

cat << 'EOF' | sudo tee /etc/turnserver.conf > /dev/null
# Enclave STUN/TURN Configuration
listening-port=3478
tls-listening-port=5349

# Min/Max ports for relaying
min-port=49152
max-port=65535

# Set verbose mode (useful for debugging, consider disabling in prod)
verbose

# Use long-term credentials
lt-cred-mech

# Replace with your own secure auth secret
use-auth-secret
static-auth-secret=enclave_turn_secret_12345

# Replace with your actual droplet public IP
# external-ip=YOUR_DROPLET_PUBLIC_IP

# Realm
realm=enclave.local

# SSL certificates (uncomment and provide paths when you have them via Let's Encrypt)
# cert=/etc/letsencrypt/live/turn.enclave.local/fullchain.pem
# pkey=/etc/letsencrypt/live/turn.enclave.local/privkey.pem

no-tcp-relay
no-multicast-peers
EOF

echo "Please edit /etc/turnserver.conf and replace YOUR_DROPLET_PUBLIC_IP with the actual IP address."
echo "Starting coturn service..."
sudo systemctl restart coturn
sudo systemctl enable coturn

echo "Coturn setup complete. Ensure ports 3478, 5349 (TCP/UDP) and 49152-65535 (UDP) are open in your firewall."

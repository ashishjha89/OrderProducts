#!/bin/bash
set -euxo pipefail

########################################
# 1. System update
########################################
dnf update -y

########################################
# 2. Install Docker
########################################
dnf install -y docker

systemctl enable docker
systemctl start docker

########################################
# 3. Add ec2-user to docker group
########################################
usermod -aG docker ec2-user

########################################
# 4. Install Docker Compose plugin (v2)
########################################
mkdir -p /usr/local/lib/docker/cli-plugins

curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose

chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Optional backward-compatibility symlink
ln -sf /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose

########################################
# 5. Install Git
########################################
dnf install -y git

########################################
# 6. Clone OrderProducts repository
########################################
cd /home/ec2-user

# Clone only if not already present (idempotent)
if [ ! -d "OrderProducts" ]; then
  git clone https://github.com/ashishjha89/OrderProducts.git
fi

chown -R ec2-user:ec2-user /home/ec2-user/OrderProducts

########################################
# 7. Run docker-compose as ec2-user
########################################

su - ec2-user <<'EOF'
set -euxo pipefail

# Apply docker group immediately
newgrp docker <<'INNER_EOF'

cd ~/OrderProducts/infrastructure

# Pull images
docker compose pull

# Start stack
docker compose up -d

INNER_EOF
EOF

########################################
# 8. Log completion
########################################
echo "User-data setup completed successfully" > /var/log/user-data-complete.log
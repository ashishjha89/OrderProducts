# Deployment Plan: AWS EC2 Single Instance

## Executive Summary

**Good News**: Your OrderProducts system requires **ZERO code changes** to deploy to AWS EC2!

The system is already perfectly architected for single-instance cloud deployment. You can deploy the exact same docker-compose setup that runs locally to AWS EC2 without any modifications to application code, Dockerfiles, or docker-compose.yml.

---

## Strategic Decisions & Rationale

### 1. Configuration Strategy: Environment Variables (Option A)

**Decision**: Use environment variables exclusively, avoiding Spring profiles or separate docker-compose files.

**Why This Option:**
- ✅ **Cloud-Native**: Aligns with 12-Factor App principles, making future migration to ECS Fargate trivial
- ✅ **Terraform-Friendly**: Environment variables map directly to Terraform resource definitions
- ✅ **Zero Config Drift**: Single source of truth - no risk of maintaining divergent config files
- ✅ **Container Orchestration Ready**: Works seamlessly with Docker Compose, ECS, Kubernetes
- ✅ **Local-AWS Parity**: Same codebase and Docker images run in both environments

**Current State**: ✅ **Already implemented in your codebase**
- All services use `${VAR:default}` pattern
- All configurations externalized via environment variables
- docker-compose.yml already provides all necessary env vars

---

### 2. Port Exposure: Security vs Convenience

**Your Request**: Expose 8080 (API Gateway), 8761 (Eureka), 9411 (Zipkin) publicly.

**My Honest Opinion**: 

#### ⚠️ Security Concerns with Exposing Monitoring Ports:
1. **Eureka exposes service topology** - attackers can see your internal architecture
2. **Zipkin contains request/response traces** - may leak sensitive data patterns
3. **Both have no authentication** - anyone with access can view everything

For a practice project running few hours/month, you have three options:

---

#### 🎯 Three Security Approaches:

**Option 1: Minimal Exposure + SSH Tunneling (Most Secure)** ⭐ **RECOMMENDED**

**Security Group Configuration:**
```
Inbound Rules:
┌──────┬──────┬─────────────┬─────────────────────┐
│ Type │ Port │   Source    │     Description     │
├──────┼──────┼─────────────┼─────────────────────┤
│ SSH  │  22  │ Your IP/32  │ SSH access          │
│ HTTP │ 8080 │ Your IP/32  │ API Gateway         │
└──────┴──────┴─────────────┴─────────────────────┘

Ports 8761 (Eureka) and 9411 (Zipkin): NOT exposed
```

**Access Monitoring Tools:**
```bash
# Open SSH tunnel from your local machine
ssh -i ~/.ssh/your-key.pem \
  -L 8761:localhost:8761 \
  -L 9411:localhost:9411 \
  ec2-user@<EC2_IP>

# Then access in browser via localhost
http://localhost:8761  # Eureka dashboard
http://localhost:9411  # Zipkin UI
```

**Pros:**
- ✅ Zero attack surface for monitoring tools
- ✅ No additional ports exposed to internet
- ✅ Even if someone finds your EC2 IP, they can't access Eureka/Zipkin
- ✅ Best practice for production-like environments

**Cons:**
- ⚠️ Requires SSH connection open while debugging
- ⚠️ Slightly less convenient (but worth the security)

---

**Option 2: Direct Access - IP Restricted (Convenient but Less Secure)**

**Security Group Configuration:**
```
Inbound Rules:
┌──────┬──────┬─────────────┬─────────────────────┐
│ Type │ Port │   Source    │     Description     │
├──────┼──────┼─────────────┼─────────────────────┤
│ SSH  │  22  │ Your IP/32  │ SSH access          │
│ HTTP │ 8080 │ Your IP/32  │ API Gateway         │
│ HTTP │ 8761 │ Your IP/32  │ Eureka Dashboard    │
│ HTTP │ 9411 │ Your IP/32  │ Zipkin UI           │
└──────┴──────┴─────────────┴─────────────────────┘
```

**Access:**
```bash
# Direct browser access
http://<EC2_IP>:8080  # API Gateway
http://<EC2_IP>:8761  # Eureka dashboard
http://<EC2_IP>:9411  # Zipkin UI
```

**Pros:**
- ✅ Direct browser access, no SSH tunnel needed
- ✅ Still restricted to your IP (reasonably secure)
- ✅ Convenient for frequent debugging

**Cons:**
- ⚠️ Larger attack surface (4 ports exposed vs 2)
- ⚠️ Must update security group when your IP changes
- ⚠️ If your IP is compromised, attacker can see internal architecture

---

#### 🎯 My Recommendation:

**Use Option 1 (SSH Tunneling)** for the best balance of security and practicality:

1. **Setup is simple**: One SSH command opens both tunnels
2. **Muscle memory**: Good habit for real production work
3. **Minimal risk**: Even if someone finds your EC2 IP, they can't access monitoring tools
4. **Learn proper practices**: Prepares you for real-world deployments

**If you prefer convenience**, use Option 2 with these precautions:
- Update security group immediately when your IP changes
- Never leave ports open when not actively working
- Monitor CloudWatch logs for unexpected access attempts

---

### 3. Data Persistence: Docker Named Volumes

**Your Choice**: Keep using Docker named volumes.

**My Honest Opinion**:

For single EC2 instance deployment, Docker named volumes are **appropriate** with important considerations:

#### ✅ Good for Your Use Case Because:
- Simple to manage - same approach for local and AWS
- Survives container restarts and `docker-compose restart`
- No external dependencies (no RDS/DocumentDB costs)
- Aligns with "practice project, few hours/month" goal

#### ⚠️ Important Limitations:

**Data Loss Scenarios:**
```bash
docker compose down -v  # ❌ Deletes all data
docker compose down     # ✅ Preserves data
docker system prune -a --volumes  # ❌ Deletes all data
```

**EC2 Instance Replacement:**
- If EC2 terminates/fails → ❌ **All data lost**
- Named volumes live on EC2's root volume (ephemeral)

#### 🎯 Recommendations:

**For Now (Practice Phase):**
- ✅ Use Docker named volumes (as chosen)
- Document backup/restore procedures
- Understand data is non-persistent across instance replacements
- Note: `docker compose restart` preserves data, but `docker compose down -v` deletes it

**For Later (When You Want Durability):**

1. **Quick Win - EBS Volumes**:
   ```yaml
   volumes:
     mysql_data:
       driver: local
       driver_opts:
         type: none
         o: bind
         device: /mnt/ebs/mysql_data
   ```
   - Attach EBS volume to EC2, mount at `/mnt/ebs`
   - Survives instance replacement (detach/reattach EBS)
   - Cost: ~$10/month for 100GB

2. **Production Approach - Managed Databases**:
   - MySQL → RDS (~$15-30/month for db.t3.micro)
   - MongoDB → DocumentDB or Atlas ($$$ - not recommended for practice)
   - Kafka → MSK ($$$)

**Decision for This Plan**: We'll use Docker named volumes as you specified, with clear documentation about data persistence characteristics.

---

### 4. Kafka Advertised Listeners

**Challenge**: Kafka needs to advertise reachable addresses. Currently configured:
```yaml
KAFKA_ADVERTISED_LISTENERS: 
  PLAINTEXT://broker:29092         # Internal (services)
  PLAINTEXT_HOST://localhost:9092  # External (local machine)
```

**For AWS EC2**:
- Internal services will connect via `broker:29092` (Docker network) ✅
- No external Kafka clients needed ✅
- **No changes required** to Kafka configuration

**Why No Changes:**
- All Kafka consumers/producers (inventory-service, order-service) run inside Docker network
- They use `broker:29092` via `KAFKA_BOOTSTRAP_SERVERS` environment variable
- `localhost:9092` listener is for local machine debugging (not used in AWS)

---

### 5. Eureka Service Registration: Hostname vs IP

**Observation**: 
- Single Docker host with one bridge network (`microservices-network`)
- Docker DNS provides stable hostname → IP resolution
- All services reference each other by service name
- Works identically on local machine and AWS EC2

#### Why `eureka.instance.prefer-ip-address=true` is NOT needed:

**Single Host Architecture (Your Setup):**
```
EC2 Instance (or Local Machine)
    └── microservices-network (Docker bridge)
        ├── discovery-server    (hostname: discovery-server)
        ├── product-service     (hostname: product-service)
        ├── inventory-service   (hostname: inventory-service)
        └── order-service       (hostname: order-service)
```

**How It Works:**
1. Eureka registers `product-service` with hostname `product-service`
2. API Gateway looks up `product-service` in Eureka → gets `product-service` hostname
3. API Gateway calls `http://product-service:8080/api/products`
4. Docker DNS resolves `product-service` → container IP (e.g., `172.18.0.5`)
5. Request succeeds ✅

**Why It Works on Both Local & AWS EC2:**
- Docker bridge network behavior is identical
- DNS resolution is identical
- Container naming is identical
- No hostname reachability issues because all containers share same network

#### When WOULD you need `prefer-ip-address=true`?

**❌ NOT needed: Single Docker host (your case)**
- All containers in same Docker network
- Docker DNS handles everything perfectly
- Hostname stability guaranteed for container lifetime

**✅ Needed: Multi-host deployments**

**Example 1: Docker Swarm (multiple EC2 instances)**
```
EC2-1: product-service (container ID: abc123, hostname: product-service.abc123)
EC2-2: inventory-service (needs to call product-service)
```
- Problem: `product-service.abc123` not resolvable from EC2-2 via Docker DNS
- Solution: Use IP addresses for cross-host communication

**Example 2: AWS ECS Fargate with awsvpc networking**
```
Each ECS task gets:
- Its own Elastic Network Interface (ENI)
- Unique private IP address
- Dynamic hostname that changes on restart
```
- Problem: Hostnames change on task restart, DNS propagation delays
- Solution: IP-based registration for faster discovery

**Example 3: Mixed deployment (Docker + non-Docker)**
```
EC2-1: Services in Docker containers
EC2-2: Services running directly on host (no Docker)
```
- Problem: Docker DNS only works inside Docker network
- Solution: IP-based registration for universal reachability

**Decision**: **NO changes needed for single EC2 deployment**

---

## Changes Required

### Summary: ZERO Code Changes Needed! 🎉

| Component | File(s) | Changes Required | Reason |
|-----------|---------|------------------|--------|
| **discovery-server** | `application.properties` | ✅ None | Already uses `${EUREKA_HOSTNAME:localhost}` |
| **discovery-server** | `Dockerfile` | ✅ None | Already parameterized |
| **api-gateway** | `application.properties` | ✅ None | Already uses env vars for all config |
| **api-gateway** | `Dockerfile` | ✅ None | Already parameterized |
| **product-service** | `application.properties` | ✅ None | Already uses env vars, hostname registration works on EC2 |
| **product-service** | `Dockerfile` | ✅ None | Already parameterized |
| **inventory-service** | `application.properties` | ✅ None | Already uses env vars, hostname registration works on EC2 |
| **inventory-service** | `Dockerfile` | ✅ None | Already parameterized |
| **order-service** | `application.properties` | ✅ None | Already uses env vars, hostname registration works on EC2 |
| **order-service** | `Dockerfile` | ✅ None | Already parameterized |
| **docker-compose.yml** | Infrastructure | ✅ None | Already perfect for single-host deployment |

**Congratulations!** Your architecture is already 100% cloud-ready for single-instance deployment!

---

## Why Your Setup Already Works

### 1. Environment Variable Pattern
**Current Implementation**: ✅
```properties
# product-service/src/main/resources/application.properties
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/product-service}
server.port=${SERVER_PORT:0}
eureka.client.serviceUrl.defaultZone=${EUREKA_SERVER:http://localhost:8761/eureka}
```

**docker-compose.yml provides all values**:
```yaml
environment:
  - MONGODB_URI=mongodb://mongodb:27017/product-service
  - EUREKA_SERVER=http://discovery-server:8761/eureka
```

### 2. Docker Networking
**Current Implementation**: ✅
```yaml
networks:
  microservices-network:
    driver: bridge
```
- All services on same network
- DNS resolution: `discovery-server` → `172.18.0.x`
- Works identically on local & AWS

### 3. Service Discovery
**Current Implementation**: ✅
- Services register with Eureka using their container hostname
- API Gateway discovers services via Eureka
- Docker DNS resolves hostnames to container IPs
- No cross-host communication → hostname-based registration works perfectly

---

## AWS EC2 Setup Instructions

### 1. EC2 Instance Setup

**Instance Configuration:**
```
Instance Type:    t3.large (2 vCPU, 8GB RAM)
AMI:              Amazon Linux 2023 (or Ubuntu 22.04)
Storage:          30GB gp3 root volume (minimum)
Security Group:   See section below
Key Pair:         Create or use existing
```

**Why t3.large?**
- Your local setup uses ~3.6GB memory
- t3.large has 8GB (leaves ~4GB buffer for OS + overhead)
- Sufficient for development/practice workload

---

### 2. Security Group Configuration

**Option 1: Direct Access (IP-Restricted)**
```
Inbound Rules:
┌──────────┬──────────┬─────────────┬─────────────────────┐
│   Type   │   Port   │   Source    │     Description     │
├──────────┼──────────┼─────────────┼─────────────────────┤
│   SSH    │    22    │  Your IP/32 │  SSH access         │
│   HTTP   │   8080   │  Your IP/32 │  API Gateway        │
│  Custom  │   8761   │  Your IP/32 │  Eureka Dashboard   │
│  Custom  │   9411   │  Your IP/32 │  Zipkin UI          │
└──────────┴──────────┴─────────────┴─────────────────────┘

Outbound Rules: All traffic (default)
```

**How to get your IP:**
```bash
curl ifconfig.me
# Example output: 203.0.113.42
# Use: 203.0.113.42/32 in security group
```

**Option 2: SSH Tunneling (More Secure)**
```
Inbound Rules:
┌──────────┬──────────┬─────────────┬─────────────────────┐
│   Type   │   Port   │   Source    │     Description     │
├──────────┼──────────┼─────────────┼─────────────────────┤
│   SSH    │    22    │  Your IP/32 │  SSH access         │
│   HTTP   │   8080   │  Your IP/32 │  API Gateway        │
└──────────┴──────────┴─────────────┴─────────────────────┘

Then on local machine:
ssh -i key.pem -L 8761:localhost:8761 -L 9411:localhost:9411 ec2-user@<EC2_IP>

# Access monitoring tools via localhost
http://localhost:8761  # Eureka
http://localhost:9411  # Zipkin
```

---

### 3. EC2 User Data Script (Automated Setup)

**For Amazon Linux 2023 - Use this script in EC2 User Data during instance launch:**

```bash
#!/bin/bash
set -e

# Log output to file for debugging
exec > >(tee /var/log/user-data.log)
exec 2>&1

echo "Starting OrderProducts setup on Amazon Linux 2023..."

# Update system (dnf is the package manager for AL2023)
dnf update -y

# Install Docker
dnf install -y docker
systemctl start docker
systemctl enable docker

# Add ec2-user to docker group
usermod -aG docker ec2-user

# Install Docker Compose V2 as Docker CLI plugin (modern method for AL2023)
DOCKER_COMPOSE_VERSION="v2.24.5"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Create symlink for backward compatibility (optional)
ln -sf /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose

# Install Git
dnf install -y git

# Clone repository (replace with your GitHub username)
cd /home/ec2-user
git clone https://github.com/YOUR_USERNAME/OrderProducts.git
chown -R ec2-user:ec2-user OrderProducts

# Pull Docker images (as ec2-user)
# Note: Using 'docker compose' (space) not 'docker-compose' (hyphen) for V2
su - ec2-user -c "cd /home/ec2-user/OrderProducts/infrastructure && docker compose pull"

# Start services (as ec2-user)
su - ec2-user -c "cd /home/ec2-user/OrderProducts/infrastructure && docker compose up -d"

# Setup log rotation for Docker containers
cat > /etc/logrotate.d/docker-containers <<'EOF'
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    missingok
    delaycompress
    copytruncate
}
EOF

# Wait for services to be healthy
echo "Waiting for services to start (this may take 3-5 minutes)..."
sleep 180

# Check service status
su - ec2-user -c "cd /home/ec2-user/OrderProducts/infrastructure && docker compose ps"

echo "OrderProducts setup complete!"
echo "Access API Gateway at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080"
```

**Important Notes:**
- Replace `YOUR_USERNAME` with your actual GitHub username
- This script uses `dnf` (Amazon Linux 2023's package manager)
- Docker Compose V2 is installed as a plugin (uses `docker compose` with space, not `docker-compose` with hyphen)
- Script logs are saved to `/var/log/user-data.log` for debugging

---

### 4. Manual Setup (Alternative to User Data)

If you prefer manual setup on **Amazon Linux 2023**:

**Step 1: Connect to EC2**
```bash
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

**Step 2: Install Docker**
```bash
# Update system
sudo dnf update -y

# Install Docker
sudo dnf install -y docker

# Start and enable Docker
sudo systemctl start docker
sudo systemctl enable docker

# Add ec2-user to docker group
sudo usermod -aG docker ec2-user

# Verify Docker installation
docker --version
```

**Step 3: Install Docker Compose V2 (as plugin)**
```bash
# Create plugin directory
sudo mkdir -p /usr/local/lib/docker/cli-plugins

# Download Docker Compose V2
DOCKER_COMPOSE_VERSION="v2.24.5"
sudo curl -SL "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose

# Make executable
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Create symlink (optional, for backward compatibility)
sudo ln -sf /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose

# Verify installation
docker compose version
```

**Step 4: Install Git and Clone Repository**
```bash
# Install Git (if not already installed)
sudo dnf install -y git

# Clone repository (replace YOUR_USERNAME)
cd ~
git clone https://github.com/YOUR_USERNAME/OrderProducts.git
cd OrderProducts/infrastructure
```

**Step 5: Start Services**
```bash
# IMPORTANT: Exit and reconnect for docker group to take effect
exit

# Reconnect to EC2
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>

# Navigate to infrastructure directory
cd ~/OrderProducts/infrastructure

# Pull Docker images
docker compose pull

# Start all services (note: 'docker compose' with space, not 'docker-compose' with hyphen)
docker compose up -d

# Wait 3-5 minutes for startup
sleep 180

# Check status (all should show "Up (healthy)")
docker compose ps

# View logs if needed
docker compose logs -f --tail=50
```

**Step 6: Verify Deployment**
```bash
# From EC2, test services locally
curl http://localhost:8761/eureka/apps  # Eureka
curl http://localhost:8080/actuator/health  # API Gateway

# From your local machine, test external access
curl http://<EC2_PUBLIC_IP>:8080/actuator/health
```

---

### 5. Terraform Setup (Future)

**Directory structure:**
```
terraform/
├── main.tf
├── variables.tf
├── outputs.tf
└── user-data.sh
```

**Example `main.tf`:**
```hcl
provider "aws" {
  region = var.aws_region
}

resource "aws_security_group" "orderproducts" {
  name        = "orderproducts-sg"
  description = "Security group for OrderProducts application"

  # SSH
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.your_ip_cidr]
  }

  # API Gateway
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.your_ip_cidr]
  }

  # Eureka (optional - for debugging)
  ingress {
    from_port   = 8761
    to_port     = 8761
    protocol    = "tcp"
    cidr_blocks = [var.your_ip_cidr]
  }

  # Zipkin (optional - for debugging)
  ingress {
    from_port   = 9411
    to_port     = 9411
    protocol    = "tcp"
    cidr_blocks = [var.your_ip_cidr]
  }

  # Outbound
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "orderproducts" {
  ami           = var.ami_id
  instance_type = "t3.large"
  key_name      = var.key_pair_name

  vpc_security_group_ids = [aws_security_group.orderproducts.id]

  user_data = templatefile("${path.module}/user-data.sh", {
    github_username = var.github_username
  })

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
  }

  tags = {
    Name = "orderproducts-instance"
  }
}

output "instance_ip" {
  value = aws_instance.orderproducts.public_ip
}

output "api_gateway_url" {
  value = "http://${aws_instance.orderproducts.public_ip}:8080"
}

output "eureka_dashboard_url" {
  value = "http://${aws_instance.orderproducts.public_ip}:8761"
}

output "zipkin_url" {
  value = "http://${aws_instance.orderproducts.public_ip}:9411"
}
```

**Usage:**
```bash
terraform init
terraform plan
terraform apply

# Outputs:
# instance_ip = "54.XXX.XXX.XXX"
# api_gateway_url = "http://54.XXX.XXX.XXX:8080"
```

---

## Testing Strategy

### Local Testing (Verification Before AWS)

**1. Verify current setup works:**
```bash
cd infrastructure
docker compose down -v
docker compose up -d

# Wait for startup
sleep 120

# Check all services are healthy
docker compose ps

# Test API Gateway
curl http://localhost:8080/api/products

# Check Eureka dashboard (should show all services with hostnames)
curl http://localhost:8761/eureka/apps
```

Expected Eureka registration:
```xml
<instance>
  <hostName>product-service</hostName>  <!-- hostname, not IP -->
  <ipAddr>172.18.0.5</ipAddr>
  <app>PRODUCT-SERVICE</app>
  <status>UP</status>
</instance>
```

**2. Verify Docker images are pushed to DockerHub:**
```bash
docker pull ashishjha/orderproducts-discovery-server:latest
docker pull ashishjha/orderproducts-api-gateway:latest
docker pull ashishjha/orderproducts-product-service:latest
docker pull ashishjha/orderproducts-inventory-service:latest
docker pull ashishjha/orderproducts-order-service:latest
```

---

### AWS Testing (After Deployment)

**1. SSH to EC2:**
```bash
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

**2. Check Docker containers:**
```bash
cd /home/ec2-user/OrderProducts/infrastructure

# Check status
docker compose ps

# Expected output: All services should show "Up (healthy)"
```

**3. Check service health from EC2:**
```bash
# Eureka
curl http://localhost:8761/eureka/apps

# API Gateway health
curl http://localhost:8080/actuator/health

# Product service via API Gateway
curl http://localhost:8080/api/products
```

**4. Test from your local machine:**
```bash
# Replace <EC2_PUBLIC_IP> with actual IP

# API Gateway
curl http://<EC2_PUBLIC_IP>:8080/actuator/health

# Get products
curl http://<EC2_PUBLIC_IP>:8080/api/products

# Eureka dashboard (if port 8761 is open)
open http://<EC2_PUBLIC_IP>:8761

# Zipkin (if port 9411 is open)
open http://<EC2_PUBLIC_IP>:9411
```

**5. Test full workflow:**
```bash
EC2_IP="<YOUR_EC2_PUBLIC_IP>"

# 1. Create a product
curl -X POST http://${EC2_IP}:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Product",
    "description": "Test from AWS",
    "price": 99.99
  }'

# 2. Get all products
curl http://${EC2_IP}:8080/api/products

# 3. Check Zipkin for traces
open http://${EC2_IP}:9411
```

**6. Monitor logs:**
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f api-gateway

# Last 100 lines
docker compose logs --tail=100 product-service
```

---

## Rollback Plan

If deployment fails or issues arise:

### 1. Quick Rollback (Restart Services)
```bash
# SSH to EC2
ssh -i key.pem ec2-user@<EC2_IP>

cd /home/ec2-user/OrderProducts/infrastructure
docker compose restart
```

### 2. Full Rollback (Clean Restart)
```bash
# Stop services
docker compose down

# Pull latest images again
docker compose pull

# Start with fresh state (keeps data)
docker compose up -d
```

### 3. Nuclear Option (Fresh Start)
```bash
# WARNING: This deletes all data
docker compose down -v

# Start fresh
docker compose up -d
```

### 4. Emergency Rollback (Terminate EC2)
- Stop EC2 instance via AWS Console
- Data in Docker volumes will be lost
- Can restart instance later if needed
- Or terminate and create new instance

---

## Cost Estimation

### Monthly Costs (Running 24/7)

| Service | Cost/Month | Notes |
|---------|------------|-------|
| **EC2 t3.large** | ~$60 | On-Demand pricing (us-east-1) |
| **EBS Storage (30GB)** | ~$3 | gp3 volume |
| **Data Transfer** | ~$1 | Minimal for practice |
| **Total (24/7)** | **~$64/month** | |

### Your Usage (Few Hours/Month)

If you run 40 hours/month (5 hours × 8 days):
- **EC2**: ~$3.50 (40 hours × $0.0832/hour)
- **EBS**: ~$3 (always attached, even when stopped)
- **Data Transfer**: ~$0.50
- **Total**: **~$7/month**

### Cost Optimization Tips

**1. Stop Instance When Not In Use**
```bash
# Stop instance (preserves data, only pay for EBS)
aws ec2 stop-instances --instance-ids i-xxxxx

# Start when needed
aws ec2 start-instances --instance-ids i-xxxxx
```
- **Stopped cost**: ~$3/month (EBS only)
- **Running cost**: ~$2/hour (EC2 + EBS)

**2. Use EC2 Instance Scheduler**
```bash
# AWS Instance Scheduler (CloudFormation template)
# Automatically start/stop on schedule
# Example: 9 AM - 6 PM on weekdays
# Saves ~70% of EC2 costs
```

**3. Use EC2 Spot Instances**
- 70-90% cheaper than On-Demand
- Risk: Can be terminated with 2-minute notice
- **Not recommended** for learning (interruptions disrupt workflow)

**4. Right-Size After Testing**
```bash
# If memory usage < 4GB consistently:
# Consider downgrading to t3.medium (4GB RAM)
# Cost: ~$30/month (vs $60 for t3.large)

# Monitor usage:
docker stats --no-stream
```

---

## Future Migration Path

### Phase 1: Current Plan ✅ (Single EC2)
- ✅ Simple deployment
- ✅ Low cost for practice (~$7/month)
- ✅ Full control
- ✅ ZERO code changes needed
- ⚠️ No high availability
- ⚠️ No auto-scaling

### Phase 2: ECS Fargate (Auto-scaling)

**Why Easy After This Setup:**
1. Already using environment variables ✅
2. Already using DockerHub images ✅
3. Already using container orchestration ✅

**When you need IP-based registration:**
- ECS tasks run on different hosts (multi-host deployment)
- Each task gets unique ENI with private IP
- Hostname-based registration becomes unreliable
- Solution: Add `eureka.instance.prefer-ip-address=true`

**Changes Needed for Fargate:**
```properties
# Add to application.properties when migrating to ECS
eureka.instance.prefer-ip-address=${EUREKA_PREFER_IP:false}
```

```yaml
# ECS Task Definition
environment:
  - name: EUREKA_PREFER_IP
    value: "true"  # ← Enable for multi-host
```

**Other Fargate Changes:**
- Replace MySQL/MongoDB with RDS/DocumentDB (optional but recommended)
- Replace Kafka with MSK or keep self-hosted
- Add ALB (Application Load Balancer) in front of API Gateway
- Use ECS Service Discovery or keep Eureka

### Phase 3: Kubernetes (EKS)

Similar migration path - same environment variable approach translates to ConfigMaps.

---

## Maintenance & Operations

### Updating Services

**1. Update code locally:**
```bash
cd product-service

# Make changes to code
# ...

# Build and test
mvn clean package
mvn test

# Build Docker image
docker build -t ashishjha/orderproducts-product-service:latest .

# Push to DockerHub
docker push ashishjha/orderproducts-product-service:latest
```

**2. Deploy to AWS:**
```bash
# SSH to EC2
ssh -i key.pem ec2-user@<EC2_IP>

cd /home/ec2-user/OrderProducts/infrastructure

# Pull new image
docker compose pull product-service

# Restart service (zero-downtime for other services)
docker compose up -d product-service

# Verify
docker compose ps
docker compose logs -f product-service
```

---

### Backup & Restore

**Backup (Manual):**
```bash
# SSH to EC2
ssh -i key.pem ec2-user@<EC2_IP>

# Create backup directory
mkdir -p /home/ec2-user/backups

# Backup MySQL data
sudo docker run --rm \
  -v mysql_data:/data \
  -v /home/ec2-user/backups:/backup \
  ubuntu tar czf /backup/mysql_data_$(date +%Y%m%d_%H%M%S).tar.gz -C /data .

# Backup MongoDB data
sudo docker run --rm \
  -v mongodb_data:/data \
  -v /home/ec2-user/backups:/backup \
  ubuntu tar czf /backup/mongodb_data_$(date +%Y%m%d_%H%M%S).tar.gz -C /data .

# Backup Kafka data
sudo docker run --rm \
  -v kafka_data:/data \
  -v /home/ec2-user/backups:/backup \
  ubuntu tar czf /backup/kafka_data_$(date +%Y%m%d_%H%M%S).tar.gz -C /data .

# Download backups to local machine (from local terminal)
scp -i key.pem ec2-user@<EC2_IP>:/home/ec2-user/backups/*.tar.gz ./
```

**Restore:**
```bash
# SSH to EC2
ssh -i key.pem ec2-user@<EC2_IP>

# Stop services
cd /home/ec2-user/OrderProducts/infrastructure
docker compose down

# Restore MySQL data
sudo docker run --rm \
  -v mysql_data:/data \
  -v /home/ec2-user/backups:/backup \
  ubuntu tar xzf /backup/mysql_data_20231228_120000.tar.gz -C /data

# Restore MongoDB data
sudo docker run --rm \
  -v mongodb_data:/data \
  -v /home/ec2-user/backups:/backup \
  ubuntu tar xzf /backup/mongodb_data_20231228_120000.tar.gz -C /data

# Start services
docker compose up -d
```

**Automated Backup (Cron):**
```bash
# Create backup script
cat > /home/ec2-user/backup_volumes.sh <<'EOF'
#!/bin/bash
BACKUP_DIR="/home/ec2-user/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p ${BACKUP_DIR}

# Backup MySQL
sudo docker run --rm \
  -v mysql_data:/data \
  -v ${BACKUP_DIR}:/backup \
  ubuntu tar czf /backup/mysql_${DATE}.tar.gz -C /data .

# Backup MongoDB
sudo docker run --rm \
  -v mongodb_data:/data \
  -v ${BACKUP_DIR}:/backup \
  ubuntu tar czf /backup/mongodb_${DATE}.tar.gz -C /data .

# Keep only last 7 days
find ${BACKUP_DIR} -name "*.tar.gz" -mtime +7 -delete

echo "Backup completed: ${DATE}"
EOF

chmod +x /home/ec2-user/backup_volumes.sh

# Add to crontab (daily at 2 AM)
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ec2-user/backup_volumes.sh >> /home/ec2-user/backup.log 2>&1") | crontab -
```

---

### Monitoring

**Basic Monitoring (Free):**
```bash
# Check service status
docker compose ps

# Check resource usage
docker stats --no-stream

# Check container health
docker inspect --format='{{.State.Health.Status}}' api-gateway

# Check logs
docker compose logs --tail=100 -f
```

**CloudWatch Monitoring (Optional):**

Install CloudWatch agent:
```bash
# Download CloudWatch agent
wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm

# Install
sudo rpm -U ./amazon-cloudwatch-agent.rpm

# Configure (requires IAM role with CloudWatch permissions)
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-config-wizard
```

**Metrics to monitor:**
- CPU utilization (alert if > 80%)
- Memory utilization (alert if > 85%)
- Disk utilization (alert if > 80%)
- Network in/out

---

## Key Differences: Local vs AWS

| Aspect | Local | AWS EC2 |
|--------|-------|---------|
| **Access URL** | `http://localhost:8080` | `http://<EC2_PUBLIC_IP>:8080` |
| **Eureka Registration** | Hostname-based | Hostname-based (same!) |
| **Docker Network** | `microservices-network` bridge | `microservices-network` bridge (same!) |
| **DNS Resolution** | Docker DNS | Docker DNS (same!) |
| **Port Exposure** | All ports to localhost | Ports to 0.0.0.0 (security group filtered) |
| **Data Persistence** | Docker volumes on local disk | Docker volumes on EC2 EBS |
| **Kafka Listeners** | Internal: `broker:29092`, External: `localhost:9092` | Internal: `broker:29092` (same!) |
| **Security** | Localhost only | Security Group + IP restrictions |
| **Cost** | Free (local resources) | ~$7/month (40 hours) |
| **Code Changes** | N/A | **ZERO** ✅ |

---

## Troubleshooting Guide

### Issue: Services not registering with Eureka

**Symptoms**: Eureka dashboard empty or shows "No instances available"

**Solutions:**
```bash
# 1. Check if discovery-server is healthy
docker logs discovery-server | tail -50

# 2. Check if service can reach Eureka
docker exec product-service curl http://discovery-server:8761/eureka/apps

# 3. Check service logs
docker logs product-service | grep -i eureka

# 4. Verify environment variables
docker exec product-service env | grep EUREKA

# 5. Check network connectivity
docker exec product-service ping -c 3 discovery-server
```

**Common causes:**
- Discovery server not fully started (wait 60-90 seconds)
- Services started before discovery server (restart services)
- Network configuration issue (restart docker-compose)

---

### Issue: API Gateway returns 503 Service Unavailable

**Symptoms**: `curl http://<EC2_IP>:8080/api/products` returns 503

**Solutions:**
```bash
# 1. Check if backend services are registered in Eureka
curl http://<EC2_IP>:8761/eureka/apps | grep product-service

# 2. Check API Gateway logs
docker logs api-gateway | tail -100

# 3. Check if API Gateway can resolve service names
docker exec api-gateway ping -c 3 product-service

# 4. Check LoadBalancer configuration
docker exec api-gateway curl http://product-service:8080/actuator/health
```

**Common causes:**
- Backend service not registered yet (wait for Eureka registration)
- Backend service unhealthy (check service logs)
- Route misconfiguration (verify application.properties)

---

### Issue: Kafka connection failures

**Symptoms**: inventory-service or order-service logs show Kafka errors

**Solutions:**
```bash
# 1. Check if broker is running and healthy
docker logs broker | tail -50
docker exec broker kafka-topics --bootstrap-server broker:29092 --list

# 2. Check if services can reach broker
docker exec inventory-service nc -zv broker 29092

# 3. Check Kafka environment variables
docker exec inventory-service env | grep KAFKA

# 4. Check Zookeeper (Kafka dependency)
docker logs zookeeper | tail -50
```

**Common causes:**
- Broker not fully started (wait 60 seconds after zookeeper)
- Zookeeper unhealthy (check zookeeper logs)
- Memory issues (check `docker stats`)

---

### Issue: High memory usage / OOM kills

**Symptoms**: Docker containers restarting, `docker stats` shows high memory usage

**Solutions:**
```bash
# 1. Check which service is using memory
docker stats --no-stream

# 2. Check for OOM kills
dmesg | grep -i "out of memory"
docker inspect <container_id> | grep OOM

# 3. Check swap usage (if enabled)
free -h
```

**Fixes:**

**Option 1: Reduce JVM heap (Dockerfile)**
```dockerfile
# Change from:
ENTRYPOINT ["java","-XX:InitialRAMPercentage=25","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

# To:
ENTRYPOINT ["java","-XX:InitialRAMPercentage=20","-XX:MaxRAMPercentage=60","-jar","/app/app.jar"]
```

**Option 2: Reduce container limits (docker-compose.yml)**
```yaml
# Change from:
mem_limit: 512m

# To:
mem_limit: 384m  # For less critical services
```

**Option 3: Upgrade instance**
```bash
# Upgrade from t3.large (8GB) to t3.xlarge (16GB)
# Stop instance, change instance type, start instance
```

---

### Issue: Cannot access from local machine

**Symptoms**: Connection timeout when accessing `http://<EC2_IP>:8080`

**Solutions:**

**1. Check Security Group:**
```bash
# Get your current IP
curl ifconfig.me

# Verify security group allows your IP
aws ec2 describe-security-groups \
  --group-ids sg-xxxxx \
  --query 'SecurityGroups[0].IpPermissions'
```

**2. Check from EC2 (should work):**
```bash
# SSH to EC2
ssh -i key.pem ec2-user@<EC2_IP>

# Test locally
curl http://localhost:8080/api/products  # Should work
```

**3. Check Docker port binding:**
```bash
docker compose ps
# Should show: 0.0.0.0:8080->8080/tcp
# NOT: 127.0.0.1:8080->8080/tcp
```

**4. Check EC2 firewall (if Amazon Linux):**
```bash
sudo iptables -L -n | grep 8080
# Should not have DROP rules for 8080
```

---

### Issue: Services start but then crash

**Symptoms**: `docker compose ps` shows services as "Restarting" or "Exit 1"

**Solutions:**
```bash
# 1. Check logs for crash reason
docker logs <service_name>

# 2. Check for dependency issues
docker compose logs | grep -i "error\|exception\|failed"

# 3. Check resource limits
docker stats

# 4. Verify all dependencies are healthy
docker compose ps | grep "healthy"
```

**Common causes:**
- Database not ready (healthcheck failed)
- Environment variable missing
- Out of memory
- Application configuration error

---

## Implementation Checklist

### Phase 1: Pre-Deployment Verification ✅

- [ ] Verify local setup works: `docker compose up -d && docker compose ps`
- [ ] Verify all services healthy locally
- [ ] Test API Gateway: `curl http://localhost:8080/api/products`
- [ ] Test Eureka dashboard: http://localhost:8761
- [ ] Verify Docker images exist in DockerHub
- [ ] Document current local setup (for rollback reference)

### Phase 2: AWS Preparation

- [ ] Create AWS account (if not exists)
- [ ] Create or identify EC2 key pair
- [ ] Determine your public IP: `curl ifconfig.me`
- [ ] Create security group with appropriate rules
- [ ] Review and customize user data script (update GitHub username)
- [ ] Decide: Use user data script or manual setup?
- [ ] Decide: Expose Eureka/Zipkin ports or use SSH tunneling?

### Phase 3: AWS Deployment

- [ ] Launch EC2 t3.large instance
  - [ ] Select AMI: Amazon Linux 2023
  - [ ] Choose t3.large instance type
  - [ ] Configure 30GB gp3 storage
  - [ ] Attach security group
  - [ ] Add key pair
  - [ ] Paste user data script (if using)
- [ ] Wait for instance to be "running" (2-3 minutes)
- [ ] Note down public IP address
- [ ] Wait for user data script to complete (5-10 minutes if used)

### Phase 4: Verification

- [ ] SSH to EC2: `ssh -i key.pem ec2-user@<EC2_IP>`
- [ ] Check user data script logs: `sudo cat /var/log/user-data.log`
- [ ] Check Docker is installed: `docker --version`
- [ ] Check Docker Compose is installed: `docker compose version`
- [ ] Navigate to project: `cd /home/ec2-user/OrderProducts/infrastructure`
- [ ] Check container status: `docker compose ps`
- [ ] Wait for all services to be "healthy" (may take 3-5 minutes)

### Phase 5: Testing

- [ ] **From EC2** - Test Eureka: `curl http://localhost:8761/eureka/apps`
- [ ] **From EC2** - Test API Gateway: `curl http://localhost:8080/api/products`
- [ ] **From local** - Test API Gateway: `curl http://<EC2_IP>:8080/api/products`
- [ ] **From local** - Open Eureka dashboard: `http://<EC2_IP>:8761` (if port open)
- [ ] **From local** - Open Zipkin: `http://<EC2_IP>:9411` (if port open)
- [ ] Test full workflow: Create product → Create order
- [ ] Monitor resource usage: `docker stats --no-stream`
- [ ] Check all services healthy: `docker compose ps`
- [ ] Review logs for errors: `docker compose logs | grep -i error`

---

### Phase 6: Documentation

- [ ] Document EC2 instance details:
  - [ ] Instance ID
  - [ ] Public IP
  - [ ] Security Group ID
  - [ ] Key pair name
- [ ] Document access URLs:
  - [ ] API Gateway
  - [ ] Eureka dashboard
  - [ ] Zipkin UI
- [ ] Document any issues encountered and solutions
- [ ] Update project README with AWS deployment section
- [ ] Save backup/restore procedures
- [ ] Document monitoring setup

## Success Criteria

Your deployment is successful when:

1. ✅ All services start without errors on AWS EC2
2. ✅ Eureka dashboard shows all services registered
3. ✅ API Gateway accessible from your local machine: `http://<EC2_IP>:8080`
4. ✅ Can create products: `POST http://<EC2_IP>:8080/api/products`
5. ✅ Can create orders: `POST http://<EC2_IP>:8080/api/order`
6. ✅ Distributed tracing visible in Zipkin: `http://<EC2_IP>:9411` (or via SSH tunnel)
7. ✅ All services show "Up (healthy)": `docker compose ps`
8. ✅ Resource usage under 6GB memory: `docker stats`
9. ✅ Services survive restart: `docker compose restart && docker compose ps`
10. ✅ Data persists after restart (test by creating product, restarting, checking product exists)

## Conclusion

**🎉 Your application is already 100% cloud-ready for AWS (single) EC2 deployment!**

### What Makes Your Architecture Excellent:

1. ✅ **Environment Variable Pattern**: All configs externalized
2. ✅ **Single Docker Network**: Stable DNS resolution
3. ✅ **Hostname-Based Registration**: Works perfectly for single-host deployment
4. ✅ **Parameterized Dockerfiles**: JVM tuning and health checks built-in
5. ✅ **Health Check Integration**: Docker Compose health checks ensure proper startup order

### Deployment Summary:

**Code Changes**: **ZERO** ❌  
**Dockerfile Changes**: **ZERO** ❌  
**docker-compose.yml Changes**: **ZERO** ❌  
**Monthly Cost (40 hours usage)**: **~$7**

### Next Steps:

1. Launch EC2 instance with user data script
2. Wait 10 minutes for setup to complete
3. Access `http://<EC2_IP>:8080/api/products`
4. Done! ✅

**When you're ready for auto-scaling (Phase 2: ECS Fargate)**, you'll need to:
- Add `eureka.instance.prefer-ip-address=${EUREKA_PREFER_IP:false}` to services
- Set `EUREKA_PREFER_IP=true` for multi-host deployments
- But for now, your single EC2 setup works perfectly as-is!


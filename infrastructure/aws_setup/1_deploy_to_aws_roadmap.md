# AWS Deployment Plan for OrderProducts

## Overview
Phased migration plan from local Docker setup to AWS with cost optimization (target: <$10/month base cost, max $20/month).

## Architecture Decisions

### Cost Optimization Strategy
- **Compute**: Pay-per-use with ECS Fargate (final state), t3.micro EC2 for infra
- **Storage**: Minimal EBS, no data persistence initially (ephemeral)
- **Databases**: Self-managed on EC2 with manual start/stop scripts
- **Messaging**: Replace Kafka with AWS SQS/SNS (pay-per-message, ~$0 for low volume)
- **Secrets**: AWS Systems Manager Parameter Store (free tier)
- **Observability**: CloudWatch + X-Ray (free tier limits)
- **No NAT Gateway**: Use public subnets with security groups ($45/month savings)

### Key Trade-offs
- Manual start/stop of resources vs always-on convenience
- Self-managed databases vs managed services (cost savings)
- Code changes required for SQS/SNS migration from Kafka
- Limited free tier observability vs full-featured monitoring

---

## Phase 1: Single EC2 Replacement

### Goal
Replace local machine with single EC2 instance running all services via Docker Compose.

### Steps

#### 1.1 Launch EC2 Instance
- **Instance Type**: t3.large (8GB RAM, 2 vCPU)
- **AMI**: Amazon Linux 2023
- **Storage**: 20GB gp3 root volume
- **Security Group**:
  - Inbound:
    - SSH (22) from your IP
    - HTTP (8080) from your IP for API Gateway
    - Custom TCP (8761) from your IP for Eureka dashboard
    - Custom TCP (9411) from your IP for Zipkin
  - Outbound:
    - Allow all
- **Network**
  - Public subnet
  - Auto-assign public IPv4 → **Enabled**
  - Outbound: Allow all
- **Key Pair**: Create and download SSH key

#### 1.2 Install Dependencies
```bash
# SSH into instance
ssh -i your-key.pem ec2-user@<instance-ip>

```bash
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user
exit
```

Reconnect (important).

Install compose plugin:

```bash
docker compose version
```

If missing:

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

Re-login to apply group changes

```bash
exit
ssh -i your-key.pem ec2-user@<instance-ip>
```

#### 1.3 Pull & start (daily workflow)

First time (or after instance start)

```bash
docker login ghcr.io
docker compose pull
docker compose up -d
```

Wait ~1–2 minutes.

Verify:

```bash
docker ps
docker stats
```

When done practicing (MOST IMPORTANT), Stop everything

```bash
docker compose down -v
```

#### 1.4 Test & Validate
- Access API Gateway: `http://<ec2-public-ip>:8080/api/products`
- Access Eureka: `http://<ec2-public-ip>:8761`
- Access Zipkin: `http://<ec2-public-ip>:9411`

### Deliverables
- [ ] EC2 instance running
- [ ] All services accessible via public IP
- [ ] Start/stop scripts created
- [ ] Documentation of EC2 IP and access methods

---

## Phase 2: VPC & IAM Setup (Week 2)

### Goal
Implement production-like networking with VPC, subnets, and proper IAM roles.

### Architecture
```
VPC (10.0.0.0/16)
├── Public Subnet 1 (10.0.1.0/24, AZ-a)
│   ├── EC2 with public IP (temporary for SSH/testing)
│   └── Application Load Balancer (future)
└── Public Subnet 2 (10.0.2.0/24, AZ-b)
    └── (Future redundancy)

Note: No private subnets/NAT gateway to save $45/month
```

### Steps

#### 2.1 Create VPC (Manual - AWS Console)
1. VPC with CIDR: 10.0.0.0/16
2. Enable DNS hostnames and DNS resolution
3. Create Internet Gateway and attach to VPC

#### 2.2 Create Subnets
1. Public Subnet 1: 10.0.1.0/24 (eu-west-1a or your region)
2. Public Subnet 2: 10.0.2.0/24 (eu-west-1b)
3. Edit subnet settings: Enable auto-assign public IPv4

#### 2.3 Create Route Table
1. Create route table for public subnets
2. Add route: 0.0.0.0/0 → Internet Gateway
3. Associate both public subnets

#### 2.4 Create Security Groups

**SG-Bastion** (for future SSH access):
- Inbound: SSH (22) from your IP
- Outbound: All traffic

**SG-ALB** (for future load balancer):
- Inbound: HTTP (80) from 0.0.0.0/0, HTTPS (443) from 0.0.0.0/0
- Outbound: All traffic

**SG-Application**:
- Inbound: 
  - 8080 from SG-ALB (or your IP for Phase 1)
  - 8761 from your IP (Eureka - temporary)
  - 9411 from your IP (Zipkin - temporary)
  - SSH (22) from SG-Bastion (or your IP)
- Outbound: All traffic

**SG-Database** (for Phase 4):
- Inbound: 
  - 3306 (MySQL) from SG-Application
  - 27017 (MongoDB) from SG-Application
- Outbound: All traffic

#### 2.5 Create IAM Roles

**EC2-Application-Role**:
- Managed Policies:
  - `AmazonSSMManagedInstanceCore` (for Systems Manager)
  - `CloudWatchAgentServerPolicy` (for CloudWatch logs)
- Inline Policy for Parameter Store:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/orderproducts/*"
    }
  ]
}
```

**ECS-Task-Execution-Role** (for Phase 5):
- Managed Policy: `AmazonECSTaskExecutionRolePolicy`
- Additional permissions for Parameter Store (same as above)

#### 2.6 Migrate EC2 to VPC
1. Create new EC2 instance in VPC Public Subnet 1
2. Attach EC2-Application-Role IAM role
3. Apply SG-Application security group
4. Deploy application (repeat Phase 1 steps 1.2-1.4)
5. Test and verify
6. Terminate old EC2 instance

#### 2.7 Setup AWS Systems Manager Session Manager (Optional)
- Enable Session Manager for SSH alternative
- No need to expose port 22 publicly
- Access via: `aws ssm start-session --target <instance-id>`

### Estimated Cost
- No additional cost (VPC is free)
- Same EC2 costs as Phase 1

### Deliverables
- [ ] VPC with subnets configured
- [ ] Security groups created
- [ ] IAM roles created
- [ ] EC2 running in VPC with IAM role
- [ ] Systems Manager access working (optional)

---

## Phase 3: Secrets Management (Week 3)

### Goal
Remove hardcoded secrets from codebase, use AWS Systems Manager Parameter Store.

### Current Secrets (from infrastructure/README.md)
- MySQL root password: `rootpassword`
- MySQL inventory_user: `kV33CaPPgSu1YuXJ`
- MySQL order_user: `nS3johd59oQIcZhN`
- MySQL debezium_user: `fBWsBYOGzcggYQfM`

### Steps

#### 3.1 Create Parameters in Parameter Store (AWS Console)
Navigate to Systems Manager → Parameter Store → Create parameter

**Standard Parameters** (Free tier):
- `/orderproducts/mysql/root-password` (SecureString)
- `/orderproducts/mysql/inventory-user` (String)
- `/orderproducts/mysql/inventory-password` (SecureString)
- `/orderproducts/mysql/order-user` (String)
- `/orderproducts/mysql/order-password` (SecureString)
- `/orderproducts/mysql/debezium-user` (String)
- `/orderproducts/mysql/debezium-password` (SecureString)

#### 3.2 Update Application Code

**Option A: Environment Variables from Parameter Store**
Create script to fetch and export parameters:
```bash
# ~/fetch-secrets.sh
#!/bin/bash
export MYSQL_ROOT_PASSWORD=$(aws ssm get-parameter --name /orderproducts/mysql/root-password --with-decryption --query 'Parameter.Value' --output text)
export MYSQL_INVENTORY_PASSWORD=$(aws ssm get-parameter --name /orderproducts/mysql/inventory-password --with-decryption --query 'Parameter.Value' --output text)
export MYSQL_ORDER_PASSWORD=$(aws ssm get-parameter --name /orderproducts/mysql/order-password --with-decryption --query 'Parameter.Value' --output text)
export MYSQL_DEBEZIUM_PASSWORD=$(aws ssm get-parameter --name /orderproducts/mysql/debezium-password --with-decryption --query 'Parameter.Value' --output text)
```

**Option B: Spring Cloud AWS Parameter Store Integration** (Recommended)
Add dependency to each service's `pom.xml`:
```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-parameter-store</artifactId>
    <version>3.0.3</version>
</dependency>
```

Update `application.properties` to reference parameters:
```properties
# Before: spring.datasource.password=kV33CaPPgSu1YuXJ
# After:  spring.datasource.password=${/orderproducts/mysql/inventory-password}
```

#### 3.3 Update docker-compose.yml
Replace hardcoded secrets with environment variables:
```yaml
mysql:
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
```

#### 3.4 Update Startup Script
```bash
# ~/start-services.sh
#!/bin/bash
source ~/fetch-secrets.sh
cd ~/OrderProducts/infrastructure
docker-compose up -d
```

### Estimated Cost
- Parameter Store Standard: Free (up to 10,000 parameters)
- **Total: $0**

### Deliverables
- [ ] All secrets stored in Parameter Store
- [ ] Application code updated to fetch from Parameter Store
- [ ] No hardcoded secrets in repository
- [ ] Updated startup scripts

---

## Phase 4: Separate Database & Messaging Infrastructure (Week 4-5)

### Goal
- Separate databases from application instance
- Replace Kafka with AWS SQS/SNS for cost savings
- Enable independent scaling and management

### Architecture
```
Application Instance (t3.micro)
├── discovery-server
├── api-gateway
├── product-service
├── inventory-service
└── order-service

Infrastructure Instance (t3.micro)
├── mysql
├── mongodb
├── zipkin
└── debezium (optional, may remove with SQS)
```

### Steps

#### 4.1 Launch Infrastructure EC2 Instance
- Instance Type: t3.micro
- Same VPC, Public Subnet 2
- Security Group: SG-Database
- IAM Role: EC2-Application-Role
- Install Docker and Docker Compose

#### 4.2 Move Databases to Infrastructure Instance
Create `infrastructure-compose.yml` on new instance:
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    command: ["mysqld", "--binlog-format=ROW", "--log-bin=mysql-bin", "--server-id=1"]
    volumes:
      - mysql_data:/var/lib/mysql
      - ./mysql_init_scripts:/docker-entrypoint-initdb.d
    networks:
      - infra-network

  mongodb:
    image: mongo:7.0
    container_name: mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    networks:
      - infra-network

  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    networks:
      - infra-network

volumes:
  mysql_data:
  mongodb_data:

networks:
  infra-network:
    driver: bridge
```

#### 4.3 Replace Kafka with AWS SQS/SNS

**A. Create SQS Queue (AWS Console)**
- Queue Name: `orderproducts-notification-queue`
- Type: Standard (cheaper, no ordering guarantee for notifications)
- Default settings (no dead-letter queue initially)

**B. Create SNS Topic (Optional for fan-out)**
- Topic Name: `orderproducts-order-events`
- Subscribe SQS queue to topic

**C. Update Order Service Code**

Remove Kafka dependencies from `order-service/pom.xml`:
```xml
<!-- Remove -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Add AWS SQS dependencies:
```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
    <version>3.0.3</version>
</dependency>
```

Create new event publisher:
```java
// Replace KafkaTemplate with SqsTemplate
@Service
public class OrderEventPublisher {
    private final SqsTemplate sqsTemplate;
    
    public void publishOrderEvent(OrderEvent event) {
        sqsTemplate.send("orderproducts-notification-queue", event);
    }
}
```

**D. Handle Debezium CDC Alternative**

Two options:
1. **Remove Debezium**: Publish events directly from Order Service (simpler, cheaper)
2. **Keep Debezium with SQS**: Use Debezium Server with SQS sink (more complex)

Recommendation: Remove Debezium, use Transactional Outbox pattern with direct SQS publishing.

#### 4.4 Update Application Configuration
Update `application.properties` in services:
```properties
# inventory-service, order-service
spring.datasource.url=jdbc:mysql://<infra-instance-private-ip>:3306/inventory_db

# product-service
spring.data.mongodb.uri=mongodb://<infra-instance-private-ip>:27017/product-service

# All services
management.zipkin.tracing.endpoint=http://<infra-instance-private-ip>:9411/api/v2/spans

# order-service (remove Kafka config)
# spring.kafka.bootstrap-servers=... (remove)

# Add SQS config
spring.cloud.aws.region.static=eu-west-1
spring.cloud.aws.sqs.endpoint=https://sqs.eu-west-1.amazonaws.com
```

#### 4.5 Update Application Instance docker-compose.yml
Remove infrastructure services, keep only application services:
```yaml
version: '3.8'
services:
  discovery-server:
    # ... same as before
  
  api-gateway:
    # ... same as before
    
  product-service:
    environment:
      - MONGODB_URI=mongodb://<infra-instance-private-ip>:27017/product-service
      - ZIPKIN_ENDPOINT=http://<infra-instance-private-ip>:9411/api/v2/spans
  
  inventory-service:
    environment:
      - MYSQL_HOST=<infra-instance-private-ip>
      - ZIPKIN_ENDPOINT=http://<infra-instance-private-ip>:9411/api/v2/spans
  
  order-service:
    environment:
      - MYSQL_HOST=<infra-instance-private-ip>
      - ZIPKIN_ENDPOINT=http://<infra-instance-private-ip>:9411/api/v2/spans
```

#### 4.6 Update IAM Role for SQS Access
Add inline policy to EC2-Application-Role:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ],
      "Resource": "arn:aws:sqs:*:*:orderproducts-*"
    }
  ]
}
```

### Alternative: AWS RDS Option (More Expensive)
If willing to spend more (~$15-30/month):
- RDS MySQL db.t3.micro or db.t4g.micro
- Manual start/stop when not in use (stopped instances: $0, storage: ~$2/month)
- Automated backups, maintenance, multi-AZ option

### Estimated Cost (Two EC2 Instances)
- EC2 Application (t3.micro): $7.50/month
- EC2 Infrastructure (t3.micro): $7.50/month
- EBS (2x 20GB): $4/month
- SQS: ~$0 (1M free requests/month)
- **Total: ~$19/month** (over budget!)

### Cost Optimization Alternative
Keep single EC2, only separate databases later when moving to ECS:
- Single EC2 t3.small (2GB RAM): $15/month
- EBS 30GB: $3/month
- **Total: ~$18/month**

### Recommended Approach
**Skip separate infrastructure EC2 for now**. Proceed directly to Phase 5 (ECS) where:
- Application services → ECS Fargate (pay per use)
- Infrastructure → Keep on single t3.micro EC2

### Deliverables
- [ ] Kafka replaced with AWS SQS/SNS
- [ ] Code changes tested locally
- [ ] Debezium removed or migrated
- [ ] Application works with remote databases
- [ ] Infrastructure consolidated on single instance

---

## Phase 5: ECS Fargate Migration (Week 6-8)

### Goal
Move application services to ECS Fargate for:
- Pay-per-use pricing (~$0 when not running)
- Auto-scaling capability
- Production-grade container orchestration

### Architecture
```
Application Load Balancer
└── Target Groups
    ├── api-gateway (ECS Fargate)
    ├── discovery-server (ECS Fargate)
    ├── product-service (ECS Fargate)
    ├── inventory-service (ECS Fargate)
    └── order-service (ECS Fargate)

Infrastructure EC2 (t3.micro)
├── mysql
├── mongodb
└── zipkin
```

### Steps

#### 5.1 Push Docker Images to ECR

**A. Create ECR Repositories**
```bash
aws ecr create-repository --repository-name orderproducts/discovery-server
aws ecr create-repository --repository-name orderproducts/api-gateway
aws ecr create-repository --repository-name orderproducts/product-service
aws ecr create-repository --repository-name orderproducts/inventory-service
aws ecr create-repository --repository-name orderproducts/order-service
```

**B. Build and Push Images**
```bash
# Login to ECR
aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.eu-west-1.amazonaws.com

# Build and push each service
cd discovery-server
docker build -t orderproducts/discovery-server .
docker tag orderproducts/discovery-server:latest <account-id>.dkr.ecr.eu-west-1.amazonaws.com/orderproducts/discovery-server:latest
docker push <account-id>.dkr.ecr.eu-west-1.amazonaws.com/orderproducts/discovery-server:latest

# Repeat for other services
```

#### 5.2 Create ECS Cluster
- Cluster Name: `orderproducts-cluster`
- Infrastructure: AWS Fargate (serverless)
- Container Insights: Disabled (to save costs, enable later if needed)

#### 5.3 Create Task Definitions

**Example: discovery-server Task Definition**
```json
{
  "family": "discovery-server",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::<account-id>:role/ECS-Task-Execution-Role",
  "taskRoleArn": "arn:aws:iam::<account-id>:role/EC2-Application-Role",
  "containerDefinitions": [
    {
      "name": "discovery-server",
      "image": "<account-id>.dkr.ecr.eu-west-1.amazonaws.com/orderproducts/discovery-server:latest",
      "portMappings": [
        {
          "containerPort": 8761,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "EUREKA_HOSTNAME",
          "value": "discovery-server.local"
        },
        {
          "name": "ZIPKIN_ENDPOINT",
          "value": "http://<infra-instance-private-ip>:9411/api/v2/spans"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/discovery-server",
          "awslogs-region": "eu-west-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      }
    }
  ]
}
```

Create similar task definitions for:
- api-gateway (CPU: 256, Memory: 512)
- product-service (CPU: 256, Memory: 512)
- inventory-service (CPU: 256, Memory: 512)
- order-service (CPU: 256, Memory: 512)

#### 5.4 Replace Eureka with AWS Cloud Map

**Why**: Native ECS service discovery, no need to run Eureka server.

**A. Create Cloud Map Namespace**
- Namespace: `orderproducts.local`
- Type: HTTP namespace (cheaper than DNS)

**B. Update Task Definitions**
Remove Eureka client dependencies, use Cloud Map for service discovery.

**C. Update Application Code**
Replace Eureka discovery with Cloud Map SDK or use ECS service discovery via DNS:
```properties
# Remove
eureka.client.service-url.defaultZone=http://discovery-server:8761/eureka

# Add (ECS handles this automatically)
# Services accessible via: http://service-name.orderproducts.local:8080
```

#### 5.5 Create Application Load Balancer

**A. Create ALB**
- Name: `orderproducts-alb`
- Scheme: Internet-facing
- Subnets: Public Subnet 1 & 2
- Security Group: SG-ALB

**B. Create Target Groups**
- `tg-api-gateway` (Port 8080, Target Type: IP)
- Health Check: `/actuator/health`

**C. Create Listener Rules**
- Port 80 → Forward to `tg-api-gateway`

#### 5.6 Create ECS Services

**Example: api-gateway Service**
- Service Name: `api-gateway-service`
- Task Definition: `api-gateway:latest`
- Cluster: `orderproducts-cluster`
- Launch Type: Fargate
- Desired Tasks: 1 (or 0 when not in use)
- Subnets: Public Subnet 1 & 2
- Security Group: SG-Application (allow 8080 from SG-ALB)
- Load Balancer: orderproducts-alb, target group: tg-api-gateway
- Service Discovery: Enable, namespace: orderproducts.local

Create similar services for other applications.

#### 5.7 Service Startup/Shutdown Scripts

**Start Services**
```bash
# ~/start-ecs-services.sh
#!/bin/bash
aws ecs update-service --cluster orderproducts-cluster --service api-gateway-service --desired-count 1
aws ecs update-service --cluster orderproducts-cluster --service product-service --desired-count 1
aws ecs update-service --cluster orderproducts-cluster --service inventory-service --desired-count 1
aws ecs update-service --cluster orderproducts-cluster --service order-service --desired-count 1
```

**Stop Services**
```bash
# ~/stop-ecs-services.sh
#!/bin/bash
aws ecs update-service --cluster orderproducts-cluster --service api-gateway-service --desired-count 0
aws ecs update-service --cluster orderproducts-cluster --service product-service --desired-count 0
aws ecs update-service --cluster orderproducts-cluster --service inventory-service --desired-count 0
aws ecs update-service --cluster orderproducts-cluster --service order-service --desired-count 0
```

### Estimated Cost (Per Month, Assuming 40 Hours Runtime)
- **ECS Fargate**:
  - 5 tasks × 0.25 vCPU × $0.04048/hour × 40 hours = $2.02
  - 5 tasks × 0.5 GB RAM × $0.004445/hour × 40 hours = $0.44
  - Subtotal: ~$2.50/month
- **ALB**: $16.20/month (base) + $0.008/LCU-hour (~$20/month total) - **EXPENSIVE!**
- **EC2 Infrastructure** (t3.micro): $7.50/month
- **EBS**: $2/month
- **ECR Storage**: ~$0.10/month (1GB)
- **CloudWatch Logs**: ~$0.50/month (500MB logs)
- **Total: ~$30/month** (over budget due to ALB!)

### Cost Optimization: Remove ALB
To stay under budget, skip ALB and use:
1. **Direct ECS Task Public IPs**: Access api-gateway directly (not production-grade)
2. **API Gateway (AWS Service)**: Use AWS API Gateway as entry point (~$3.50/1M requests)
3. **Keep Single EC2 with Docker Compose**: Skip ECS until budget allows

### Recommended: Defer Phase 5 for Now
**Stay on single EC2 with Docker Compose** until:
- Budget increases to accommodate ALB ($20/month)
- Or use Application Load Balancer alternatives (Nginx on EC2)

### Alternative: Nginx Reverse Proxy on EC2
- Keep all services on single EC2
- Use Nginx as lightweight load balancer
- Cost: $0 additional

### Deliverables
- [ ] ECR repositories created
- [ ] Docker images pushed to ECR
- [ ] ECS cluster created
- [ ] Task definitions created
- [ ] ECS services running
- [ ] Services accessible via ALB or public IP
- [ ] Start/stop scripts created

---

## Phase 6: Enhanced Observability (Week 9)

### Goal
Implement comprehensive observability with:
- Centralized logging (CloudWatch Logs)
- Distributed tracing (AWS X-Ray)
- Metrics and alarms (CloudWatch Metrics)
- Cost tracking (Cost Explorer, Budgets)

### Steps

#### 6.1 CloudWatch Logs Integration

**A. Update Task Definitions (ECS) or Docker Compose**
```yaml
# docker-compose.yml
logging:
  driver: awslogs
  options:
    awslogs-region: eu-west-1
    awslogs-group: /orderproducts/api-gateway
    awslogs-stream-prefix: docker
```

**B. Create Log Groups**
- `/orderproducts/api-gateway`
- `/orderproducts/product-service`
- `/orderproducts/inventory-service`
- `/orderproducts/order-service`
- `/orderproducts/discovery-server`

**C. Set Retention Policies**
- Retention: 7 days (to save costs)

#### 6.2 AWS X-Ray Integration

**A. Add X-Ray Dependencies**
```xml
<!-- pom.xml for all services -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-spring</artifactId>
    <version>2.15.0</version>
</dependency>
```

**B. Replace Zipkin with X-Ray**
```properties
# application.properties
# Remove Zipkin config
# management.zipkin.tracing.endpoint=...

# Add X-Ray
spring.application.name=order-service
management.tracing.sampling.probability=1.0
```

**C. Update IAM Role**
Add X-Ray permissions to task role:
```json
{
  "Effect": "Allow",
  "Action": [
    "xray:PutTraceSegments",
    "xray:PutTelemetryRecords"
  ],
  "Resource": "*"
}
```

**D. Remove Zipkin Container**
No longer needed, save resources.

#### 6.3 CloudWatch Metrics & Alarms

**A. Enable Container Insights (Optional, costs extra)**
- Cluster-level metrics
- ~$0.30/task/month

**B. Create Custom Metrics**
Use Micrometer to publish custom metrics:
```java
@Component
public class OrderMetrics {
    private final MeterRegistry registry;
    
    public void recordOrderPlaced() {
        registry.counter("orders.placed").increment();
    }
}
```

**C. Create Alarms**
- High CPU usage (>80% for 5 minutes)
- High memory usage (>80% for 5 minutes)
- API Gateway 5xx errors (>10 in 5 minutes)

#### 6.4 Cost Tracking

**A. Enable Cost Allocation Tags**
Tag all resources:
- Key: `Project`, Value: `OrderProducts`
- Key: `Environment`, Value: `Dev`
- Key: `ManagedBy`, Value: `Manual` (or `Terraform` later)

**B. Create Budget**
- Budget Name: `OrderProducts-Monthly-Budget`
- Amount: $20/month
- Alerts:
  - 80% threshold ($16)
  - 100% threshold ($20)
  - Forecast 100% threshold

**C. Cost Explorer**
- Create saved reports
- Filter by Project=OrderProducts tag
- Track costs by service (EC2, ECS, ALB, etc.)

#### 6.5 Structured Logging

**A. Update Log Format**
Use JSON structured logs:
```properties
# logback-spring.xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <includeMdcKeyName>traceId</includeMdcKeyName>
    <includeMdcKeyName>spanId</includeMdcKeyName>
  </encoder>
</appender>
```

**B. CloudWatch Insights Queries**
Example queries:
```
# Find all errors
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc

# Order processing time
fields @timestamp, duration
| filter operation = "place_order"
| stats avg(duration), max(duration), min(duration)
```

### Estimated Cost
- CloudWatch Logs: $0.50/GB ingested (~$1/month for 2GB)
- X-Ray: Free tier 100K traces/month (sufficient for development)
- CloudWatch Metrics: Free for basic metrics
- Cost Explorer: Free
- Budgets: First 2 budgets free
- **Total: ~$1-2/month**

### Deliverables
- [ ] All services logging to CloudWatch
- [ ] X-Ray tracing configured
- [ ] Cost allocation tags applied
- [ ] Budget alerts configured
- [ ] CloudWatch alarms created
- [ ] Structured logging implemented

---

## Phase 7: Advanced Features (Week 10+)

### Goal
Add production-grade capabilities:
- Auto-scaling
- Rate limiting
- Enhanced security
- CI/CD pipeline
- Infrastructure as Code (Terraform)

### 7.1 Auto-Scaling (ECS)

**Target Tracking Scaling**
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleOutCooldown": 60,
  "ScaleInCooldown": 300
}
```

Config:
- Min Tasks: 1
- Max Tasks: 3
- Target CPU: 70%

### 7.2 Rate Limiting (API Gateway)

**Option A: Spring Cloud Gateway Rate Limiter**
```yaml
# api-gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

**Option B: AWS WAF (more expensive, ~$10/month)**
- Create Web ACL
- Add rate-based rule (100 requests per 5 minutes per IP)
- Associate with ALB

### 7.3 Enhanced Security

**A. HTTPS with ACM Certificate**
- Request certificate from AWS Certificate Manager (free)
- Add HTTPS listener to ALB (port 443)
- Redirect HTTP → HTTPS

**B. Secrets Rotation**
- Enable automatic rotation for Parameter Store secrets
- Lambda function to rotate database passwords

**C. VPC Endpoints (to avoid internet charges)**
- VPC Endpoint for ECR
- VPC Endpoint for CloudWatch Logs
- VPC Endpoint for Systems Manager
- Cost: $0.01/hour per endpoint (~$7/month per endpoint)

**D. WAF Rules**
- SQL injection protection
- XSS protection
- Geo-blocking (if needed)

### 7.4 CI/CD Pipeline

**GitHub Actions Workflow**
```yaml
# .github/workflows/deploy.yml
name: Deploy to ECS
on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: eu-west-1
      
      - name: Build and push Docker image
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REGISTRY
          docker build -t order-service .
          docker tag order-service:latest $ECR_REGISTRY/orderproducts/order-service:latest
          docker push $ECR_REGISTRY/orderproducts/order-service:latest
      
      - name: Deploy to ECS
        run: |
          aws ecs update-service --cluster orderproducts-cluster --service order-service --force-new-deployment
```

### 7.5 Infrastructure as Code (Terraform)

**Project Structure**
```
terraform/
├── main.tf              # Provider, backend config
├── vpc.tf               # VPC, subnets, security groups
├── iam.tf               # IAM roles and policies
├── ecs.tf               # ECS cluster, task definitions, services
├── alb.tf               # Application Load Balancer
├── cloudwatch.tf        # Log groups, alarms
├── variables.tf         # Input variables
├── outputs.tf           # Output values
└── terraform.tfvars     # Variable values
```

**Example: VPC Module**
```hcl
# vpc.tf
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "orderproducts-vpc"
    Project     = "OrderProducts"
    ManagedBy   = "Terraform"
  }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index + 1}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "orderproducts-public-${count.index + 1}"
  }
}
```

**Benefits**
- Version control infrastructure
- Reproducible environments
- Easy teardown (save costs)
- State management

**Usage**
```bash
# Initialize
terraform init

# Plan changes
terraform plan

# Apply changes
terraform apply

# Destroy all (when done testing)
terraform destroy
```

### 7.6 Cost Optimization Checklist

- [ ] Use Fargate Spot (70% discount, may interrupt)
- [ ] Schedule ECS tasks (run only during work hours)
- [ ] Use S3 for static assets (cheaper than ALB)
- [ ] Reserved Instances for EC2 (if running 24/7)
- [ ] Savings Plans for ECS Fargate
- [ ] CloudWatch log retention (7 days max)
- [ ] Delete unused EBS snapshots
- [ ] Review and delete old ECR images

---

## Cost Summary

### Minimal Setup (Phase 1-3): ~$7-10/month
- EC2 t3.micro: $7.50
- EBS 20GB: $2
- Parameter Store: $0
- **Total: ~$10/month** ✅

### Current State (Phase 4): ~$10/month
- EC2 t3.micro: $7.50
- EBS 20GB: $2
- SQS: ~$0
- Parameter Store: $0
- **Total: ~$10/month** ✅

### With ECS (Phase 5): ~$30/month
- ECS Fargate (40 hours): $2.50
- ALB: $20
- EC2 Infrastructure: $7.50
- EBS: $2
- ECR: $0.10
- CloudWatch: $1
- **Total: ~$33/month** ❌ (over budget)

### Optimized ECS (No ALB): ~$13/month
- ECS Fargate (40 hours): $2.50
- EC2 Infrastructure: $7.50
- EBS: $2
- ECR: $0.10
- CloudWatch: $1
- Access via API Gateway (AWS): $3.50
- **Total: ~$17/month** ⚠️ (slightly over, but acceptable)

### Recommended Path for Budget
1. **Phase 1-4**: Stay on single EC2 with Docker Compose ($10/month)
2. **Phase 6**: Add observability ($12/month)
3. **Phase 5+7**: Migrate to ECS when budget allows ($30+/month) or use Nginx instead of ALB

---

## EC2/EBS Cost Optimization Strategies

### Understanding AWS Costs When Instances Are Stopped

#### EC2 Stopped Instances: NOT Completely Free ⚠️

**What You DON'T Pay:**
- ✅ EC2 compute hours: $0
- ✅ Data transfer: $0

**What You STILL Pay:**
- ❌ **EBS volumes**: $0.08/GB/month (gp3) - **20GB = $1.60/month**
- ❌ **EBS snapshots**: $0.05/GB/month
- ❌ **Elastic IPs** (if allocated but not attached): $0.005/hour = $3.60/month
- ❌ **AMIs** (stored as snapshots): $0.05/GB/month

**Example Cost Breakdown:**
```
Scenario: t3.micro with 20GB gp3 EBS, running 40 hours/month

When RUNNING (40 hours):
- EC2 compute: $0.0104/hour × 40 = $0.416
- EBS storage: $0.08/GB × 20 × (1 month) = $1.60
- Total: ~$2.02/month

When STOPPED (rest of month):
- EC2 compute: $0
- EBS storage: $1.60/month (still charged!)
- Total: ~$1.60/month

Monthly Total: ~$3.62/month (not $0.42!)
```

#### RDS Stopped Instances: Even Worse 🚫

**What You DON'T Pay:**
- ✅ RDS compute hours: $0

**What You STILL Pay:**
- ❌ **Storage**: $0.115/GB/month (gp3)
- ❌ **Automated backups**: $0.095/GB/month
- ❌ **Snapshots**: $0.095/GB/month
- ❌ **Data transfer**: Standard rates

**Critical Limitation:**
- ⚠️ **RDS auto-starts after 7 days** of being stopped
- Cannot stay stopped indefinitely
- Must manually stop again every 7 days or set up automation

**Example Cost:**
```
db.t3.micro with 20GB storage, stopped:
- Storage: $0.115 × 20 = $2.30/month
- Backup (20GB): $0.095 × 20 = $1.90/month
- Total when STOPPED: ~$4.20/month

Running continuously:
- Compute: $0.017/hour × 730 = $12.41/month
- Storage + backups: $4.20/month
- Total: ~$16.61/month
```

**Verdict**: ❌ **DO NOT use RDS for this project** - use self-managed MySQL/MongoDB in Docker on EC2

---

### Strategy 1: Stop/Start EC2 (Simple, Some Cost)

**Best For**: Quick sessions, don't mind paying ~$2/month when idle

#### Implementation
```bash
# Stop instance when done
aws ec2 stop-instances --instance-ids i-xxxxx

# Start instance when needed
aws ec2 start-instances --instance-ids i-xxxxx

# Get new public IP (changes every start unless using Elastic IP)
aws ec2 describe-instances --instance-ids i-xxxxx --query 'Reservations[0].Instances[0].PublicIpAddress'
```

#### Cost Analysis
```
Monthly cost (40 hours usage):
- Running: $0.416 (compute) + $1.60 (EBS) = $2.02
- Stopped: $1.60 (EBS only)
- Total: ~$3.62/month
```

**Pros:**
- ✅ Simple - just stop/start
- ✅ Data persists automatically
- ✅ Fast startup (~1-2 minutes)
- ✅ No manual restore process

**Cons:**
- ❌ Still pay ~$1.60/month for EBS when stopped
- ❌ Public IP changes each start (unless Elastic IP, which costs $3.60/month)

---

### Strategy 2: Snapshot + Terminate (Minimal Cost, More Work)

**Best For**: Long idle periods (weeks/months), minimize costs to ~$1/month

#### Implementation

**A. Create Comprehensive Snapshot Script**

```bash
#!/bin/bash
# ~/snapshot-and-terminate.sh

INSTANCE_ID="i-xxxxx"
PROJECT_TAG="OrderProducts"
DATE=$(date +%Y%m%d-%H%M%S)

echo "Creating snapshots for OrderProducts instance..."

# Get all EBS volume IDs attached to instance
VOLUME_IDS=$(aws ec2 describe-instances \
  --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].BlockDeviceMappings[*].Ebs.VolumeId' \
  --output text)

# Create snapshot for each volume
for VOLUME_ID in $VOLUME_IDS; do
  echo "Creating snapshot for volume $VOLUME_ID..."
  
  SNAPSHOT_ID=$(aws ec2 create-snapshot \
    --volume-id $VOLUME_ID \
    --description "$PROJECT_TAG-snapshot-$DATE" \
    --tag-specifications "ResourceType=snapshot,Tags=[{Key=Project,Value=$PROJECT_TAG},{Key=Date,Value=$DATE},{Key=AutoCreated,Value=true}]" \
    --query 'SnapshotId' \
    --output text)
  
  echo "Snapshot created: $SNAPSHOT_ID"
  
  # Wait for snapshot to complete (optional, can skip)
  echo "Waiting for snapshot to complete..."
  aws ec2 wait snapshot-completed --snapshot-ids $SNAPSHOT_ID
  echo "Snapshot $SNAPSHOT_ID completed!"
done

# Optional: Create AMI (includes all volumes)
echo "Creating AMI..."
AMI_ID=$(aws ec2 create-image \
  --instance-id $INSTANCE_ID \
  --name "$PROJECT_TAG-ami-$DATE" \
  --description "OrderProducts application snapshot" \
  --tag-specifications "ResourceType=image,Tags=[{Key=Project,Value=$PROJECT_TAG},{Key=Date,Value=$DATE}]" \
  --query 'ImageId' \
  --output text)

echo "AMI created: $AMI_ID"
echo "Waiting for AMI to be available..."
aws ec2 wait image-available --image-ids $AMI_ID

# Terminate instance
echo "Terminating instance $INSTANCE_ID..."
aws ec2 terminate-instances --instance-ids $INSTANCE_ID

echo "Done! Instance terminated, snapshots saved."
echo "Snapshot IDs: $VOLUME_IDS"
echo "AMI ID: $AMI_ID"
```

**B. Restore from Snapshot Script**

```bash
#!/bin/bash
# ~/restore-from-snapshot.sh

AMI_ID="ami-xxxxx"  # Your latest AMI
INSTANCE_TYPE="t3.micro"
SUBNET_ID="subnet-xxxxx"
SECURITY_GROUP_ID="sg-xxxxx"
KEY_NAME="your-key-pair"
IAM_ROLE="EC2-Application-Role"

echo "Launching new instance from AMI $AMI_ID..."

INSTANCE_ID=$(aws ec2 run-instances \
  --image-id $AMI_ID \
  --instance-type $INSTANCE_TYPE \
  --subnet-id $SUBNET_ID \
  --security-group-ids $SECURITY_GROUP_ID \
  --key-name $KEY_NAME \
  --iam-instance-profile Name=$IAM_ROLE \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=OrderProducts},{Key=Project,Value=OrderProducts}]' \
  --query 'Instances[0].InstanceId' \
  --output text)

echo "Instance launched: $INSTANCE_ID"
echo "Waiting for instance to be running..."
aws ec2 wait instance-running --instance-ids $INSTANCE_ID

# Get public IP
PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo "Instance ready!"
echo "Instance ID: $INSTANCE_ID"
echo "Public IP: $PUBLIC_IP"
echo "SSH: ssh -i your-key.pem ec2-user@$PUBLIC_IP"
```

**C. Automated Snapshot Cleanup Script**

```bash
#!/bin/bash
# ~/cleanup-old-snapshots.sh

PROJECT_TAG="OrderProducts"
RETENTION_DAYS=30  # Keep snapshots for 30 days

echo "Finding snapshots older than $RETENTION_DAYS days..."

CUTOFF_DATE=$(date -d "$RETENTION_DAYS days ago" +%Y-%m-%d)

# Find old snapshots
OLD_SNAPSHOTS=$(aws ec2 describe-snapshots \
  --owner-ids self \
  --filters "Name=tag:Project,Values=$PROJECT_TAG" \
  --query "Snapshots[?StartTime<='$CUTOFF_DATE'].SnapshotId" \
  --output text)

if [ -z "$OLD_SNAPSHOTS" ]; then
  echo "No old snapshots to delete."
  exit 0
fi

echo "Found snapshots to delete: $OLD_SNAPSHOTS"

# Delete old snapshots
for SNAPSHOT_ID in $OLD_SNAPSHOTS; do
  echo "Deleting snapshot $SNAPSHOT_ID..."
  aws ec2 delete-snapshot --snapshot-id $SNAPSHOT_ID
done

echo "Cleanup complete!"
```

#### Cost Analysis
```
Monthly cost (40 hours usage, with snapshots):
- Running: $0.416 (compute)
- Snapshot storage: $0.05/GB × 20GB = $1.00/month
- Total: ~$1.42/month (60% cheaper than stop/start!)

When NOT running:
- Compute: $0
- EBS: $0 (terminated)
- Snapshot: $1.00/month
- Total: ~$1.00/month
```

**Pros:**
- ✅ Minimal cost when idle (~$1/month for snapshots)
- ✅ No EBS charges when terminated
- ✅ Can keep multiple snapshots for backup
- ✅ Clean state each time (no cruft accumulation)

**Cons:**
- ❌ More complex - need to create/restore snapshots
- ❌ Slower startup (~5-10 minutes including restore)
- ❌ Public IP changes every time
- ❌ Instance ID changes every time
- ❌ Risk of forgetting to snapshot before termination

---

### Strategy 3: Hybrid Approach (Recommended for Your Use Case)

**Use stop/start during active development**, **snapshot+terminate for long breaks**

#### Decision Tree
```
Are you working on the project this week?
├─ YES → Keep instance stopped ($1.60/month)
│   └─ Quick start when needed
└─ NO (taking a break for weeks/months)
    └─ Snapshot + terminate ($1/month)
        └─ Restore when you come back
```

#### Implementation

**Enhanced Start Script with Check**
```bash
#!/bin/bash
# ~/smart-start.sh

INSTANCE_ID="i-xxxxx"  # Update this
PROJECT_TAG="OrderProducts"

# Check if instance exists
INSTANCE_STATE=$(aws ec2 describe-instances \
  --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].State.Name' \
  --output text 2>/dev/null)

if [ "$INSTANCE_STATE" == "stopped" ]; then
  echo "Instance exists and is stopped. Starting..."
  aws ec2 start-instances --instance-ids $INSTANCE_ID
  aws ec2 wait instance-running --instance-ids $INSTANCE_ID
  
elif [ "$INSTANCE_STATE" == "running" ]; then
  echo "Instance is already running."
  
else
  echo "Instance not found. Need to restore from snapshot."
  echo "Available AMIs:"
  aws ec2 describe-images \
    --owners self \
    --filters "Name=tag:Project,Values=$PROJECT_TAG" \
    --query 'Images[*].[ImageId,CreationDate,Name]' \
    --output table
  
  echo ""
  echo "Run restore-from-snapshot.sh with the desired AMI ID"
  exit 1
fi

# Get IP and show connection info
PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo "Instance ready at: $PUBLIC_IP"
echo "Connect: ssh -i your-key.pem ec2-user@$PUBLIC_IP"
```

---

### Strategy 4: Ultimate Cost Optimization with Lambda (Advanced)

**Completely automate snapshot/restore with AWS Lambda**

#### Architecture
```
CloudWatch Events (Scheduled)
├─ Daily Check: "Is instance being used?"
├─ If idle for 7 days → Trigger Lambda
│   └─ Create snapshot + terminate instance
└─ On-demand: User triggers Lambda
    └─ Restore from latest snapshot
```

#### Lambda Function (Python)
```python
import boto3
import os
from datetime import datetime, timedelta

ec2 = boto3.client('ec2')
cloudwatch = boto3.client('cloudwatch')

def lambda_handler(event, context):
    instance_id = os.environ['INSTANCE_ID']
    project_tag = 'OrderProducts'
    
    # Check CloudWatch metrics for activity
    response = cloudwatch.get_metric_statistics(
        Namespace='AWS/EC2',
        MetricName='CPUUtilization',
        Dimensions=[{'Name': 'InstanceId', 'Value': instance_id}],
        StartTime=datetime.utcnow() - timedelta(days=7),
        EndTime=datetime.utcnow(),
        Period=86400,  # Daily
        Statistics=['Average']
    )
    
    # If no activity for 7 days, snapshot and terminate
    if not response['Datapoints'] or all(d['Average'] < 1 for d in response['Datapoints']):
        print(f"Instance {instance_id} idle for 7 days. Creating snapshot...")
        
        # Create AMI
        timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
        ami_response = ec2.create_image(
            InstanceId=instance_id,
            Name=f'{project_tag}-auto-{timestamp}',
            Description='Auto-created by idle detection',
            TagSpecifications=[{
                'ResourceType': 'image',
                'Tags': [
                    {'Key': 'Project', 'Value': project_tag},
                    {'Key': 'AutoCreated', 'Value': 'true'},
                    {'Key': 'Date', 'Value': timestamp}
                ]
            }]
        )
        
        ami_id = ami_response['ImageId']
        print(f"AMI created: {ami_id}")
        
        # Wait for AMI to be available
        waiter = ec2.get_waiter('image_available')
        waiter.wait(ImageIds=[ami_id])
        
        # Terminate instance
        ec2.terminate_instances(InstanceIds=[instance_id])
        print(f"Instance {instance_id} terminated.")
        
        return {
            'statusCode': 200,
            'body': f'Instance terminated, AMI: {ami_id}'
        }
    else:
        print("Instance has recent activity. Keeping alive.")
        return {
            'statusCode': 200,
            'body': 'Instance active, no action taken'
        }
```

**Cost**: Lambda free tier (1M requests/month), saves you from forgetting to terminate

---

### Comparison Table

| Strategy | Monthly Cost (Idle) | Monthly Cost (40h use) | Complexity | Startup Time | Data Persistence |
|----------|---------------------|------------------------|------------|--------------|------------------|
| **Always Running** | $9.50 | $9.50 | Low | Instant | Yes |
| **Stop/Start** | $1.60 | $3.62 | Low | 1-2 min | Yes |
| **Snapshot/Terminate** | $1.00 | $1.42 | Medium | 5-10 min | Manual |
| **Hybrid** | $1.00-1.60 | $1.42-3.62 | Medium | 1-10 min | Manual |
| **Lambda Automation** | $1.00 | $1.42 | High | 5-10 min | Auto |

---

### Recommended Approach for Your Project

**Phase 1-3** (Learning AWS):
- Use **Stop/Start** strategy
- Simple, predictable, fast
- Cost: ~$3-4/month

**Phase 4+** (Familiar with AWS):
- Switch to **Snapshot/Terminate** for long breaks
- Use **Stop/Start** for active weeks
- Cost: ~$1-2/month average

**Phase 7** (Advanced):
- Implement **Lambda automation** with Terraform
- Completely hands-off
- Cost: ~$1-2/month

---

### Updated Cost Estimates with Optimization

#### Scenario 1: Active Development (Using Stop/Start)
```
Monthly usage: 40 hours over 4 weekends
- Running time: 40 hours × $0.0104/hour = $0.42
- EBS storage (full month): 20GB × $0.08/GB = $1.60
- Total: ~$2.02/month ✅
```

#### Scenario 2: Occasional Use (Using Snapshot/Terminate)
```
Monthly usage: 40 hours over 2 weekends
- Running time: 40 hours × $0.0104/hour = $0.42
- EBS snapshots: 20GB × $0.05/GB = $1.00
- Total: ~$1.42/month ✅ (30% cheaper!)
```

#### Scenario 3: Long Break (Snapshot + Delete Everything)
```
Not using for 3 months:
- Keep 1 snapshot: 20GB × $0.05/GB × 3 months = $3.00 total
- Average per month: $1.00/month ✅
```

---

### Action Items

Add to your workflow:

1. **Create snapshot scripts** (week 1)
   - [ ] Write snapshot-and-terminate.sh
   - [ ] Write restore-from-snapshot.sh
   - [ ] Test snapshot creation
   - [ ] Test restore process
   - [ ] Document AMI IDs and dates

2. **Setup cost alerts** (week 2)
   - [ ] CloudWatch alarm for EBS costs > $2
   - [ ] Budget alert at 80% of $10/month
   - [ ] Weekly cost review in Cost Explorer

3. **Automate cleanup** (week 3)
   - [ ] Create cleanup-old-snapshots.sh
   - [ ] Test deletion of old snapshots
   - [ ] Schedule monthly cleanup (cron or manually)

4. **Document your AMIs** (ongoing)
   - [ ] Keep spreadsheet/notes of AMI IDs
   - [ ] Tag AMIs with feature/phase info
   - [ ] Delete old AMIs after testing new ones

---

## Migration Rollback Plan

Each phase should have a rollback strategy:

### Phase 1 Rollback
- Terminate EC2 instance
- Return to local Docker Compose

### Phase 2 Rollback
- Continue using EC2 in default VPC
- Delete VPC resources (no cost impact)

### Phase 3 Rollback
- Revert to hardcoded secrets in docker-compose.yml
- Delete Parameter Store parameters

### Phase 4 Rollback
- Restore Kafka in docker-compose.yml
- Remove SQS queue
- Revert code changes

### Phase 5 Rollback
- Scale ECS services to 0
- Start EC2 with docker-compose
- Keep ECS resources for future use

---

## Progress Checklist

### Phase 1: Single EC2 (Week 1)
- [ ] Launch EC2 instance (t3.micro)
- [ ] Configure security groups (SSH, HTTP ports)
- [ ] Install Docker and Docker Compose
- [ ] Clone repository
- [ ] Start all services via docker-compose
- [ ] Test API Gateway endpoint
- [ ] Test Eureka dashboard access
- [ ] Test Zipkin access
- [ ] Create start/stop scripts
- [ ] Document public IP and access methods
- [ ] Verify cost (<$10/month)

### Phase 2: VPC & IAM (Week 2)
- [ ] Create VPC (10.0.0.0/16)
- [ ] Create Internet Gateway
- [ ] Create public subnets (2 AZs)
- [ ] Create route tables
- [ ] Create security groups (SG-Application, SG-ALB, SG-Database)
- [ ] Create IAM role: EC2-Application-Role
- [ ] Create IAM role: ECS-Task-Execution-Role
- [ ] Add Parameter Store permissions to roles
- [ ] Add CloudWatch permissions to roles
- [ ] Launch new EC2 in VPC with IAM role
- [ ] Migrate application to new EC2
- [ ] Test Systems Manager Session Manager access (optional)
- [ ] Terminate old EC2 instance
- [ ] Verify application still works

### Phase 3: Secrets Management (Week 3)
- [ ] Create Parameter Store parameters (7 total)
- [ ] Test parameter retrieval with AWS CLI
- [ ] Create fetch-secrets.sh script
- [ ] Update docker-compose.yml to use env vars
- [ ] Update start-services.sh to source secrets
- [ ] Test application startup with Parameter Store
- [ ] Remove hardcoded secrets from repository
- [ ] Commit and push changes
- [ ] Verify no secrets in git history (or clean history)

### Phase 4: Messaging Migration (Week 4-5)
- [ ] Create SQS queue: orderproducts-notification-queue
- [ ] Create SNS topic (optional): orderproducts-order-events
- [ ] Update IAM role with SQS permissions
- [ ] Remove Kafka dependencies from order-service
- [ ] Add AWS SQS dependencies
- [ ] Implement SqsTemplate event publisher
- [ ] Update application.properties (remove Kafka config)
- [ ] Test locally with LocalStack or AWS SQS
- [ ] Remove Debezium from docker-compose.yml
- [ ] Remove Zookeeper and Kafka from docker-compose.yml
- [ ] Deploy updated services to EC2
- [ ] Test order placement → SQS message flow
- [ ] Verify cost savings (no Kafka containers)
- [ ] Update documentation

### Phase 5: ECS Fargate (Week 6-8) - OPTIONAL
- [ ] Create ECR repositories (5 services)
- [ ] Build Docker images locally
- [ ] Push images to ECR
- [ ] Create ECS cluster: orderproducts-cluster
- [ ] Create Cloud Map namespace: orderproducts.local
- [ ] Create task definition: discovery-server
- [ ] Create task definition: api-gateway
- [ ] Create task definition: product-service
- [ ] Create task definition: inventory-service
- [ ] Create task definition: order-service
- [ ] Create Application Load Balancer (or skip)
- [ ] Create target group: tg-api-gateway
- [ ] Create ALB listener rules
- [ ] Create ECS service: api-gateway-service
- [ ] Create ECS service: product-service
- [ ] Create ECS service: inventory-service
- [ ] Create ECS service: order-service
- [ ] Test service discovery between tasks
- [ ] Test ALB → api-gateway routing
- [ ] Create start-ecs-services.sh script
- [ ] Create stop-ecs-services.sh script
- [ ] Verify cost (~$30/month or optimized ~$17/month)
- [ ] Keep EC2 running for databases

### Phase 6: Observability (Week 9)
- [ ] Create CloudWatch Log Groups (5 services)
- [ ] Set retention policy (7 days)
- [ ] Update docker-compose/task definitions with awslogs driver
- [ ] Test log streaming to CloudWatch
- [ ] Add X-Ray SDK dependencies to all services
- [ ] Update application.properties for X-Ray
- [ ] Add X-Ray permissions to IAM roles
- [ ] Remove Zipkin container
- [ ] Test X-Ray trace collection
- [ ] View traces in X-Ray console
- [ ] Enable cost allocation tags on all resources
- [ ] Create AWS Budget ($20/month with alerts)
- [ ] Create CloudWatch alarms (CPU, memory, errors)
- [ ] Configure structured JSON logging
- [ ] Create CloudWatch Insights queries
- [ ] Test end-to-end observability
- [ ] Document monitoring procedures

### Phase 7: Advanced Features (Week 10+) - FUTURE
- [ ] Configure ECS auto-scaling policies
- [ ] Implement rate limiting in API Gateway
- [ ] Request ACM certificate
- [ ] Add HTTPS listener to ALB
- [ ] Configure HTTP → HTTPS redirect
- [ ] Setup secrets rotation Lambda
- [ ] Create WAF Web ACL
- [ ] Add WAF rules (SQL injection, XSS)
- [ ] Create GitHub Actions workflow
- [ ] Test CI/CD pipeline
- [ ] Initialize Terraform project
- [ ] Create Terraform modules (VPC, ECS, ALB, etc.)
- [ ] Import existing resources to Terraform state
- [ ] Test terraform plan
- [ ] Test terraform apply
- [ ] Test terraform destroy (in staging/test environment)
- [ ] Document Terraform usage
- [ ] Review cost optimization opportunities
- [ ] Implement Fargate Spot (if applicable)

### Ongoing Tasks
- [ ] Weekly cost review in Cost Explorer
- [ ] Monthly budget vs actual reconciliation
- [ ] Quarterly architecture review
- [ ] Security audit (unused resources, open security groups)
- [ ] Performance testing and optimization
- [ ] Update documentation as changes are made

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Pricing](https://aws.amazon.com/fargate/pricing/)
- [RDS Pricing Calculator](https://calculator.aws/)
- [Best Practices for ECS](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/)
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)

### Cost Optimization
- [AWS Trusted Advisor](https://aws.amazon.com/premiumsupport/technology/trusted-advisor/)
- [AWS Compute Optimizer](https://aws.amazon.com/compute-optimizer/)
- [Reserved Instance Recommendations](https://aws.amazon.com/aws-cost-management/reserved-instance-reporting/)

### Terraform Resources
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [ECS Terraform Module](https://registry.terraform.io/modules/terraform-aws-modules/ecs/aws/latest)
- [VPC Terraform Module](https://registry.terraform.io/modules/terraform-aws-modules/vpc/aws/latest)

### Spring Cloud AWS
- [Spring Cloud AWS Documentation](https://docs.awspring.io/spring-cloud-aws/docs/3.0.0/reference/html/)
- [SQS Integration](https://docs.awspring.io/spring-cloud-aws/docs/3.0.0/reference/html/index.html#sqs-integration)
- [Parameter Store Integration](https://docs.awspring.io/spring-cloud-aws/docs/3.0.0/reference/html/index.html#integrating-your-spring-cloud-application-with-the-aws-parameter-store)

---

## Notes

1. **Data Persistence**: Not implemented in Phase 1-4. To add later:
   - Attach separate EBS volume for MySQL/MongoDB data
   - Configure volume mounts in docker-compose
   - Create EBS snapshots before stopping instance

2. **High Availability**: Not included in this plan (single instance, single AZ)
   - To add: Multi-AZ deployment, auto-scaling groups, RDS Multi-AZ

3. **Disaster Recovery**: Not implemented
   - To add: Automated backups, cross-region replication

4. **Security Hardening**: Basic security only
   - To add: Network ACLs, VPC Flow Logs, GuardDuty, Config Rules

5. **Performance Testing**: Not included
   - Tools: Apache JMeter, Gatling, k6
   - Test gradually scaling from 10 → 100 → 1000 requests/sec

6. **Code Changes Required**:
   - Phase 4: Replace Kafka with SQS (order-service)
   - Phase 5: Replace Eureka with Cloud Map (all services)
   - Phase 6: Replace Zipkin with X-Ray (all services)

---

**Last Updated**: 2025-12-24
**Version**: 1.0
**Author**: Claude AI
**Status**: Ready for Review

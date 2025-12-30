# Terraform to create and setup single EC2 instance

## Create and setup single EC2 instance

```bash
# Login via AWS SSO: Note AWS setup is configured via SSH
aws sso login --profile ashishjha-admin

# Tell Terraform to use the SSO profile
export AWS_PROFILE=ashishjha-admin

# Run terraform
terraform init
terraform plan
terraform apply
```

NOTE: It will generate output in following format (when successful):

```bash
ec2_public_dns = "ec2-34-241-108-7.eu-west-1.compute.amazonaws.com"
ec2_public_ip = "34.241.108.7"
```

## Destroy the infrastructure created

This will destroy EC2 and Security Group

```bash
terraform destroy
```
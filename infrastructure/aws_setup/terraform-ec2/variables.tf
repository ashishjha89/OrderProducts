variable "my_ip" {
  description = "My public IP for SSH/HTTP access"
  type        = string
  default     = "89.99.47.2/32"
}

variable "key_name" {
  description = "Existing EC2 key pair name"
  type        = string
  default     = "orderproducts-ec2-key"
}

variable "instance_type" {
  default = "t3.large"
}

variable "ami_id" {
  description = "Amazon Linux 2023 AMI"
  default     = "ami-09c54d172e7aa3d9a"
}

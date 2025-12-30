resource "aws_security_group" "ec2_sg" {
  name        = "orderproducts-ec2-sg"
  description = "Security group for OrderProducts EC2"

  ingress {
    description = "SSH from my IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
    description = "HTTP 8080 from my IP"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
  description     = "EC2 Instance Connect"
  from_port       = 22
  to_port         = 22
  protocol        = "tcp"
  prefix_list_ids = ["pl-0839cc4c195a4e751"]
}

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "orderproducts-ec2-sg"
  }
}

resource "aws_instance" "orderproducts" {
  ami           = var.ami_id
  instance_type = var.instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [
    aws_security_group.ec2_sg.id
  ]

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
  }

  user_data = file("${path.module}/user_data.sh")

  tags = {
    Name = "orderproducts-ec2"
  }
}


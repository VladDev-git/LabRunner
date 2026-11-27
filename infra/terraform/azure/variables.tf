variable "subscription_id" {
  description = "Azure subscription ID (Azure for Students). Pass via terraform.tfvars or TF_VAR_subscription_id."
  type        = string
}

variable "project" {
  description = "Project name used in resource names and tags."
  type        = string
  default     = "labrunner"
}

variable "environment" {
  description = "Environment name (part of resource names)."
  type        = string
  default     = "lab4"
}

variable "location" {
  description = "Azure region. Azure for Students allows only a few regions (subscription policy)."
  type        = string
  default     = "polandcentral"
}

variable "vm_size" {
  description = "VM size: 2 vCPU / 4 GiB RAM is enough for the Docker Compose stack."
  type        = string
  default     = "Standard_B2als_v2"
}

variable "admin_username" {
  description = "Linux admin user for SSH and Ansible."
  type        = string
  default     = "labrunner"
}

variable "ssh_public_key_path" {
  description = "Path to the public SSH key that is installed on the VM."
  type        = string
  default     = "~/.ssh/labrunner_azure.pub"
}

variable "ssh_allowed_cidr" {
  description = "CIDR allowed to reach SSH. Empty = the public IP of the machine running Terraform (/32)."
  type        = string
  default     = ""
}

variable "vnet_cidr" {
  description = "Address space of the virtual network."
  type        = string
  default     = "10.40.0.0/16"
}

variable "subnet_cidr" {
  description = "Address range of the application subnet."
  type        = string
  default     = "10.40.1.0/24"
}

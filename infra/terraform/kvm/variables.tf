variable "libvirt_uri" {
  description = "libvirt connection URI (system KVM/QEMU daemon)."
  type        = string
  default     = "qemu:///system"
}

variable "project" {
  description = "Project name used in resource names."
  type        = string
  default     = "labrunner"
}

variable "environment" {
  description = "Environment name (part of resource names)."
  type        = string
  default     = "lab4"
}

variable "pool_path" {
  description = "Directory of the libvirt storage pool for VM disks."
  type        = string
  default     = "/var/lib/libvirt/images/labrunner"
}

variable "base_image_url" {
  description = "Ubuntu 24.04 LTS cloud image (qcow2) used as the read-only base disk."
  type        = string
  default     = "https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img"
}

variable "vcpu" {
  description = "Virtual CPUs of the VM (same profile as Azure Standard_B2als_v2)."
  type        = number
  default     = 2
}

variable "memory_mb" {
  description = "RAM of the VM in MiB."
  type        = number
  default     = 4096
}

variable "disk_size_gb" {
  description = "Size of the VM system disk."
  type        = number
  default     = 20
}

variable "subnet_cidr" {
  description = "Isolated NAT network of the VM (same addressing as the Azure subnet)."
  type        = string
  default     = "10.40.1.0/24"
}

variable "vm_ip" {
  description = "Fixed address of the VM in the NAT network (DHCP reservation)."
  type        = string
  default     = "10.40.1.10"
}

variable "admin_username" {
  description = "Linux admin user for SSH and Ansible."
  type        = string
  default     = "labrunner"
}

variable "ssh_public_key_path" {
  description = "Public SSH key installed by cloud-init."
  type        = string
  default     = "~/.ssh/labrunner_azure.pub"
}

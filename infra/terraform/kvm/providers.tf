terraform {
  required_version = ">= 1.9.0"

  required_providers {
    # 0.8.x: stable schema; 0.9.x is a breaking rewrite of the provider.
    libvirt = {
      source  = "dmacvicar/libvirt"
      version = "0.8.3"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }

  # Local state, never committed (.gitignore, ADR-0006).
}

# KVM/QEMU hypervisor managed by libvirt (inside WSL2 on the developer machine).
provider "libvirt" {
  uri = var.libvirt_uri
}

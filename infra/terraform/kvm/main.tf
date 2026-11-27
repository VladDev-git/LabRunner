locals {
  name     = "${var.project}-${var.environment}"
  key_path = replace(pathexpand(var.ssh_public_key_path), ".pub", "")
}

# ---------- Storage ----------
resource "libvirt_pool" "main" {
  name = local.name
  type = "dir"
  target {
    path = var.pool_path
  }
}

# Read-only base image downloaded once; the VM disk is a copy-on-write overlay on top of it.
resource "libvirt_volume" "base" {
  name   = "ubuntu-24.04-cloudimg.qcow2"
  pool   = libvirt_pool.main.name
  source = var.base_image_url
  format = "qcow2"
}

resource "libvirt_volume" "os" {
  name           = "${local.name}-os.qcow2"
  pool           = libvirt_pool.main.name
  base_volume_id = libvirt_volume.base.id
  size           = var.disk_size_gb * 1024 * 1024 * 1024
  format         = "qcow2"
}

# ---------- Network ----------
# Isolated NAT network: the VM reaches the Internet for packages, but is not reachable
# from the LAN — only from the host. Equivalent of the Azure VNet/subnet.
resource "libvirt_network" "app" {
  name      = "${local.name}-net"
  mode      = "nat"
  domain    = "${var.project}.lab"
  addresses = [var.subnet_cidr]
  autostart = true

  dhcp {
    enabled = true
  }

  dns {
    enabled    = true
    local_only = true
  }
}

# ---------- First-boot configuration (cloud-init) ----------
resource "libvirt_cloudinit_disk" "init" {
  name = "${local.name}-cloudinit.iso"
  pool = libvirt_pool.main.name
  user_data = templatefile("${path.module}/templates/cloud-init.yaml.tftpl", {
    hostname       = "${var.project}-vm"
    admin_username = var.admin_username
    ssh_public_key = trimspace(file(pathexpand(var.ssh_public_key_path)))
  })
}

# ---------- Virtual machine ----------
resource "libvirt_domain" "app" {
  name      = "${local.name}-vm"
  vcpu      = var.vcpu
  memory    = var.memory_mb
  cloudinit = libvirt_cloudinit_disk.init.id
  autostart = false

  cpu {
    mode = "host-passthrough"
  }

  network_interface {
    network_id     = libvirt_network.app.id
    hostname       = "${var.project}-vm"
    addresses      = [var.vm_ip]
    wait_for_lease = true
  }

  disk {
    volume_id = libvirt_volume.os.id
  }

  # The provider adds SPICE graphics by default, which this QEMU build does not support.
  # A local-only VNC display is enough (the server is managed over SSH anyway).
  graphics {
    type           = "vnc"
    listen_type    = "address"
    listen_address = "127.0.0.1"
    autoport       = true
  }

  # Serial console for `virsh console`.
  console {
    type        = "pty"
    target_type = "serial"
    target_port = "0"
  }
}

# ---------- Hand-off to Ansible ----------
resource "local_file" "ansible_inventory" {
  filename        = "${path.module}/../../ansible/inventory.ini"
  file_permission = "0644"
  content = templatefile("${path.module}/templates/inventory.ini.tftpl", {
    host     = var.vm_ip
    user     = var.admin_username
    key_path = local.key_path
    vm_name  = libvirt_domain.app.name
  })
}

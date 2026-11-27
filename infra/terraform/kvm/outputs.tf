output "vm_name" {
  description = "libvirt domain name of the VM."
  value       = libvirt_domain.app.name
}

output "vm_ip" {
  description = "IP address of the VM in the isolated NAT network."
  value       = libvirt_domain.app.network_interface[0].addresses[0]
}

output "network" {
  description = "libvirt NAT network and its address range."
  value       = "${libvirt_network.app.name} (${var.subnet_cidr})"
}

output "ssh_command" {
  description = "Ready-to-use SSH command (from WSL)."
  value       = "ssh -i ${local.key_path} ${var.admin_username}@${var.vm_ip}"
}

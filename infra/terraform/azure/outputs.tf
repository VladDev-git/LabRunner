output "resource_group" {
  description = "Resource group that contains the whole environment."
  value       = azurerm_resource_group.main.name
}

output "public_ip" {
  description = "Public IP address of the application VM."
  value       = azurerm_public_ip.vm.ip_address
}

output "private_ip" {
  description = "Private IP address of the VM inside the VNet."
  value       = azurerm_network_interface.vm.private_ip_address
}

output "ssh_allowed_from" {
  description = "Source CIDR allowed to connect over SSH."
  value       = local.ssh_source
}

output "ssh_command" {
  description = "Ready-to-use SSH command."
  value       = "ssh -i ${replace(pathexpand(var.ssh_public_key_path), ".pub", "")} ${var.admin_username}@${azurerm_public_ip.vm.ip_address}"
}

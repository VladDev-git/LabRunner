locals {
  name = "${var.project}-${var.environment}"

  tags = {
    project     = var.project
    environment = var.environment
    managed_by  = "terraform"
    course_lab  = "4"
  }

  # Least privilege for SSH: only the operator's current public IP, unless set explicitly.
  ssh_source = var.ssh_allowed_cidr != "" ? var.ssh_allowed_cidr : "${chomp(data.http.operator_ip.response_body)}/32"
}

data "http" "operator_ip" {
  url = "https://api.ipify.org"
}

# ---------- Resource group ----------
resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}

# ---------- Network ----------
resource "azurerm_virtual_network" "main" {
  name                = "vnet-${local.name}"
  address_space       = [var.vnet_cidr]
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags
}

resource "azurerm_subnet" "app" {
  name                 = "snet-app"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.subnet_cidr]
}

# Network security group: deny by default, allow only SSH (operator IP) and HTTP/HTTPS.
resource "azurerm_network_security_group" "app" {
  name                = "nsg-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags

  security_rule {
    name                       = "allow-ssh-operator"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = local.ssh_source
    destination_address_prefix = "*"
  }

  # Accepted risk (ADR-0006): HTTP/HTTPS of a public web service must be reachable from
  # the Internet. Only these two ports are open; SSH is limited to the operator IP.
  #trivy:ignore:AZU-0047
  security_rule {
    name                       = "allow-http-https"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_ranges    = ["80", "443"]
    source_address_prefix      = "Internet"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "deny-all-inbound"
    priority                   = 4096
    direction                  = "Inbound"
    access                     = "Deny"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "app" {
  subnet_id                 = azurerm_subnet.app.id
  network_security_group_id = azurerm_network_security_group.app.id
}

resource "azurerm_public_ip" "vm" {
  name                = "pip-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = local.tags
}

resource "azurerm_network_interface" "vm" {
  name                = "nic-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.tags

  ip_configuration {
    name                          = "primary"
    subnet_id                     = azurerm_subnet.app.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.vm.id
  }
}

# Defence in depth: the same NSG also filters traffic on the VM's network interface.
resource "azurerm_network_interface_security_group_association" "vm" {
  network_interface_id      = azurerm_network_interface.vm.id
  network_security_group_id = azurerm_network_security_group.app.id
}

# ---------- Virtual machine ----------
resource "azurerm_linux_virtual_machine" "app" {
  name                  = "vm-${local.name}"
  computer_name         = "${var.project}-vm"
  location              = azurerm_resource_group.main.location
  resource_group_name   = azurerm_resource_group.main.name
  size                  = var.vm_size
  admin_username        = var.admin_username
  network_interface_ids = [azurerm_network_interface.vm.id]
  tags                  = local.tags

  # Key-based SSH only; password login is disabled on the image.
  disable_password_authentication = true
  admin_ssh_key {
    username   = var.admin_username
    public_key = file(pathexpand(var.ssh_public_key_path))
  }

  os_disk {
    name                 = "osdisk-${local.name}"
    caching              = "ReadWrite"
    storage_account_type = "StandardSSD_LRS"
    disk_size_gb         = 30
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  # The VM gets updates through Ansible; Azure only patches the platform.
  patch_mode = "ImageDefault"
}

# ---------- Hand-off to Ansible ----------
# Terraform writes the Ansible inventory, so the IP never has to be copied by hand.
resource "local_file" "ansible_inventory" {
  filename        = "${path.module}/../../ansible/inventory.ini"
  file_permission = "0644"
  content = templatefile("${path.module}/templates/inventory.ini.tftpl", {
    host       = azurerm_public_ip.vm.ip_address
    user       = var.admin_username
    key_path   = replace(pathexpand(var.ssh_public_key_path), ".pub", "")
    vm_name    = azurerm_linux_virtual_machine.app.name
    private_ip = azurerm_network_interface.vm.private_ip_address
  })
}

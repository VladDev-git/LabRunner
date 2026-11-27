terraform {
  required_version = ">= 1.9.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.81"
    }
    http = {
      source  = "hashicorp/http"
      version = "~> 3.5"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }

  # State is kept locally and never committed (see .gitignore and ADR-0006).
  # Team setup: backend "azurerm" with a storage account + blob lease locking.
}

provider "azurerm" {
  features {
    resource_group {
      # The lab environment is disposable: allow destroy even if Azure added resources.
      prevent_deletion_if_contains_resources = false
    }
  }
  # Authentication comes from `az login` (Azure CLI); no secrets in code.
  subscription_id = var.subscription_id
}

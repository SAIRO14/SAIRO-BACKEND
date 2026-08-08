data "oci_identity_availability_domains" "available" {
  compartment_id = var.tenancy_ocid
}

resource "oci_identity_compartment" "sairo" {
  compartment_id = var.tenancy_ocid
  name           = "sairo"
  description    = "SAIRO contest deployment"
  enable_delete  = true
}

resource "oci_core_vcn" "sairo" {
  compartment_id = oci_identity_compartment.sairo.id
  cidr_blocks    = ["10.10.0.0/16"]
  display_name   = "sairo-vcn"
  dns_label      = "sairo"

  freeform_tags = {
    project = "sairo"
  }
}

resource "oci_core_internet_gateway" "sairo" {
  compartment_id = oci_identity_compartment.sairo.id
  vcn_id         = oci_core_vcn.sairo.id
  display_name   = "sairo-internet-gateway"
  enabled        = true
}

resource "oci_core_route_table" "public" {
  compartment_id = oci_identity_compartment.sairo.id
  vcn_id         = oci_core_vcn.sairo.id
  display_name   = "sairo-public-routes"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.sairo.id
  }
}

resource "oci_core_security_list" "public" {
  compartment_id = oci_identity_compartment.sairo.id
  vcn_id         = oci_core_vcn.sairo.id
  display_name   = "sairo-subnet-security-list"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }
}

resource "oci_core_subnet" "public" {
  compartment_id             = oci_identity_compartment.sairo.id
  vcn_id                     = oci_core_vcn.sairo.id
  cidr_block                 = "10.10.1.0/24"
  display_name               = "sairo-public-subnet"
  dns_label                  = "public"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.public.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_network_security_group" "app" {
  compartment_id = oci_identity_compartment.sairo.id
  vcn_id         = oci_core_vcn.sairo.id
  display_name   = "sairo-app-nsg"
}

resource "oci_core_network_security_group_security_rule" "app_ssh" {
  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.admin_cidr
  source_type               = "CIDR_BLOCK"
  description               = "SSH from the administrator IP only"

  tcp_options {
    destination_port_range {
      min = 22
      max = 22
    }
  }
}

resource "oci_core_network_security_group_security_rule" "app_http" {
  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "Public HTTP"

  tcp_options {
    destination_port_range {
      min = 80
      max = 80
    }
  }
}

resource "oci_core_network_security_group_security_rule" "app_https" {
  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "Public HTTPS"

  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "app_http3" {
  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "INGRESS"
  protocol                  = "17"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "Public HTTP/3"

  udp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "app_egress" {
  network_security_group_id = oci_core_network_security_group.app.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
  description               = "Application outbound traffic"
}

resource "oci_core_network_security_group" "db" {
  compartment_id = oci_identity_compartment.sairo.id
  vcn_id         = oci_core_vcn.sairo.id
  display_name   = "sairo-db-nsg"
}

resource "oci_core_network_security_group_security_rule" "db_ssh" {
  network_security_group_id = oci_core_network_security_group.db.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.admin_cidr
  source_type               = "CIDR_BLOCK"
  description               = "SSH from the administrator IP only"

  tcp_options {
    destination_port_range {
      min = 22
      max = 22
    }
  }
}

resource "oci_core_network_security_group_security_rule" "db_postgres" {
  network_security_group_id = oci_core_network_security_group.db.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = oci_core_network_security_group.app.id
  source_type               = "NETWORK_SECURITY_GROUP"
  description               = "PostgreSQL from the app VM only"

  tcp_options {
    destination_port_range {
      min = 5432
      max = 5432
    }
  }
}

resource "oci_core_network_security_group_security_rule" "db_egress" {
  network_security_group_id = oci_core_network_security_group.db.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
  description               = "Database VM outbound traffic"
}

data "oci_core_images" "ubuntu" {
  compartment_id           = var.tenancy_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.E2.1.Micro"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

locals {
  availability_domain = data.oci_identity_availability_domains.available.availability_domains[0].name
  ubuntu_image_id     = data.oci_core_images.ubuntu.images[0].id
  common_metadata = {
    ssh_authorized_keys = trimspace(file(var.ssh_public_key_path))
    user_data           = base64encode(file("${path.module}/cloud-init.yaml"))
  }
  common_tags = {
    project = "sairo"
    purpose = "contest"
  }
}

resource "oci_core_instance" "app" {
  availability_domain = local.availability_domain
  compartment_id      = oci_identity_compartment.sairo.id
  display_name        = "sairo-app"
  shape               = "VM.Standard.E2.1.Micro"

  create_vnic_details {
    assign_public_ip = true
    display_name     = "sairo-app-vnic"
    hostname_label   = "app"
    nsg_ids          = [oci_core_network_security_group.app.id]
    subnet_id        = oci_core_subnet.public.id
  }

  source_details {
    source_id               = local.ubuntu_image_id
    source_type             = "image"
    boot_volume_size_in_gbs = 50
    boot_volume_vpus_per_gb = 10
  }

  metadata      = local.common_metadata
  freeform_tags = local.common_tags
}

resource "oci_core_instance" "db" {
  availability_domain = local.availability_domain
  compartment_id      = oci_identity_compartment.sairo.id
  display_name        = "sairo-db"
  shape               = "VM.Standard.E2.1.Micro"

  create_vnic_details {
    assign_public_ip = true
    display_name     = "sairo-db-vnic"
    hostname_label   = "db"
    nsg_ids          = [oci_core_network_security_group.db.id]
    subnet_id        = oci_core_subnet.public.id
  }

  source_details {
    source_id               = local.ubuntu_image_id
    source_type             = "image"
    boot_volume_size_in_gbs = 50
    boot_volume_vpus_per_gb = 10
  }

  metadata      = local.common_metadata
  freeform_tags = local.common_tags
}

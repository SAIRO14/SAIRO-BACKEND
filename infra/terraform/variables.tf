variable "tenancy_ocid" {
  description = "OCI tenancy OCID"
  type        = string
}

variable "region" {
  description = "Always Free 리소스를 만들 홈 리전"
  type        = string
  default     = "ap-chuncheon-1"
}

variable "oci_profile" {
  description = "로컬 OCI CLI 설정 프로필"
  type        = string
  default     = "DEFAULT"
}

variable "admin_cidr" {
  description = "SSH를 허용할 운영자 공인 IP의 /32 CIDR"
  type        = string

  validation {
    condition     = can(cidrnetmask(var.admin_cidr)) && split("/", var.admin_cidr)[1] == "32"
    error_message = "admin_cidr는 단일 IPv4 주소를 나타내는 /32 CIDR이어야 합니다."
  }
}

variable "ssh_public_key_path" {
  description = "Compute 인스턴스에 등록할 SSH 공개키 경로"
  type        = string
}

variable "ubuntu_image_ocid" {
  description = "두 VM에 사용할 고정 Ubuntu 24.04 이미지 OCID"
  type        = string

  validation {
    condition     = startswith(var.ubuntu_image_ocid, "ocid1.image.")
    error_message = "ubuntu_image_ocid는 OCI 이미지 OCID여야 합니다."
  }
}

output "app_public_ip" {
  description = "Caddy에 접속할 app VM 공인 IP"
  value       = oci_core_instance.app.public_ip
}

output "app_private_ip" {
  description = "DB NSG가 신뢰하는 app VM 사설 IP"
  value       = oci_core_instance.app.private_ip
}

output "db_public_ip" {
  description = "초기 SSH 작업에 사용할 DB VM 공인 IP"
  value       = oci_core_instance.db.public_ip
}

output "db_private_ip" {
  description = "애플리케이션 JDBC URL에 사용할 DB VM 사설 IP"
  value       = oci_core_instance.db.private_ip
}

output "sairo_compartment_id" {
  value = oci_identity_compartment.sairo.id
}

# SAIRO OCI 인프라

Terraform은 Always Free `VM.Standard.E2.1.Micro` 두 대와 각 50GB 부트 볼륨을 만든다.
SAIRO 전용 컴파트먼트·VCN·public subnet·NSG도 함께 관리한다.

개인별 `terraform.tfvars`와 Terraform 상태는 Git에 커밋하지 않는다.

```bash
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -out=sairo.tfplan
terraform apply sairo.tfplan
```

보안 규칙은 다음과 같다.

- app VM: 운영자 IP에서 SSH, 인터넷에서 TCP 80/443과 UDP 443
- db VM: 운영자 IP에서 SSH, app NSG에서 PostgreSQL 5432
- 두 VM 모두 필요한 패키지 설치와 외부 API 호출을 위해 outbound를 허용

두 VM은 공모전 운영 편의를 위해 공인 IP를 갖지만 DB 포트는 인터넷에서 접근할 수 없다.

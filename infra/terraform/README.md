# SAIRO OCI 인프라

Terraform은 Always Free `VM.Standard.E2.1.Micro` 두 대와 각 50GB 부트 볼륨을 만든다.
SAIRO 전용 컴파트먼트·VCN·public subnet·NSG도 함께 관리한다.
구성 선택과 DB 공인 IP의 보안 트레이드오프는
[ADR 0017](../../docs/decisions/0017-oci-always-free-deployment-topology.md)에 기록돼 있다.

개인별 `terraform.tfvars`와 Terraform 상태는 Git에 커밋하지 않는다.
`ubuntu_image_ocid`에는 검증한 Ubuntu 24.04 이미지 OCID를 명시한다. 최신 이미지를 자동 선택하지
않으므로 OS 이미지를 바꿀 때는 release note와 `terraform plan`의 부트 볼륨 변경 여부를 먼저
검토한다.

```bash
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -out=sairo.tfplan
terraform apply sairo.tfplan
```

DB 인스턴스에는 `prevent_destroy`가 설정되어 있다. DB를 의도적으로 교체해야 한다면 먼저
`pg_dump`를 외부 저장소로 복사하고, 코드에서 보호를 제거하는 별도 변경을 리뷰받는다.

보안 규칙은 다음과 같다.

- app VM: 운영자 IP에서 SSH, 인터넷에서 TCP 80/443과 UDP 443
- db VM: 운영자 IP에서 SSH, app NSG에서 PostgreSQL 5432
- 두 VM 모두 필요한 패키지 설치와 외부 API 호출을 위해 outbound를 허용

두 VM은 공모전 운영 편의를 위해 공인 IP를 갖지만 DB 포트는 인터넷에서 접근할 수 없다.

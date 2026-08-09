# 0018. OCI Always Free 배포를 Terraform과 분리 VM으로 구성한다

- **상태:** 수용됨
- **날짜:** 2026-08-09
- **관련:** 이슈 #36, PR #71

## 맥락

공모전 제출과 시연에 필요한 서버를 추가 비용 없이 운영해야 한다. 애플리케이션과 PostgreSQL은
서로 다른 메모리 사용 특성과 장애 범위를 가지므로 1GB VM 한 대에 함께 올리면 한 컨테이너의
메모리 압박이 전체 서비스를 중단시킬 수 있다. 동일한 환경을 다시 만들고 보안 규칙을 코드로
검토하려면 수동 콘솔 작업 대신 인프라 정의도 필요하다.

DB VM도 최초 구성과 운영 중에 패키지 업데이트, 컨테이너 이미지 다운로드 등 outbound 인터넷
연결이 필요하다. [OCI 공식 문서](https://docs.oracle.com/en-us/iaas/database-tools/doc/troubleshooting-connections.html)는
Free Tier 계정에서 NAT Gateway와 Service Gateway 생성이 제한된다고 안내한다. 이 프로젝트는
계정 상태에 따라 과금될 수 있는 네트워크 리소스에 의존하지 않기로 했다.

DB에는 초기 기준 데이터뿐 아니라 사용자가 만든 코스와 공유 링크가 쌓인다. 그러나 공모전 범위에서
별도 관리형 DB나 자동 원격 백업 체계까지 운영하기는 어렵다. 적어도 Terraform의 우발적 삭제와
단기 운영 실수에 대비할 복구 수단은 필요하다.

## 결정

OCI Always Free `VM.Standard.E2.1.Micro` 두 대를 app VM과 DB VM으로 분리하고, 컴파트먼트·VCN·
서브넷·NSG·인스턴스를 Terraform으로 관리한다.

- 두 VM은 outbound 인터넷 연결을 위해 public subnet에 두고 공인 IP를 할당한다.
- DB의 PostgreSQL 5432 ingress는 app NSG만 허용하고 Docker도 DB 사설 IP에만 bind한다.
- DB SSH ingress는 운영자의 단일 `/32` CIDR로 제한한다. 공용 security list에는 ingress를 두지 않는다.
- OS 이미지 OCID를 입력 변수로 고정하고 DB 인스턴스에는 `prevent_destroy`를 적용한다.
- DB VM에서 매일 custom-format `pg_dump`를 생성하고 7일간 보관한다. dump는 `pg_restore --list`로
  검증한 뒤 확정하며, 최신 파일은 운영자가 주기적으로 별도 장비나 Object Storage로 복사한다.

## 고려한 대안

| 대안 | 장점 | 버린 이유 |
|---|---|---|
| app과 DB를 VM 한 대에서 실행 | 구성과 네트워크가 단순하다 | 1GB 메모리를 두 서비스가 경쟁하고 VM 한 대의 장애가 전체 서비스 장애가 된다 |
| DB를 private subnet에 두고 NAT Gateway 사용 | DB에 공인 IP가 없어 공격 표면이 작다 | Free Tier 계정의 NAT 사용 제한과 과금 가능성에 의존하며 무료 운영 조건을 보장하기 어렵다 |
| app VM을 NAT 인스턴스 겸 bastion으로 사용 | 추가 관리형 NAT 없이 DB 공인 IP를 없앨 수 있다 | IP forwarding·방화벽·장애 복구를 직접 운영해야 하고 app VM이 네트워크 단일 실패점이 된다 |
| 관리형 DB와 자동 원격 백업 사용 | 데이터 내구성과 복구 자동화가 좋아진다 | 공모전 제출용 무료 운영 범위와 초기 구축 시간에 맞지 않는다 |

## 결과

- 좋아지는 것:
  - app과 DB의 메모리·장애 범위가 분리되고 Terraform plan으로 인프라 변경을 검토할 수 있다.
  - PostgreSQL 포트는 공인 IP가 있어도 인터넷 전체에 열리지 않는다.
  - 이미지 자동 변경과 DB 인스턴스 우발 삭제를 막고 최근 dump로 운영 실수를 복구할 수 있다.
- 나빠지거나 감수하는 것:
  - DB VM에 공인 IP가 있으므로 NSG와 운영자 CIDR을 계속 정확하게 관리해야 한다.
  - 같은 부트 볼륨의 dump는 VM이나 볼륨 자체가 손실되면 함께 사라진다.
  - 원격 백업 복사는 자동화되지 않아 운영자가 주기적으로 수행해야 한다.
- 이 결정에 뒤따라야 하는 작업:
  - Terraform apply 전 plan에서 인스턴스 교체와 네트워크 ingress 변경을 확인한다.
  - 공모전 운영 중 최신 dump를 주기적으로 외부에 복사하고 복원 가능 여부를 확인한다.

## 다시 검토해야 할 시점

- 공모전 이후 장기 운영하거나 복구해야 할 사용자 데이터의 가치가 커질 때
- 무료 범위를 벗어난 네트워크·관리형 DB 비용을 허용할 때
- 운영자 IP 변경이 잦아 `/32` SSH 허용 방식의 유지가 어려워질 때

# OCI Always Free 배포

공모전 제출용 저비용 구성을 전제로 한다. 춘천 홈 리전은 Always Free Ampere A1 대상에서
제외되므로 `VM.Standard.E2.1.Micro` 두 대를 사용한다.

```text
Internet
   |
   | 80/443
   v
app VM (Caddy + Spring Boot, 1GB)
   |
   | private VCN: 5432
   v
db VM (PostgreSQL 16 + pgvector, 1GB)
```

## VM 역할

- app VM: `compose.app.yaml`을 실행한다. 공인 IP를 갖고 80/443만 공개한다.
- db VM: `compose.db.yaml`을 실행한다. 5432는 app VM의 사설 IP에서만 허용한다.
- E2.1.Micro는 AMD64이므로 앱 Compose 파일도 `linux/amd64`를 명시한다.
- SSH 22번은 가능한 한 운영자 IP로 제한한다.
- Spring Boot의 8080은 호스트에 게시하지 않고 Caddy 컨테이너에서만 접근한다.

## 환경 파일

예시 파일을 복사한 뒤 실제 값을 채운다. 실제 환경 파일은 Git에서 제외된다.

```bash
cp deploy/.env.app.example deploy/.env.app
cp deploy/.env.db.example deploy/.env.db
```

두 파일의 DB 이름·사용자·비밀번호는 서로 같아야 한다. `DB_BIND_ADDRESS`와 JDBC URL에는
`terraform output -raw db_private_ip`로 확인한 DB VM의 VCN 사설 IP를 넣는다. 예시의
`10.10.1.10`은 플레이스홀더이며 그대로 사용하지 않는다.

도메인이 없으면 `SERVER_ADDRESS=:80`으로 HTTP만 연다. 도메인의 A 레코드를 app VM 공인 IP로
연결한 뒤 `SERVER_ADDRESS=api.example.com`처럼 바꾸면 Caddy가 인증서를 자동 발급한다.

## `/opt/sairo` 부트스트랩

Compose project는 실행 위치와 관계없이 `sairo`로 고정되어 있다. 두 VM에서 각각 운영 파일을
`/opt/sairo`에 설치한다. app VM에는 app 파일만, DB VM에는 DB 파일만 둔다.

app VM:

```bash
sudo install -d -o ubuntu -g ubuntu -m 0750 /opt/sairo
install -m 0644 deploy/compose.app.yaml deploy/Caddyfile /opt/sairo/
install -m 0600 deploy/.env.app /opt/sairo/.env.app
```

DB VM:

```bash
sudo install -d -o ubuntu -g ubuntu -m 0750 /opt/sairo
install -m 0644 deploy/compose.db.yaml /opt/sairo/compose.db.yaml
install -m 0755 deploy/backup-postgres.sh /opt/sairo/backup-postgres.sh
install -m 0600 deploy/.env.db /opt/sairo/.env.db
install -d -m 0700 /opt/sairo/backups
```

1GB app VM에서는 이미지를 빌드하지 않는다. 최초 한 번은 개발 장비에서 AMD64 이미지를 만들어
app VM으로 옮린다. CD가 병합된 뒤에는 같은 전송과 재기동을 GitHub Actions가 수행한다.

개발 장비:

```bash
docker build --platform linux/amd64 -t sairo-backend:local .
docker save sairo-backend:local | gzip -1 > /tmp/sairo-backend.tar.gz
scp /tmp/sairo-backend.tar.gz ubuntu@APP_VM:/tmp/
```

DB VM과 app VM에서 각각 실행한다.

```bash
# DB VM
cd /opt/sairo
docker compose --env-file .env.db -f compose.db.yaml up -d

# app VM: 먼저 전송한 이미지를 적재한다.
gzip -dc /tmp/sairo-backend.tar.gz | docker load
cd /opt/sairo
docker compose --env-file .env.app -f compose.app.yaml up -d --no-build
```

애플리케이션 기동 시 Flyway가 스키마를 생성한다. 컨테이너 상태는 다음 명령으로 확인한다.

```bash
docker compose --env-file /opt/sairo/.env.app -f /opt/sairo/compose.app.yaml ps
curl https://api.example.com/actuator/health
```

## 초기 데이터

Flyway는 테이블만 만들며 `photos`와 `spots` 기준 데이터는 넣지 않는다. 추천 기능을 사용하려면
`data/deduped_results.json`과 `data/spots_phase1_checkpoint.json`을 DB VM으로 안전하게 옮긴 뒤
`scripts/import_data.py`를 실행한다. 스크립트는 `DB_PASSWORD`를 필수로 받고, `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USER`로 대상 DB를 지정한다. 데이터 경로는 `--data-dir`로 전달하며, 두 파일을
따로 지정해야 하면 `--photos`와 `--spots`를 사용한다.

```bash
sudo apt-get install -y python3-psycopg2
DB_HOST=10.10.1.10 DB_PORT=5432 DB_NAME=sairo DB_USER=sairo \
  DB_PASSWORD='replace-with-db-password' \
  python3 scripts/import_data.py --data-dir /path/to/data
```

`DB_HOST`도 예시값이 아니라 `terraform output -raw db_private_ip`의 결과로 바꾼다. PostgreSQL은
DB VM 사설 IP에만 bind되므로 `127.0.0.1`로 접속하지 않는다.

환경 변수 `SAIRO_DATA_DIR`, `PHOTOS_DATA_PATH`, `SPOTS_DATA_PATH`도 같은 경로 옵션의 기본값으로
사용할 수 있다. 비밀번호와 원본 JSON은 저장소에 커밋하지 않는다.

## DB 백업

DB VM에서 백업 timer를 설치한다. 매일 UTC 03시경 custom-format `pg_dump`를 만들고 기본 7일간
보관한다.

```bash
sudo install -m 0644 deploy/systemd/sairo-db-backup.service /etc/systemd/system/
sudo install -m 0644 deploy/systemd/sairo-db-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sairo-db-backup.timer
sudo systemctl start sairo-db-backup.service
sudo systemctl status sairo-db-backup.service
```

백업은 `/opt/sairo/backups/`에 `0600` 권한으로 저장된다. 같은 부트 볼륨의 백업만으로는 VM이나
부트 볼륨 손실을 복구할 수 없으므로, 공모전 운영 중에는 최신 dump를 주기적으로 별도 장비나
Object Storage로 복사한다. 실제 복구 가능 여부는 다음처럼 dump 목록을 읽어 확인한다.

```bash
pg_restore --list /path/to/sairo-YYYYMMDDTHHMMSSZ.dump
```

## 메모리 기준

- app: 컨테이너 640MB, JVM heap 최대 384MB, Serial GC
- Caddy: 컨테이너 96MB
- PostgreSQL: 컨테이너 700MB, shared buffers 128MB, 최대 연결 20개
- 애플리케이션 DB 풀: 최대 5개

모두 E2.1.Micro의 1GB 한도에 맞춘 시작값이다. 공모전 부하 테스트 결과를 보기 전에는 값을
늘리지 않는다.

## CD

`.github/workflows/cd.yml`은 전체 Gradle build와 test를 통과한 뒤 AMD64 애플리케이션 이미지를
만든다. PR에서는 여기까지 실행해 Dockerfile을 검증한다. `main`에 반영되면 GitHub-hosted runner가
이미지·배포 스크립트·Compose·Caddy를 checksum과 함께 artifact로 올리고, app VM의 self-hosted
runner가 검증한 bundle만 내려받아 배포한다. 운영 runner에서는 저장소를 checkout하지 않는다.
1GB VM에서는 Gradle이나 Docker 이미지 빌드를 실행하지 않는다.

app VM의 runner는 GitHub로 outbound 연결만 만들기 때문에 GitHub Actions IP를 위해 SSH 22번을
추가로 공개할 필요가 없다. 이 저장소는 private이며 배포 job은 PR에서 실행되지 않고 `main`에서
발생한 push나 수동 실행에만 동작한다. 대기 중 더 최신 `main` commit이 생기면 오래된 job은 배포를
건너뛴다. bundle은 실패 조사와 수동 복구를 위해 7일간 보관한다.

### 최초 runner 등록

GitHub 저장소의 `Settings > Actions > Runners > New self-hosted runner`에서 한 시간 동안 유효한
등록 토큰을 발급한다. app VM에서 토큰을 셸 기록에 남기지 않고 입력한 뒤 설치 스크립트를 실행한다.

```bash
read -rsp "Runner registration token: " GITHUB_RUNNER_TOKEN
export GITHUB_RUNNER_TOKEN
./deploy/install-github-runner.sh
unset GITHUB_RUNNER_TOKEN
```

설치 스크립트는 `actions/runner` v2.336.0 Linux X64 archive의 SHA-256을 검증하고,
`sairo-app` runner를 `sairo-deploy` label로 systemd 서비스에 등록한다. runner를 교체할 때는 GitHub
설정에서 기존 runner를 제거한 뒤 `/opt/actions-runner`를 정리하고 다시 등록한다.

GitHub 저장소에는 다음 Actions variable을 추가한다. 실제 DB 비밀번호와 TourAPI 키는 계속 app
VM의 `/opt/sairo/.env.app`에만 둔다.

| 이름 | 값 예시 |
|---|---|
| `DEPLOY_HEALTH_URL` | `https://api.example.com/actuator/health` |

### 배포와 복구

배포 job은 image checksum을 검증하고 `/opt/sairo`의 Compose/Caddy 설정을 갱신한 뒤 backend가
Docker health check를 통과할 때까지 기다린다. 그 다음 Caddy를 재생성하고 공개 health URL까지
응답 본문의 `status: UP`을 확인한다. 어느 단계든 실패하거나 종료 신호를 받으면 직전
Compose/Caddy 파일과 실행 이미지를 다시 올리고 rollback health check까지 수행한다. 성공한 이미지
tag는 `/opt/sairo/.last-good-image`에도 기록해 현재 컨테이너 조회가 실패할 때 사용한다.

production 환경에 승인자를 추가하려면 GitHub `Settings > Environments > production`에서 보호
규칙을 설정한다. 현재 private 저장소의 GitHub Free 플랜에서는 required reviewer와 branch
protection을 사용할 수 없으므로 production의 허용 branch를 `main`으로 제한하고, 저장소 write/admin
권한은 꼭 필요한 사용자에게만 부여한다. 같은 환경의 배포는 동시에 하나만 실행되며 진행 중인
배포를 취소하지 않는다.

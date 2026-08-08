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
DB VM의 VCN 사설 IP를 넣는다.

도메인이 없으면 `SERVER_ADDRESS=:80`으로 HTTP만 연다. 도메인의 A 레코드를 app VM 공인 IP로
연결한 뒤 `SERVER_ADDRESS=api.example.com`처럼 바꾸면 Caddy가 인증서를 자동 발급한다.

## 실행

DB VM에서 저장소를 받은 뒤:

```bash
docker compose --env-file deploy/.env.db -f deploy/compose.db.yaml up -d
```

app VM에서 저장소를 받은 뒤:

```bash
docker compose --env-file deploy/.env.app -f deploy/compose.app.yaml up -d --build
```

1GB VM에서 Gradle 이미지 빌드를 실행하면 메모리가 부족할 수 있다. 실제 배포에서는 CI나 개발
장비에서 `linux/amd64` 이미지를 빌드해 레지스트리에 올린 뒤 `APP_IMAGE`를 그 주소로 설정하고
`--no-build`로 실행한다.

애플리케이션 기동 시 Flyway가 스키마를 생성한다. 컨테이너 상태는 다음 명령으로 확인한다.

```bash
docker compose --env-file deploy/.env.app -f deploy/compose.app.yaml ps
curl http://localhost/actuator/health
```

## 초기 데이터

Flyway는 테이블만 만들며 `photos`와 `spots` 기준 데이터는 넣지 않는다. 추천 기능을 사용하려면
`data/deduped_results.json`과 `data/spots_phase1_checkpoint.json`을 DB VM으로 안전하게 옮긴 뒤
`scripts/import_data.py`를 실행한다. 스크립트는 `DB_PASSWORD`를 필수로 받고, `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USER`로 대상 DB를 지정한다. 데이터 경로는 `--data-dir`로 전달하며, 두 파일을
따로 지정해야 하면 `--photos`와 `--spots`를 사용한다.

```bash
sudo apt-get install -y python3-psycopg2
DB_HOST=127.0.0.1 DB_PORT=5432 DB_NAME=sairo DB_USER=sairo \
  DB_PASSWORD='replace-with-db-password' \
  python3 scripts/import_data.py --data-dir /path/to/data
```

환경 변수 `SAIRO_DATA_DIR`, `PHOTOS_DATA_PATH`, `SPOTS_DATA_PATH`도 같은 경로 옵션의 기본값으로
사용할 수 있다. 비밀번호와 원본 JSON은 저장소에 커밋하지 않는다.

## 메모리 기준

- app: 컨테이너 640MB, JVM heap 최대 384MB, Serial GC
- Caddy: 컨테이너 96MB
- PostgreSQL: 컨테이너 700MB, shared buffers 128MB, 최대 연결 20개
- 애플리케이션 DB 풀: 최대 5개

모두 E2.1.Micro의 1GB 한도에 맞춘 시작값이다. 공모전 부하 테스트 결과를 보기 전에는 값을
늘리지 않는다.

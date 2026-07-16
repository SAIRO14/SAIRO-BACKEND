"""
사이로 데이터 임포트 스크립트
- deduped_results.json       → photos 테이블
- spots_phase1_checkpoint.json → spots 테이블

실행 전 필요:
  pip install psycopg2-binary pgvector
  Docker sairo-postgres 컨테이너 구동 중이어야 함

실행:
  python scripts/import_data.py
"""

import json
import sys
import os
import psycopg2
from pgvector.psycopg2 import register_vector

DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "dbname": "sairo",
    "user": "postgres",
    "password": "sairo1234",
}

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)

PHOTOS_FILE = os.path.join(PROJECT_DIR, "deduped_results.json")
SPOTS_FILE  = os.path.join(PROJECT_DIR, "spots_phase1_checkpoint.json")


def connect():
    conn = psycopg2.connect(**DB_CONFIG)
    register_vector(conn)
    return conn


def import_photos(conn, path):
    print(f"[photos] 파일 로드: {path}")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    rows = []
    skipped = 0
    for item in data:
        embedding = item.get("embedding")
        if not embedding or len(embedding) != 512:
            skipped += 1
            continue
        rows.append((
            item["id"],
            item.get("title", ""),
            item.get("image_url", ""),
            item.get("location"),
            item.get("keywords"),
            embedding,
        ))

    with conn.cursor() as cur:
        cur.executemany(
            """
            INSERT INTO photos (id, title, image_url, location, keywords, embedding)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO NOTHING
            """,
            rows,
        )
    conn.commit()

    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM photos")
        count = cur.fetchone()[0]

    print(f"[photos] 삽입 대상: {len(rows)}건 | skip(임베딩 이상): {skipped}건")
    print(f"[photos] DB 실제 건수: {count}건")
    assert count == len(rows), f"[photos] 예상 {len(rows)}건 != 실제 {count}건"
    print(f"[photos] 검증 통과")


def import_spots(conn, path):
    print(f"\n[spots] 파일 로드: {path}")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    # 리스트 또는 dict(contentid 키) 두 가지 형태 모두 처리
    if isinstance(data, dict):
        items = list(data.values())
    else:
        items = data

    rows = []
    skipped = 0
    for item in items:
        spot_id = item.get("contentid")
        if not spot_id:
            skipped += 1
            continue

        # mapx = 경도(lng), mapy = 위도(lat)
        try:
            lat = float(item["mapy"]) if item.get("mapy") else None
            lng = float(item["mapx"]) if item.get("mapx") else None
        except (ValueError, TypeError):
            lat, lng = None, None

        rows.append((
            str(spot_id),
            item.get("title"),
            item.get("addr1", "").split()[0] if item.get("addr1") else None,  # 지역명 추출
            lat,
            lng,
            item.get("firstimage"),
            item.get("usetime"),
            item.get("restdate"),
            item.get("parking"),
            item.get("infocenter"),
            item.get("cat1"),
            item.get("cat2"),
            item.get("cat3"),
        ))

    with conn.cursor() as cur:
        cur.executemany(
            """
            INSERT INTO spots (spot_id, name, region_name, lat, lng, image_url,
                               operating_hours, closed_days, parking, contact,
                               cat1, cat2, cat3)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (spot_id) DO NOTHING
            """,
            rows,
        )
    conn.commit()

    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM spots")
        count = cur.fetchone()[0]

    print(f"[spots] 삽입 대상: {len(rows)}건 | skip(id 없음): {skipped}건")
    print(f"[spots] DB 실제 건수: {count}건")
    assert count == len(rows), f"[spots] 예상 {len(rows)}건 != 실제 {count}건"
    print(f"[spots] 검증 통과")


def main():
    missing = [p for p in [PHOTOS_FILE, SPOTS_FILE] if not os.path.exists(p)]
    if missing:
        print("[오류] 아래 파일이 프로젝트 루트에 없습니다:")
        for p in missing:
            print(f"  - {os.path.basename(p)}")
        sys.exit(1)

    print("DB 연결 중...")
    try:
        conn = connect()
    except Exception as e:
        print(f"[오류] DB 연결 실패: {e}")
        print("Docker sairo-postgres 컨테이너가 실행 중인지 확인하세요.")
        sys.exit(1)

    try:
        import_photos(conn, PHOTOS_FILE)
        import_spots(conn, SPOTS_FILE)
        print("\n모든 임포트 완료 및 검증 통과")
    finally:
        conn.close()


if __name__ == "__main__":
    main()

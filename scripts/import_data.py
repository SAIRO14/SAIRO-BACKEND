#!/usr/bin/env python3
"""One-time data import: deduped_results.json (photos) + spots_phase1_checkpoint.json (spots)"""

import json
import re
import sys
import psycopg2

DB_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "dbname": "sairo",
    "user": "postgres",
    "password": "sairo1234",
}

# spots addr1 first-word → canonical form matching photo location format
REGION_NORM = {
    "제주특별자치도": "제주도",
    "전북특별자치도": "전라북도",
    "강원특별자치도": "강원도",
}

# addr1 first-word → 광역시도 축약명 (area_name 생성에 사용)
PROVINCE_ABBR = {
    "서울특별시": "서울",
    "부산광역시": "부산",
    "대구광역시": "대구",
    "인천광역시": "인천",
    "광주광역시": "광주",
    "대전광역시": "대전",
    "울산광역시": "울산",
    "세종특별자치시": "세종",
    "경기도": "경기",
    "강원도": "강원",
    "강원특별자치도": "강원",
    "충청북도": "충북",
    "충청남도": "충남",
    "전라북도": "전북",
    "전북특별자치도": "전북",
    "전라남도": "전남",
    "경상북도": "경북",
    "경상남도": "경남",
    "제주도": "제주",
    "제주특별자치도": "제주",
}

PROJECT_ROOT = "/Users/limchaeryun/Desktop/sairo-backend"


def to_vector_literal(floats: list) -> str:
    return "[" + ",".join(str(f) for f in floats) + "]"


def import_photos(cur, data: list) -> int:
    inserted = 0
    for item in data:
        embedding = item.get("embedding")
        if not embedding:
            continue
        cur.execute(
            """
            INSERT INTO photos (id, title, image_url, location, keywords, embedding)
            VALUES (%s, %s, %s, %s, %s, %s::vector)
            ON CONFLICT (id) DO NOTHING
            """,
            (
                item["id"],
                item.get("title", ""),
                item.get("image_url", ""),
                item.get("location"),
                item.get("keywords"),
                to_vector_literal(embedding),
            ),
        )
        inserted += cur.rowcount
    return inserted


def normalize_region(addr1: str) -> str:
    first_word = addr1.split()[0] if addr1 else ""
    return REGION_NORM.get(first_word, first_word)


def build_area_name(addr1: str) -> str | None:
    """addr1에서 시군구 단위 지역권을 생성한다. 예: "충청북도 보은군 ..." → "충북 보은"."""
    if not addr1:
        return None
    parts = addr1.strip().split()
    if len(parts) < 2:
        return None
    province = PROVINCE_ABBR.get(parts[0])
    if province is None:
        return None
    # 세종특별자치시는 시군구 계층이 없어 읍면동 단위로 떨어진다. 광역시도명만 반환한다.
    if province == "세종":
        return "세종"
    sigungu = parts[1]
    if len(sigungu) > 2 and sigungu[-1] in "시군구":
        sigungu = sigungu[:-1]
    return f"{province} {sigungu}" if sigungu else province


def import_spots(cur, data: list) -> int:
    inserted = 0
    for item in data:
        addr1 = item.get("addr1", "")
        region_name = normalize_region(addr1)
        try:
            lat = float(item["mapy"]) if item.get("mapy") else None
            lng = float(item["mapx"]) if item.get("mapx") else None
        except (ValueError, TypeError):
            lat, lng = None, None

        cur.execute(
            """
            INSERT INTO spots (
                spot_id, name, region_name, area_name, lat, lng, image_url,
                operating_hours, closed_days, parking, contact,
                cat1, cat2, cat3
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (spot_id) DO UPDATE SET area_name = EXCLUDED.area_name
            """,
            (
                item["contentid"],
                item.get("title", ""),
                region_name,
                build_area_name(addr1),
                lat,
                lng,
                item.get("firstimage") or None,
                item.get("usetime") or None,
                item.get("restdate") or None,
                item.get("parking") or None,
                item.get("infocenter") or item.get("tel") or None,
                item.get("cat1") or None,
                item.get("cat2") or None,
                item.get("cat3") or None,
            ),
        )
        inserted += cur.rowcount
    return inserted


def main():
    print("Loading JSON files...")
    with open(f"{PROJECT_ROOT}/deduped_results.json") as f:
        photos_data = json.load(f)
    with open(f"{PROJECT_ROOT}/spots_phase1_checkpoint.json") as f:
        spots_data = json.load(f)

    print(f"  photos: {len(photos_data)} items")
    print(f"  spots:  {len(spots_data)} items")

    conn = psycopg2.connect(**DB_CONFIG)
    conn.autocommit = False
    cur = conn.cursor()

    try:
        print("\nImporting photos...")
        photo_count = import_photos(cur, photos_data)
        print(f"  inserted: {photo_count}")

        print("Importing spots...")
        spot_count = import_spots(cur, spots_data)
        print(f"  inserted: {spot_count}")

        conn.commit()

        cur.execute("SELECT COUNT(*) FROM photos")
        print(f"\n[verify] photos: {cur.fetchone()[0]}")
        cur.execute("SELECT COUNT(*) FROM spots")
        print(f"[verify] spots:  {cur.fetchone()[0]}")

    except Exception as e:
        conn.rollback()
        print(f"\nERROR: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        cur.close()
        conn.close()

    print("\nDone.")


if __name__ == "__main__":
    main()

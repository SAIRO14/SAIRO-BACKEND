#!/usr/bin/env python3
"""One-time data import: deduped_results.json (photos) + spots_phase1_checkpoint.json (spots)"""

import json
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
                spot_id, name, region_name, lat, lng, image_url,
                operating_hours, closed_days, parking, contact,
                cat1, cat2, cat3
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (spot_id) DO NOTHING
            """,
            (
                item["contentid"],
                item.get("title", ""),
                region_name,
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

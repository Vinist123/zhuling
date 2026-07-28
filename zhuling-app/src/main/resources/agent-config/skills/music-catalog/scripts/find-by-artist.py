import json
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: find-by-artist.py <artist>")

    artist = sys.argv[1]
    catalog_path = Path(__file__).resolve().parents[1] / "data" / "catalog.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    songs = [song["title"] for song in catalog["songs"] if song["artist"] == artist]
    print(json.dumps({"artist": artist, "songs": songs}, ensure_ascii=False))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Build-time helper: copies the `crafting` section from a VoidVault config JSON
into the asset item definition.

Usage:
  python tools/sync_crafting_config.py config.example.json

Why this exists:
  Hytale recipes are loaded from item assets. `crafting.enabled=false` is handled
  at runtime by VoidVault through the crafting registry hook, but changing the
  ingredient list itself should be synced into the asset before building the mod jar.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ITEM_JSON = ROOT / "src/main/resources/Server/Item/Items/Furniture/Metal/VoidVault.json"


def main() -> int:
    config_path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "config.example.json"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    item = json.loads(ITEM_JSON.read_text(encoding="utf-8"))
    crafting = config.get("crafting", {})

    if not crafting.get("enabled", True):
        item.pop("Recipe", None)
    else:
        bench = crafting.get("benchRequirement", {})
        item["Recipe"] = {
            "TimeSeconds": crafting.get("timeSeconds", 5),
            "Input": [
                {"ItemId": entry["itemId"], "Quantity": entry["quantity"]}
                for entry in crafting.get("input", [])
            ],
            "BenchRequirement": [
                {
                    "Id": bench.get("id", "Workbench"),
                    "Type": bench.get("type", "Crafting"),
                    "Categories": bench.get("categories", ["Workbench_Survival"]),
                    "RequiredTierLevel": bench.get("requiredTierLevel", 3),
                }
            ],
        }

    ITEM_JSON.write_text(json.dumps(item, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Synced crafting config into {ITEM_JSON}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

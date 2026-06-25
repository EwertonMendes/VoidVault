# 🟣 VoidVault

> A source-available Hytale server mod that adds a cross-dimensional personal vault for each player.

<img width="724" height="543" alt="VoidVault_logo" src="https://github.com/user-attachments/assets/afe8b5cd-84e7-422a-b86f-4af56ea9d453" />

VoidVault is a source-available Hytale server mod that adds a cross-dimensional personal vault for each player.

It provides a physical craftable vault block, optional permission-based multi-vaults, player-bound SQLite storage, configurable slot tiers, LuckPerms-compatible permission checks, and an in-game migration path from EnderChest mod.

The project is designed to be easy to maintain, fork, and contribute to, while keeping the data migration logic safe enough for real servers.

---

## 🔥 VoidVault is sponsored by HyBrasa / VoidVault é patrocinado por HyBrasa

Join the best Brazilian RPG server, click the image below and join our discord:
Participe do melhor servidor de RPG do Brasil! Clique na imagem abaixo e entre no nosso Discord:

**Server IP**: `jogar.hybrasa.com.br:11276`

<a href="https://discord.gg/cHEMmhXQ9K" target="_blank" rel="noopener noreferrer">
  <img width="468" height="60" alt="hybrasa_banner" src="https://github.com/user-attachments/assets/d78562ce-97a5-405d-a622-10ab4989345e" />
</a>

---

## 🎯 Project goals

* Provide a reliable personal vault system for Hytale servers.
* Offer a clean migration path from the legacy EnderChest mod.
* Keep player data safe during imports, reloads, shutdowns, and slot-tier changes.
* Support both small servers and larger servers with permission-based slot and vault tiers.
* Keep the codebase approachable for contributors.

---

## ✨ Current features

| Feature                                               | Description                                                  |
| ----------------------------------------------------- | ------------------------------------------------------------ |
| `/vv` and `/voidvault` command roots                  | Open the effective default vault and expose admin commands. |
| Physical craftable `Void Vault` block                 | Allows players to access their vault through a block.        |
| Custom VoidVault opening sound                        | Plays when opening the physical vault block.                 |
| SQLite storage by default                             | Stores vault data locally.                                   |
| Optional multi-vault support                          | Allows multiple vaults per player when enabled.              |
| Configurable visible slot tiers                       | Supports different vault sizes by permission or group.       |
| LuckPerms-compatible permission and group checks      | Reads groups through reflection when LuckPerms is installed. |
| Admin reload command                                  | Reloads runtime configuration.                               |
| Admin inspect command                                 | Allows staff to inspect player vaults.                       |
| In-game EnderChest importer                           | Imports data from the legacy EnderChest mod.                 |
| Overflow-safe migration                               | Preserves slots above a player's current visible limit.      |
| Runtime config reload                                 | Applies config changes without full server restart.          |
| Configurable crafting enable/disable flag             | Allows server owners to disable crafting.                    |
| Build-ready Gradle project with ShadowJar             | Ready for local builds and releases.                         |
| Local `runServer` Gradle task for development servers | Starts a local Hytale test server.                           |
| Card-based vault selector                             | Shows six spacious cards per page with occupancy, overflow and item icons. |
| Vault management page                                 | Rename, choose a real item icon, use 25 preset colors or a custom HEX color, favorite and set default. |
| Vault organization                                    | Deterministically compacts and sorts visible vault slots.   |
| Deposit similar items                                 | Moves only stack-compatible items already in the vault.     |
| Debounced persistence                                 | Groups rapid inventory changes while still flushing safely.|

---

## 🌳 Command tree

| Command                                   | Description                                        |
| ----------------------------------------- | -------------------------------------------------- |
| `/vv`                                     | Opens the player's effective default vault.        |
| `/voidvault`                              | Opens the player's effective default vault.        |
| `/voidvault help`                         | Shows available VoidVault commands.                |
| `/voidvault overflow`                     | Shows hidden overflow item slots.                  |
| `/voidvault overflow <number\|all>`       | Shows overflow for a specific vault or all vaults. |
| `/voidvault list`                         | Lists available vaults.                            |
| `/vv <number>`                            | Opens a specific vault.                            |
| `/vv ui`                                  | Opens the multi-vault selector page.               |
| `/vv rename <number> <name\|reset>`       | Sets or resets a custom vault label.               |
| `/voidvault open <player\|uuid>`          | Opens another player's main vault.                 |
| `/voidvault open <player\|uuid> <number>` | Opens a specific vault from another player.        |
| `/voidvault reload`                       | Reloads the VoidVault configuration.               |
| `/voidvault import enderchest`            | Imports data from the legacy EnderChest mod.       |

---

## 🔐 Permissions

Default command permissions are configured in `mods/VoidVault/config.json`:

| Permission               | Purpose                                 |
| ------------------------ | --------------------------------------- |
| `voidvault.use`          | Allows players to use VoidVault.        |
| `voidvault.admin`        | Allows admin-level VoidVault actions.   |
| `voidvault.admin.reload` | Allows reloading the VoidVault config.  |
| `voidvault.ui`           | Allows opening the multi-vault selector UI. |
| `voidvault.admin.import` | Allows running the EnderChest importer. |

Default slot permissions:

| Permission             |    Slots |
| ---------------------- | -------: |
| `voidvault.slots.vip1` | 18 slots |
| `voidvault.slots.vip2` | 27 slots |
| `voidvault.slots.vip3` | 36 slots |
| `voidvault.slots.vip4` | 54 slots |
| `voidvault.slots.vip5` | 63 slots |

Legacy EnderChest permissions are also supported by default to make migration easier:

| Permission         |    Slots |
| ------------------ | -------: |
| `enderchests.vip`  | 27 slots |
| `enderchests.vip+` | 54 slots |
| `enderchests.vip5` | 63 slots |

Default multi-vault permissions when `multi-vaults.enabled` is set to `true`:

| Permission              |    Vaults |
| ----------------------- | --------: |
| `voidvault.vaults.vip1` |  2 vaults |
| `voidvault.vaults.vip2` |  3 vaults |
| `voidvault.vaults.vip5` | 10 vaults |

---

## 👑 LuckPerms support

VoidVault can work without LuckPerms, but it can also read LuckPerms groups through reflection when LuckPerms is installed.

Slot tiers can be configured by permission node and/or LuckPerms group:

```json
{
  "id": "vip5",
  "slots": 63,
  "permission": "voidvault.slots.vip5",
  "luckPermsGroups": ["vip5"]
}
```

The highest matching slot tier is used.

---

## ⚙️ Configuration

The default configuration is generated at:

```txt
mods/VoidVault/config.json
```

A development example is available at:

```txt
config.example.json
```

Main config sections:

| Section        | Purpose                                      |
| -------------- | -------------------------------------------- |
| `database`     | Database type and file path.                 |
| `commands`     | Command names, aliases and permissions.      |
| `slots`        | Visible slot limits and slot tiers.          |
| `multi-vaults` | Multi-vault access and vault tiers.          |
| `crafting`     | Crafting enable/disable behavior.            |
| `importer`     | Legacy EnderChest import paths and behavior. |
| `safety`       | Save, debounce and safety behavior.          |
| `organization` | Sorting and deposit-similar behavior.        |

---

## 💜 Multi-vault behavior

Multi-vaults are disabled by default. Existing servers keep the same `/vv` and block behavior until `multi-vaults.enabled` is set to `true`.

When enabled, `/vv` opens the player's effective default vault, `/vv <number>` opens a specific accessible vault, and `/vv ui` opens the card-based selector. The selector displays occupancy, overflow and the selected Hytale item icon. Its management page allows renaming, choosing an item icon, selecting from 25 preset accent colors, entering a custom HEX color, or opening a dedicated native ColorPicker page, as well as favoriting, setting a default vault, sorting, and depositing similar items. The existing `/vv rename <number> <name|reset>` command remains available.

The item-icon picker reads the server's live item registry, so it supports base-game items and items registered by other installed asset packs. It renders only 24 results at a time, supports server-side search and pagination, and shares a lazy registry index across picker pages. Selecting an icon is validated against the live registry. If a saved item is later removed, VoidVault keeps the stored preference, displays a safe fallback, and lets the player replace or reset it without breaking the UI.

The color area offers a larger translated preset palette, a server-validated HEX field, and a button that opens the native Hytale `ColorPicker` on a dedicated page. Keeping the native picker out of the initial management document prevents a picker-rendering failure from disconnecting players merely by opening **Manage**. The dedicated picker uses the shared Hytale default style and a full-size 310×290 viewport; its changes remain a page-local draft until Apply is pressed, avoiding a database write for every mouse movement. The same color resolver is used by the management panel and selector cards, so custom colors remain visually consistent.

If the configured default vault becomes temporarily inaccessible, `/vv` safely falls back to Vault 1 without deleting the preference. Interacting with the physical Void Vault block still opens Vault 1 directly when the player only has one vault, or opens the selector when the player has multiple vaults.

Vault count is controlled by `multi-vaults.defaultVaults`, `multi-vaults.maxVaults`, permission tiers and LuckPerms groups. The hard safety limit is 10000 vaults per player. Losing access to extra vaults never deletes data; locked vaults remain stored and become accessible again if the player regains permission.

---

## 🛡️ Slot overflow behavior

VoidVault does not delete items when a player has fewer visible slots than the amount of data stored.

For example, if a player has items in slots `0` through `62`, but their current tier only grants `9` visible slots, the extra slots remain stored in the database and become visible again when the player receives a tier with enough slots.

This is especially important during EnderChest migrations.

---

## 🔨 Crafting configuration

VoidVault has a craftable block item.

The runtime config contains:

```json
{
  "crafting": {
    "enabled": true
  }
}
```

When `crafting.enabled` is set to `false`, VoidVault attempts to disable the recipe at runtime.

Hytale loads the actual recipe ingredients from the item asset JSON, so ingredient changes should be synced before building the jar:

---

## 🔁 Migration from EnderChest

VoidVault can import data from EnderChest mod.

Default legacy database path:

```txt
mods/kvothe_EnderChest/enderchest.db
```

Default legacy JSON directory:

```txt
mods/kvothe_EnderChest/ender_chest_data
```

Run the import in-game:

```txt
/voidvault import enderchest
```

VoidVault will:

* Read legacy EnderChest player vaults.
* Import valid inventory data into `mods/VoidVault/voidvault.db`.
* Preserve legacy slot indexes.
* Keep overflow items stored safely.
* Skip non-empty VoidVault records unless overwrite behavior is enabled.
* Overwrite empty `{}` records created before migration.
* Generate import reports under `mods/VoidVault/reports/`.
* Create a backup before confirmed imports when `createBackupBeforeConfirm` is enabled.

---

## 🗃️ Legacy EnderChest database format

The expected legacy table is:

```sql
CREATE TABLE ender_chests (
  uuid TEXT PRIMARY KEY,
  inventory_data TEXT NOT NULL,
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

The expected inventory JSON format is:

```json
{
  "0": {
    "id": "Potion_Health_Small",
    "amount": 3,
    "metadata": "{...}",
    "durability": 0.0
  }
}
```

---

## 🗄️ VoidVault database format

VoidVault stores data in SQLite by default:

```txt
mods/VoidVault/voidvault.db
```

The legacy table is kept for compatibility:

```txt
void_vaults
```

VoidVault stores active vault data in:

```txt
void_vault_inventories
```

Each row stores:

```txt
uuid
vault_id
inventory_data
source
last_updated
```

Vault 1 is mirrored to the legacy table for safer rollback. The `inventory_data` format is intentionally close to the legacy EnderChest format so migrations remain simple and auditable.

VoidVault 0.3.0 stores per-vault presentation metadata in:

```txt
void_vault_metadata
```

This table preserves the custom name and adds an optional registered Hytale item ID for the icon, a validated preset color ID or normalized `#RRGGBB` custom color, favorite state, and a single explicit default vault per player. Existing schema-v2 databases are migrated automatically and idempotently. Item IDs received from the UI are never trusted: they are resolved from the server-side picker page and validated against the current item registry before persistence.

---

## 🧰 Development requirements

* JDK 25
* Gradle Wrapper script included in the project
* A local Hytale server/modding environment
* Optional: LuckPerms installed on the test server

The project uses:

| Dependency        | Usage                     |
| ----------------- | ------------------------- |
| Hytale server API | `compileOnly`             |
| SQLite JDBC       | Bundled through ShadowJar |
| Gson              | Bundled through ShadowJar |
| BSON              | Bundled through ShadowJar |

---

## 🏗️ Build

On Windows PowerShell:

```powershell
Remove-Item -Recurse -Force .\build -ErrorAction SilentlyContinue
.\gradlew.bat clean build
```

On Linux/macOS:

```bash
./gradlew clean build
```

The generated mod jar is:

```txt
build/libs/VoidVault-x.x.x.jar
```

---

## 🚀 Local runServer task

The project includes a `runServer` Gradle task based on the standard Hytale install layout.

By default, it uses the local Hytale installation for the selected patchline:

```txt
<user home>/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar
<user home>/AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip
```

Then run:

```bash
./gradlew runServer
```

You can override the paths in `gradle.properties`:

```properties
hytale_home=C:/Users/YOUR_USER/AppData/Roaming/Hytale
patchline=release
server_jar=C:/Path/To/HytaleServer.jar
```

The task builds the shaded jar, copies it to `run/run_mods`, and starts the server with `--allow-op`, `--disable-sentry`, `--assets`, and `--mods`.

---

## ✅ Local testing checklist

After building and copying the jar to your test server:

| Command                            | Purpose                           |
| ---------------------------------- | --------------------------------- |
| `/voidvault help`                  | Check the help command.           |
| `/vv`                              | Open the main vault.              |
| `/voidvault reload`                | Reload the configuration.         |
| `/voidvault overflow`              | Check main vault overflow.        |
| `/voidvault overflow all`          | Check overflow across all vaults. |
| `/voidvault list`                  | List available vaults.            |
| `/vv 2`                            | Open Vault 2.                     |
| `/vv ui`                           | Open the selector UI.             |
| `/vv rename 2 Ores`                | Set a custom vault name.          |
| `/vv rename 2 reset`               | Reset a custom vault name.        |
| `/voidvault import enderchest`     | Test EnderChest import.           |
| `/voidvault open <player\|uuid>`   | Open another player's main vault. |
| `/voidvault open <player\|uuid> 2` | Open another player's Vault 2.    |

Recommended manual tests:

* Open your own vault with `/vv`.
* Place and use the physical Void Vault block.
* Confirm the custom open sound plays when opening the physical block.
* Add items, close the vault, restart the server, and confirm items persist.
* Test different slot tiers.
* Test LuckPerms group-based slot tiers.
* Import a copy of an EnderChest database.
* Confirm overflow items are preserved.
* Confirm admin inspect works.
* Enable multi-vaults in a copy of the config and test `/vv 2`.
* Confirm the physical block opens the selector for players with multiple vaults.
* Confirm locked extra vaults remain stored after permission changes.
* Confirm `reload` applies config changes.
* Open `/vv ui` and verify the selector renders six cards per page without disconnecting the client.
* Type a new name in the management page, click Save, and confirm the exact typed value is used.
* Open the item-icon picker, search by the translated item name, submit with Enter and with the Search button, change pages, select an item, and confirm the same native icon rendering appears on both the vault card and management page.
* Remove or disable an asset pack that supplied a selected icon in a disposable test environment and confirm VoidVault shows its fallback without disconnecting the client.
* Test icon, color, favorite and default-vault persistence after restart.
* Verify sort changes only visible slots and preserves overflow.
* Verify deposit-similar ignores incompatible metadata, equipment and the hotbar by default.
* Move several stacks rapidly and confirm debounce persistence still survives close/restart.

---

## 📁 Project structure

```txt
src/main/java/tblack/voidvault/
  commands/      Command registration and command handlers
  config/        Config loading and normalization
  importer/      Legacy EnderChest import logic
  model/         Shared data models
  permissions/   Permission and LuckPerms integration
  service/       Metadata, summaries, sorting and deposit operations
  storage/       SQLite, loaded vaults and debounced persistence
  systems/       Block interaction systems
  ui/            Custom UI selector pages
  util/          Shared utility classes

src/main/resources/
  manifest.json
  Common/
  Server/
```

---

## 🤝 Contributing

Contributions are welcome.

Before opening a pull request:

1. Keep the project buildable with `./gradlew clean build`.
2. Avoid breaking existing config keys when possible.
3. Preserve migration safety.
4. Do not delete unknown or invalid legacy items during import.
5. Prefer clear logs and reports over silent failure.
6. Keep code readable and easy to audit.

---

## 🧭 Development principles

VoidVault should be conservative with player data.

When in doubt:

* Preserve data instead of deleting it.
* Log migration problems.
* Skip unsafe writes.
* Keep backups before destructive operations.
* Keep old slot indexes intact.
* Avoid hard dependencies on optional plugins.

---

## 📜 License

VoidVault is licensed under the PolyForm Noncommercial License 1.0.0.

VoidVault is source-available for non-commercial use. You may read, use, copy, modify, fork, and contribute to the project for non-commercial purposes.

You may not sell copies of this project, sell modified versions of this project, or use this project as the basis of a commercial product or paid service without explicit written permission from the project maintainers.

Commercial licensing exceptions may be granted by the maintainers on request.

Required Notice: Copyright (c) 2026 Tblack

# VoidVault

VoidVault is a source-available Hytale server mod that adds a cross-dimensional personal vault for each player.

It provides a physical craftable vault block, player-bound SQLite storage, configurable slot tiers, LuckPerms-compatible permission checks, and an in-game migration path from EnderChest mod.

The project is designed to be easy to maintain, fork, and contribute to, while keeping the data migration logic safe enough for real servers.

## VoidVault is sponsored by HyBrasa / VoidVault é patrocinado por HyBrasa🔥

Join the best Brazilian RPG server, click the image below and join our discord:
Participe do melhor servidor de RPG do Brasil! Clique na imagem abaixo e entre no nosso Discord:

**Server IP**: enx-cirion-69.enx.host:11276

<a href="https://discord.gg/cHEMmhXQ9K" target="_blank" rel="noopener noreferrer">
  <img width="468" height="60" alt="hybrasa_banner" src="https://github.com/user-attachments/assets/d78562ce-97a5-405d-a622-10ab4989345e" />
</a>

## Project goals

- Provide a reliable personal vault system for Hytale servers.
- Offer a clean migration path from the legacy EnderChest mod.
- Keep player data safe during imports, reloads, shutdowns, and slot-tier changes.
- Support both small servers and larger servers with permission-based slot tiers.
- Keep the codebase approachable for contributors.

## Current features

- `/vv` and `/voidvault` command roots.
- Physical craftable `Void Vault` block.
- Custom VoidVault opening sound.
- SQLite storage by default.
- Configurable visible slot tiers.
- LuckPerms-compatible permission and group checks.
- Admin reload command.
- Admin inspect command.
- In-game EnderChest importer.
- Overflow-safe migration for slots above a player's current visible limit.
- Runtime config reload.
- Configurable crafting enable/disable flag.
- Build-ready Gradle project with ShadowJar.
- Local `runServer` Gradle task for development servers.

## Command tree

```txt
/vv
/voidvault
/voidvault help
/voidvault overflow
/voidvault open <player|uuid>
/voidvault reload
/voidvault import enderchest
```

## Permissions

Default command permissions are configured in `mods/VoidVault/config.json`:

```txt
voidvault.use
voidvault.admin
voidvault.admin.reload
voidvault.admin.import
```

Default slot permissions:

```txt
voidvault.slots.vip1 -> 18 slots
voidvault.slots.vip2 -> 27 slots
voidvault.slots.vip3 -> 36 slots
voidvault.slots.vip4 -> 54 slots
voidvault.slots.vip5 -> 63 slots
```

Legacy EnderChest permissions are also supported by default to make migration easier:

```txt
enderchests.vip  -> 27 slots
enderchests.vip+ -> 54 slots
enderchests.vip5 -> 63 slots
```

## LuckPerms support

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

## Configuration

The default configuration is generated at:

```txt
mods/VoidVault/config.json
```

A development example is available at:

```txt
config.example.json
```

Main config sections:

```txt
database
commands
slots
crafting
importer
safety
```

## Slot overflow behavior

VoidVault does not delete items when a player has fewer visible slots than the amount of data stored.

For example, if a player has items in slots `0` through `62`, but their current tier only grants `9` visible slots, the extra slots remain stored in the database and become visible again when the player receives a tier with enough slots.

This is especially important during EnderChest migrations.

## Crafting configuration

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

## Migration from EnderChest

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

- Read legacy EnderChest player vaults.
- Import valid inventory data into `mods/VoidVault/voidvault.db`.
- Preserve legacy slot indexes.
- Keep overflow items stored safely.
- Skip non-empty VoidVault records unless overwrite behavior is enabled.
- Overwrite empty `{}` records created before migration.
- Generate import reports under `mods/VoidVault/reports/`.
- Create a backup before confirmed imports when `createBackupBeforeConfirm` is enabled.

## Legacy EnderChest database format

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

## VoidVault database format

VoidVault stores data in SQLite by default:

```txt
mods/VoidVault/voidvault.db
```

The main table is:

```txt
void_vaults
```

Each row stores:

```txt
uuid
inventory_data
source
last_updated
```

The `inventory_data` format is intentionally close to the legacy EnderChest format so migrations remain simple and auditable.

## Development requirements

- JDK 25
- Gradle Wrapper script included in the project
- A local Hytale server/modding environment
- Optional: LuckPerms installed on the test server

The project uses:

- Hytale server API as `compileOnly`
- SQLite JDBC bundled through ShadowJar
- Gson bundled through ShadowJar
- BSON bundled through ShadowJar

## Build

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

## Local runServer task

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

## Local testing checklist

After building and copying the jar to your test server:

```txt
/voidvault help
/vv
/voidvault reload
/voidvault overflow
/voidvault import enderchest
/voidvault open <player|uuid>
```

Recommended manual tests:

- Open your own vault with `/vv`.
- Place and use the physical Void Vault block.
- Confirm the custom open sound plays when opening the physical block.
- Add items, close the vault, restart the server, and confirm items persist.
- Test different slot tiers.
- Test LuckPerms group-based slot tiers.
- Import a copy of an EnderChest database.
- Confirm overflow items are preserved.
- Confirm admin inspect works.
- Confirm `reload` applies config changes.

## Project structure

```txt
src/main/java/tblack/voidvault/
  commands/      Command registration and command handlers
  config/        Config loading and normalization
  importer/      Legacy EnderChest import logic
  model/         Shared data models
  permissions/   Permission and LuckPerms integration
  storage/       SQLite and vault inventory storage
  systems/       Block interaction systems
  util/          Shared utility classes

src/main/resources/
  manifest.json
  Common/
  Server/
```

## Contributing

Contributions are welcome.

Before opening a pull request:

1. Keep the project buildable with `./gradlew clean build`.
2. Avoid breaking existing config keys when possible.
3. Preserve migration safety.
4. Do not delete unknown or invalid legacy items during import.
5. Prefer clear logs and reports over silent failure.
6. Keep code readable and easy to audit.

## Development principles

VoidVault should be conservative with player data.

When in doubt:

- Preserve data instead of deleting it.
- Log migration problems.
- Skip unsafe writes.
- Keep backups before destructive operations.
- Keep old slot indexes intact.
- Avoid hard dependencies on optional plugins.

## License

VoidVault is licensed under the PolyForm Noncommercial License 1.0.0.

VoidVault is source-available for non-commercial use. You may read, use, copy, modify, fork, and contribute to the project for non-commercial purposes.

You may not sell copies of this project, sell modified versions of this project, or use this project as the basis of a commercial product or paid service without explicit written permission from the project maintainers.

Commercial licensing exceptions may be granted by the maintainers on request.

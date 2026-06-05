# EnderChest DB inspection notes

The uploaded `kvothe_EnderChest` backup was inspected for migration compatibility.

## Files found

```txt
kvothe_EnderChest/config.json
kvothe_EnderChest/enderchest.db
kvothe_EnderChest/permissions.json
```

## Legacy config

```json
{
  "enableCrafting": true,
  "database": {
    "type": "sqlite",
    "host": "localhost",
    "port": 5432,
    "name": "enderchest",
    "user": "postgres",
    "password": "password"
  }
}
```

## SQLite schema

```sql
CREATE TABLE ender_chests (
  uuid TEXT PRIMARY KEY,
  inventory_data TEXT NOT NULL,
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Data summary from uploaded server DB

```txt
Rows: 28
Rows with items: 7
Total occupied item slots: 224
Highest slot index found: 62
Players with data above 9 default slots: yes
```

This is why VoidVault preserves overflow slots during both import and later saves.

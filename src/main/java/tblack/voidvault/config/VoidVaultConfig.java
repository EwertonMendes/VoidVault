package tblack.voidvault.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class VoidVaultConfig {
    public static final int MAX_VAULTS_LIMIT = 10_000;

    public String configVersion = "2";
    public Database database = new Database();
    public Commands commands = new Commands();
    public Slots slots = new Slots();
    @SerializedName("multi-vaults")
    public MultiVaults multiVaults = new MultiVaults();
    public Crafting crafting = new Crafting();
    public Importer importer = new Importer();
    public Safety safety = new Safety();

    public static class Database {
        public String type = "sqlite";
        public String file = "mods/VoidVault/voidvault.db";
    }

    public static class Commands {
        public String primary = "voidvault";
        public List<String> aliases = new ArrayList<>(List.of("vv"));
        public String usePermission = "voidvault.use";
        public String adminPermission = "voidvault.admin";
        public String reloadPermission = "voidvault.admin.reload";
        public String importPermission = "voidvault.admin.import";
    }

    public static class Slots {
        public int defaultSlots = 9;
        public int maxSlots = 63;
        public boolean allowOverflow = true;
        public List<Tier> tiers = new ArrayList<>(List.of(
                new Tier("vip1", 18, "voidvault.slots.vip1", List.of("vip1")),
                new Tier("vip2", 27, "voidvault.slots.vip2", List.of("vip2")),
                new Tier("vip3", 36, "voidvault.slots.vip3", List.of("vip3")),
                new Tier("vip4", 54, "voidvault.slots.vip4", List.of("vip4")),
                new Tier("vip5", 63, "voidvault.slots.vip5", List.of("vip5")),
                new Tier("legacy_endervip", 27, "enderchests.vip", List.of()),
                new Tier("legacy_endervip_plus", 54, "enderchests.vip+", List.of()),
                new Tier("legacy_endervip5", 63, "enderchests.vip5", List.of())
        ));
    }

    public static class Tier {
        public String id;
        public int slots;
        public String permission;
        public List<String> luckPermsGroups = new ArrayList<>();

        public Tier() {
        }

        public Tier(String id, int slots, String permission, List<String> luckPermsGroups) {
            this.id = id;
            this.slots = slots;
            this.permission = permission;
            this.luckPermsGroups = new ArrayList<>(luckPermsGroups);
        }
    }

    public static class MultiVaults {
        public boolean enabled = false;
        public int defaultVaults = 1;
        public int maxVaults = 10;
        public List<MultiVaultTier> tiers = new ArrayList<>(List.of(
                new MultiVaultTier("vip1", 2, "voidvault.vaults.vip1", List.of("vip1")),
                new MultiVaultTier("vip2", 3, "voidvault.vaults.vip2", List.of("vip2")),
                new MultiVaultTier("vip5", 10, "voidvault.vaults.vip5", List.of("vip5"))
        ));
    }

    public static class MultiVaultTier {
        public String id;
        public int vaults;
        public String permission;
        public List<String> luckPermsGroups = new ArrayList<>();

        public MultiVaultTier() {
        }

        public MultiVaultTier(String id, int vaults, String permission, List<String> luckPermsGroups) {
            this.id = id;
            this.vaults = vaults;
            this.permission = permission;
            this.luckPermsGroups = new ArrayList<>(luckPermsGroups);
        }
    }

    public static class Crafting {
        public boolean enabled = true;
        public double timeSeconds = 5.0;
        public List<Ingredient> input = new ArrayList<>(List.of(
                new Ingredient("Ingredient_Voidheart", 4),
                new Ingredient("Ingredient_Void_Essence", 20),
                new Ingredient("Ingredient_Bar_Adamantite", 1)
        ));
        public BenchRequirement benchRequirement = new BenchRequirement();
    }

    public static class Ingredient {
        public String itemId;
        public int quantity;

        public Ingredient() {
        }

        public Ingredient(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    public static class BenchRequirement {
        public String id = "Workbench";
        public String type = "Crafting";
        public List<String> categories = new ArrayList<>(List.of("Workbench_Survival"));
        public int requiredTierLevel = 3;
    }

    public static class Importer {
        public String legacyDatabasePath = "mods/kvothe_EnderChest/enderchest.db";
        public String legacyJsonDirectory = "mods/kvothe_EnderChest/ender_chest_data";
        public boolean skipEmptyVaults = false;
        public boolean overwriteExisting = false;
        public boolean createBackupBeforeConfirm = true;
    }

    public static class Safety {
        public boolean preventDoubleOpen = true;
        public boolean saveOnEveryChange = true;
        public boolean saveOnClose = true;
        public boolean saveOnShutdown = true;
    }

    public int clampSlots(int value) {
        int min = Math.max(1, slots.defaultSlots);
        int max = Math.max(min, slots.maxSlots);
        return Math.max(min, Math.min(max, value));
    }

    public int clampVaults(int value) {
        int min = Math.max(1, multiVaults.defaultVaults);
        int max = Math.max(min, Math.min(MAX_VAULTS_LIMIT, multiVaults.maxVaults));
        return Math.max(min, Math.min(max, value));
    }

    public boolean isMultiVaultEnabled() {
        return multiVaults != null && multiVaults.enabled;
    }
}

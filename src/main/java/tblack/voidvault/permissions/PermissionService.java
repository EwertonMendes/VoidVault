package tblack.voidvault.permissions;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import tblack.voidvault.config.VoidVaultConfig;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionService {
    private static final long CACHE_TTL_MILLIS = 5_000L;

    private VoidVaultConfig config;
    private boolean luckPermsChecked;
    private Object luckPermsApi;
    private final Map<UUID, CachedInt> slotCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedInt> vaultCountCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedString> groupCache = new ConcurrentHashMap<>();

    public PermissionService(VoidVaultConfig config) {
        this.config = config;
    }

    public void reload(VoidVaultConfig config) {
        this.config = config;
        this.luckPermsChecked = false;
        this.luckPermsApi = null;
        this.slotCache.clear();
        this.vaultCountCache.clear();
        this.groupCache.clear();
        registerPermissions();
    }

    public void registerPermissions() {
        try {
            com.hypixel.hytale.server.core.permissions.PermissionsModule module = PermissionsModule.get();
            for (String permission : collectPermissions()) {
                try {
                    module.registerPermission(permission);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private java.util.Set<String> collectPermissions() {
        java.util.Set<String> perms = new java.util.HashSet<>();
        if (config == null || config.commands == null) return perms;

        addIfNotBlank(perms, config.commands.usePermission);
        addIfNotBlank(perms, config.commands.adminPermission);
        addIfNotBlank(perms, config.commands.reloadPermission);
        addIfNotBlank(perms, config.commands.importPermission);
        addIfNotBlank(perms, config.commands.uiPermission);

        if (config.slots != null && config.slots.tiers != null) {
            for (VoidVaultConfig.Tier tier : config.slots.tiers) {
                addIfNotBlank(perms, tier.permission);
            }
        }

        if (config.multiVaults != null && config.multiVaults.tiers != null) {
            for (VoidVaultConfig.MultiVaultTier tier : config.multiVaults.tiers) {
                addIfNotBlank(perms, tier.permission);
            }
        }

        return perms;
    }

    private static void addIfNotBlank(java.util.Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value);
        }
    }

    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isBlank()) {
            return false;
        }

        try {
            if (PermissionsModule.get().hasPermission(uuid, permission)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return hasLuckPermsPermission(uuid, permission);
    }

    public int getSlots(UUID uuid) {
        if (uuid == null) return config.clampSlots(config.slots.defaultSlots);

        CachedInt cached = cachedInt(slotCache, uuid);
        if (cached != null) return cached.value();

        int best = config.slots.defaultSlots;
        List<VoidVaultConfig.Tier> sorted = config.slots.tiers.stream()
                .sorted(Comparator.comparingInt((VoidVaultConfig.Tier tier) -> tier.slots).reversed())
                .toList();

        String primaryGroup = getLuckPermsPrimaryGroup(uuid);
        for (VoidVaultConfig.Tier tier : sorted) {
            if (tier == null) continue;
            if (!matchesTier(uuid, primaryGroup, tier.permission, tier.luckPermsGroups)) continue;

            best = Math.max(best, tier.slots);
            break;
        }

        int result = config.clampSlots(best);
        slotCache.put(uuid, new CachedInt(result, System.currentTimeMillis()));
        return result;
    }

    public int getVaultCount(UUID uuid) {
        if (uuid == null) return 1;

        if (!config.isMultiVaultEnabled()) {
            return 1;
        }

        CachedInt cached = cachedInt(vaultCountCache, uuid);
        if (cached != null) return cached.value();

        int best = config.multiVaults.defaultVaults;
        List<VoidVaultConfig.MultiVaultTier> sorted = config.multiVaults.tiers.stream()
                .sorted(Comparator.comparingInt((VoidVaultConfig.MultiVaultTier tier) -> tier.vaults).reversed())
                .toList();

        String primaryGroup = getLuckPermsPrimaryGroup(uuid);
        for (VoidVaultConfig.MultiVaultTier tier : sorted) {
            if (tier == null) continue;
            if (!matchesTier(uuid, primaryGroup, tier.permission, tier.luckPermsGroups)) continue;

            best = Math.max(best, tier.vaults);
            break;
        }

        int result = config.clampVaults(best);
        vaultCountCache.put(uuid, new CachedInt(result, System.currentTimeMillis()));
        return result;
    }

    public boolean canAccessVault(UUID uuid, int vaultId) {
        if (uuid == null || vaultId < 1) {
            return false;
        }
        return vaultId <= getVaultCount(uuid);
    }

    public String getLuckPermsPrimaryGroup(UUID uuid) {
        if (uuid == null) return null;

        CachedString cached = cachedString(groupCache, uuid);
        if (cached != null) return cached.value();

        try {
            Object user = loadLuckPermsUser(uuid);
            if (user == null) return null;
            Method getPrimaryGroup = user.getClass().getMethod("getPrimaryGroup");
            Object group = getPrimaryGroup.invoke(user);
            String result = group == null ? null : group.toString();
            groupCache.put(uuid, new CachedString(result, System.currentTimeMillis()));
            return result;
        } catch (Throwable ignored) {
            groupCache.put(uuid, new CachedString(null, System.currentTimeMillis()));
            return null;
        }
    }

    private boolean matchesTier(UUID uuid, String primaryGroup, String permission, List<String> groups) {
        boolean matchesPermission = permission != null && !permission.isBlank() && hasPermission(uuid, permission);
        if (matchesPermission) {
            return true;
        }
        return primaryGroup != null && groups != null && groups.stream().anyMatch(group -> group.equalsIgnoreCase(primaryGroup));
    }

    private boolean hasLuckPermsPermission(UUID uuid, String permission) {
        try {
            Object user = loadLuckPermsUser(uuid);
            if (user == null) return false;

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
            Object bool = result.getClass().getMethod("asBoolean").invoke(result);
            return Boolean.TRUE.equals(bool);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object loadLuckPermsUser(UUID uuid) throws Exception {
        Object api = getLuckPermsApi();
        if (api == null) return null;

        Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
        Object future = userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid);
        return future.getClass().getMethod("join").invoke(future);
    }

    private Object getLuckPermsApi() {
        if (luckPermsChecked) {
            return luckPermsApi;
        }
        luckPermsChecked = true;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPermsApi = provider.getMethod("get").invoke(null);
        } catch (Throwable ignored) {
            luckPermsApi = null;
        }
        return luckPermsApi;
    }

    private CachedInt cachedInt(Map<UUID, CachedInt> cache, UUID uuid) {
        if (uuid == null) return null;

        CachedInt cached = cache.get(uuid);
        if (cached == null) return null;
        if (!cached.isValid()) {
            cache.remove(uuid, cached);
            return null;
        }
        return cached;
    }

    private CachedString cachedString(Map<UUID, CachedString> cache, UUID uuid) {
        if (uuid == null) return null;

        CachedString cached = cache.get(uuid);
        if (cached == null) return null;
        if (!cached.isValid()) {
            cache.remove(uuid, cached);
            return null;
        }
        return cached;
    }

    private record CachedInt(int value, long createdAt) {
        private boolean isValid() {
            return System.currentTimeMillis() - createdAt <= CACHE_TTL_MILLIS;
        }
    }

    private record CachedString(String value, long createdAt) {
        private boolean isValid() {
            return System.currentTimeMillis() - createdAt <= CACHE_TTL_MILLIS;
        }
    }
}

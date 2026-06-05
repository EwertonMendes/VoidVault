package tblack.voidvault.permissions;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import tblack.voidvault.config.VoidVaultConfig;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PermissionService {
    private VoidVaultConfig config;
    private boolean luckPermsChecked;
    private Object luckPermsApi;

    public PermissionService(VoidVaultConfig config) {
        this.config = config;
    }

    public void reload(VoidVaultConfig config) {
        this.config = config;
        this.luckPermsChecked = false;
        this.luckPermsApi = null;
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
            // LuckPerms can replace the built-in permission system; fall back to reflection below.
        }

        return hasLuckPermsPermission(uuid, permission);
    }

    public int getSlots(UUID uuid) {
        int best = config.slots.defaultSlots;
        List<VoidVaultConfig.Tier> sorted = config.slots.tiers.stream()
                .sorted(Comparator.comparingInt((VoidVaultConfig.Tier tier) -> tier.slots).reversed())
                .toList();

        String primaryGroup = getLuckPermsPrimaryGroup(uuid);
        for (VoidVaultConfig.Tier tier : sorted) {
            if (tier == null) continue;
            boolean matchesPermission = tier.permission != null && !tier.permission.isBlank() && hasPermission(uuid, tier.permission);
            boolean matchesGroup = primaryGroup != null && tier.luckPermsGroups != null
                    && tier.luckPermsGroups.stream().anyMatch(group -> group.equalsIgnoreCase(primaryGroup));

            if (matchesPermission || matchesGroup) {
                best = Math.max(best, tier.slots);
                break;
            }
        }

        return config.clampSlots(best);
    }

    public String getLuckPermsPrimaryGroup(UUID uuid) {
        try {
            Object user = loadLuckPermsUser(uuid);
            if (user == null) return null;
            Method getPrimaryGroup = user.getClass().getMethod("getPrimaryGroup");
            Object group = getPrimaryGroup.invoke(user);
            return group == null ? null : group.toString();
        } catch (Throwable ignored) {
            return null;
        }
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
}

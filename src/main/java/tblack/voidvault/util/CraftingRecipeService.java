package tblack.voidvault.util;

import tblack.voidvault.config.VoidVaultConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * The recipe is declared in the item asset JSON. If an admin disables crafting,
 * this runtime hook removes the loaded recipe from the Workbench registry.
 * Reflection is used because the crafting registry API is still moving between Hytale builds.
 */
public final class CraftingRecipeService {
    private CraftingRecipeService() {
    }

    public static void apply(VoidVaultConfig config) {
        if (config == null || config.crafting == null || config.crafting.enabled) {
            return;
        }
        removeRecipe("VoidVault");
    }

    @SuppressWarnings("unchecked")
    private static void removeRecipe(String outputItemId) {
        try {
            Class<?> craftingPluginClass = Class.forName("com.hypixel.hytale.builtin.crafting.CraftingPlugin");
            Method get = craftingPluginClass.getMethod("get");
            Object craftingPlugin = get.invoke(null);

            Field registriesField = craftingPluginClass.getDeclaredField("registries");
            registriesField.setAccessible(true);
            Map<String, Object> registries = (Map<String, Object>) registriesField.get(craftingPlugin);
            if (registries == null || !registries.containsKey("Workbench")) return;

            Object registry = registries.get("Workbench");
            Class<?> registryClass = Class.forName("com.hypixel.hytale.builtin.crafting.BenchRecipeRegistry");
            Object[] recipes = (Object[]) registryClass.getMethod("getAllRecipes").invoke(registry);
            String targetRecipeId = null;

            for (Object recipe : recipes) {
                Field primaryOutputField = recipe.getClass().getDeclaredField("primaryOutput");
                primaryOutputField.setAccessible(true);
                Object primaryOutput = primaryOutputField.get(recipe);
                if (primaryOutput == null) continue;

                Field itemIdField = primaryOutput.getClass().getDeclaredField("itemId");
                itemIdField.setAccessible(true);
                Object itemId = itemIdField.get(primaryOutput);
                if (!outputItemId.equals(itemId)) continue;

                Field idField = recipe.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                targetRecipeId = (String) idField.get(recipe);
                break;
            }

            if (targetRecipeId != null) {
                registryClass.getMethod("removeRecipe", String.class).invoke(registry, targetRecipeId);
                System.out.println("[VoidVault] Removed crafting recipe for " + outputItemId + " because crafting.enabled=false");
            }
        } catch (Throwable throwable) {
            System.err.println("[VoidVault] Could not remove recipe at runtime: " + throwable.getMessage());
        }
    }
}

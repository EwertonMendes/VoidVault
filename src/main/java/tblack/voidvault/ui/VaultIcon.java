package tblack.voidvault.ui;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class VaultIcon {
    public static final String DEFAULT_ID = "default";

    private static final Map<String, Entry> ICONS = new LinkedHashMap<>();

    static {
        register("default", "VV");
        register("minerals", "ORE");
        register("building", "BLD");
        register("equipment", "GEAR");
        register("food", "FOOD");
        register("potions", "POT");
        register("resources", "RES");
        register("valuable", "GEM");
        register("farming", "FARM");
        register("misc", "MISC");
    }

    private VaultIcon() {
    }

    private static void register(String id, String shortLabel) {
        ICONS.put(id, new Entry(shortLabel));
    }

    public static boolean isValidId(String id) {
        return id != null && ICONS.containsKey(id);
    }

    public static Set<String> getAllIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ICONS.keySet()));
    }

    public static String normalize(String id) {
        return isValidId(id) ? id : DEFAULT_ID;
    }

    public static String shortLabel(String id) {
        return ICONS.get(normalize(id)).shortLabel();
    }

    public record Entry(String shortLabel) {
    }
}

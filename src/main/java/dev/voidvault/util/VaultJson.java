package dev.voidvault.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.voidvault.model.SavedItem;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VaultJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type TYPE = new TypeToken<Map<Integer, SavedItem>>() {}.getType();

    private VaultJson() {
    }

    public static Map<Integer, SavedItem> parse(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<Integer, SavedItem> parsed = GSON.fromJson(json, TYPE);
            if (parsed == null) {
                return new LinkedHashMap<>();
            }
            return new LinkedHashMap<>(parsed);
        } catch (JsonSyntaxException exception) {
            throw exception;
        }
    }

    public static String stringify(Map<Integer, SavedItem> items) {
        if (items == null || items.isEmpty()) {
            return "{}";
        }
        return GSON.toJson(items, TYPE);
    }

    public static int countValidItems(Map<Integer, SavedItem> items) {
        int count = 0;
        for (SavedItem item : items.values()) {
            if (item != null && item.isValid()) {
                count++;
            }
        }
        return count;
    }

    public static int maxSlot(Map<Integer, SavedItem> items) {
        int max = -1;
        for (Integer slot : items.keySet()) {
            if (slot != null && slot > max) {
                max = slot;
            }
        }
        return max;
    }
}

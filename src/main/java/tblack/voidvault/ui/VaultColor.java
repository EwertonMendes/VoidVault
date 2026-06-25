package tblack.voidvault.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VaultColor {
    public static final String DEFAULT_ID = "void";

    private static final Pattern HEX_PATTERN = Pattern.compile("^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$");
    private static final Map<String, Entry> COLORS = new LinkedHashMap<>();

    static {
        register("void", "#7A39B4", "#9A59D4", "#5E2B86");
        register("lavender", "#B57EDC");
        register("purple", "#9B59B6", "#BB77D6", "#7D3F99");
        register("violet", "#7F5AF0");
        register("magenta", "#D946EF");
        register("pink", "#EC4899");
        register("rose", "#F43F5E");
        register("red", "#E74C3C", "#FF6C5C", "#C72C1C");
        register("coral", "#FF6B6B");
        register("orange", "#E67E22", "#FF9E42", "#C65E02");
        register("amber", "#F59E0B");
        register("gold", "#F1C40F", "#FFE44F", "#D1A40F");
        register("yellow", "#FDE047");
        register("lime", "#84CC16");
        register("green", "#2ECC71", "#4EEC91", "#1EAC51");
        register("emerald", "#10B981");
        register("teal", "#14B8A6");
        register("cyan", "#1ABC9C", "#3ADCBC", "#0A9C7C");
        register("sky", "#38BDF8");
        register("blue", "#3498DB", "#54B8FB", "#2478AB");
        register("indigo", "#6366F1");
        register("navy", "#1E3A8A");
        register("brown", "#92400E");
        register("gray", "#95A5A6", "#B5C5C6", "#758586");
        register("silver", "#CBD5E1");
    }

    private VaultColor() {
    }

    private static void register(String id, String mainColor) {
        String normalized = normalizeCustomHex(mainColor);
        COLORS.put(id, createEntry(normalized));
    }

    private static void register(String id, String mainColor, String hoverColor, String accentColor) {
        COLORS.put(id, new Entry(
                requireHex(mainColor),
                requireHex(hoverColor),
                requireHex(accentColor)
        ));
    }

    public static boolean isValidId(String value) {
        return normalizeSelection(value) != null;
    }

    public static boolean isPresetId(String value) {
        if (value == null) return false;
        return COLORS.containsKey(value.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isCustomColor(String value) {
        return value != null && !isPresetId(value) && normalizeCustomHex(value) != null;
    }

    public static String normalizeSelection(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;

        String presetId = trimmed.toLowerCase(Locale.ROOT);
        if (COLORS.containsKey(presetId)) return presetId;
        return normalizeCustomHex(trimmed);
    }

    public static String normalizeCustomHex(String value) {
        if (value == null) return null;
        Matcher matcher = HEX_PATTERN.matcher(value.trim());
        if (!matcher.matches()) return null;

        String digits = matcher.group(1).toUpperCase(Locale.ROOT);
        if (digits.length() == 3) {
            digits = "" + digits.charAt(0) + digits.charAt(0)
                    + digits.charAt(1) + digits.charAt(1)
                    + digits.charAt(2) + digits.charAt(2);
        } else if (digits.length() == 8) {
            digits = digits.substring(0, 6);
        }
        return "#" + digits;
    }

    public static Set<String> getAllIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(COLORS.keySet()));
    }

    public static Entry get(String selection) {
        String normalized = normalizeSelection(selection);
        if (normalized == null) return COLORS.get(DEFAULT_ID);

        Entry preset = COLORS.get(normalized);
        return preset != null ? preset : createEntry(normalized);
    }

    public static String mainColor(String selection) {
        return get(selection).mainColor;
    }

    public static String hoverColor(String selection) {
        return get(selection).hoverColor;
    }

    public static String accentColor(String selection) {
        return get(selection).accentColor;
    }

    private static Entry createEntry(String mainColor) {
        return new Entry(
                mainColor,
                mix(mainColor, "#FFFFFF", 0.18),
                mix(mainColor, "#000000", 0.22)
        );
    }

    private static String mix(String first, String second, double secondWeight) {
        int[] a = rgb(first);
        int[] b = rgb(second);
        double firstWeight = 1.0 - secondWeight;
        int red = clamp((int) Math.round(a[0] * firstWeight + b[0] * secondWeight));
        int green = clamp((int) Math.round(a[1] * firstWeight + b[1] * secondWeight));
        int blue = clamp((int) Math.round(a[2] * firstWeight + b[2] * secondWeight));
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    private static String requireHex(String value) {
        String normalized = normalizeCustomHex(value);
        if (normalized == null) throw new IllegalArgumentException("Invalid color: " + value);
        return normalized;
    }

    private static int[] rgb(String color) {
        String normalized = normalizeCustomHex(color);
        if (normalized == null) throw new IllegalArgumentException("Invalid color: " + color);
        return new int[]{
                Integer.parseInt(normalized.substring(1, 3), 16),
                Integer.parseInt(normalized.substring(3, 5), 16),
                Integer.parseInt(normalized.substring(5, 7), 16)
        };
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public record Entry(String mainColor, String hoverColor, String accentColor) {
    }
}

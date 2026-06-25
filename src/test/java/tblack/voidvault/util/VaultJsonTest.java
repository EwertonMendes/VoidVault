package tblack.voidvault.util;

import org.junit.jupiter.api.Test;
import tblack.voidvault.model.SavedItem;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaultJsonTest {

    @Test
    void parseEmptyOrNull() {
        assertTrue(VaultJson.parse(null).isEmpty());
        assertTrue(VaultJson.parse("").isEmpty());
        assertTrue(VaultJson.parse("  ").isEmpty());
    }

    @Test
    void parseValidJson() {
        String json = "{\"0\":{\"id\":\"Item_A\",\"amount\":5,\"metadata\":null,\"durability\":0.0}}";
        Map<Integer, SavedItem> items = VaultJson.parse(json);
        assertEquals(1, items.size());
        SavedItem item = items.get(0);
        assertNotNull(item);
        assertEquals("Item_A", item.id);
        assertEquals(5, item.amount);
    }

    @Test
    void parseInvalidJsonThrows() {
        assertThrows(com.google.gson.JsonSyntaxException.class, () -> VaultJson.parse("{invalid"));
    }

    @Test
    void stringifyEmpty() {
        assertEquals("{}", VaultJson.stringify(null));
        assertEquals("{}", VaultJson.stringify(new LinkedHashMap<>()));
    }

    @Test
    void stringifyAndParseRoundtrip() {
        Map<Integer, SavedItem> original = new LinkedHashMap<>();
        original.put(0, new SavedItem("Item_A", 10, "meta", 1.5));
        original.put(5, new SavedItem("Item_B", 1, null, 0.0));

        String json = VaultJson.stringify(original);
        Map<Integer, SavedItem> parsed = VaultJson.parse(json);

        assertEquals(2, parsed.size());
        assertEquals("Item_A", parsed.get(0).id);
        assertEquals(10, parsed.get(0).amount);
        assertEquals("Item_B", parsed.get(5).id);
        assertEquals(1, parsed.get(5).amount);
    }

    @Test
    void countValidItems() {
        Map<Integer, SavedItem> items = new LinkedHashMap<>();
        items.put(0, new SavedItem("Item_A", 5, null, 0));
        items.put(1, new SavedItem(null, 0, null, 0));
        items.put(2, new SavedItem("Item_B", 1, null, 0));
        items.put(3, new SavedItem("", 0, null, 0));

        assertEquals(2, VaultJson.countValidItems(items));
    }

    @Test
    void countValidItemsEmpty() {
        assertEquals(0, VaultJson.countValidItems(new LinkedHashMap<>()));
    }

    @Test
    void maxSlot() {
        Map<Integer, SavedItem> items = new LinkedHashMap<>();
        items.put(0, new SavedItem("A", 1, null, 0));
        items.put(5, new SavedItem("B", 1, null, 0));
        items.put(2, new SavedItem("C", 1, null, 0));

        assertEquals(5, VaultJson.maxSlot(items));
    }

    @Test
    void maxSlotEmpty() {
        assertEquals(-1, VaultJson.maxSlot(new LinkedHashMap<>()));
    }
}

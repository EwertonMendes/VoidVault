package tblack.voidvault.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoidVaultConfigTest {

    @Test
    void defaultConfig() {
        VoidVaultConfig config = new VoidVaultConfig();
        assertEquals("3", config.configVersion);
        assertEquals("sqlite", config.database.type);
        assertEquals(9, config.slots.defaultSlots);
        assertEquals(63, config.slots.maxSlots);
        assertTrue(config.slots.allowOverflow);
        assertTrue(config.crafting.enabled);
        assertFalse(config.isMultiVaultEnabled());
        assertEquals(750, config.safety.saveDebounceMillis);
        assertEquals(5000, config.safety.saveMaxDelayMillis);
        assertTrue(config.organization.sortEnabled);
        assertTrue(config.organization.depositMatchingEnabled);
        assertFalse(config.organization.depositMatchingIncludeHotbar);
    }

    @Test
    void clampSlots() {
        VoidVaultConfig config = new VoidVaultConfig();
        assertEquals(9, config.clampSlots(5));
        assertEquals(9, config.clampSlots(9));
        assertEquals(27, config.clampSlots(27));
        assertEquals(63, config.clampSlots(63));
        assertEquals(63, config.clampSlots(100));
        assertEquals(9, config.clampSlots(0));
        assertEquals(9, config.clampSlots(-1));
    }

    @Test
    void clampVaults() {
        VoidVaultConfig config = new VoidVaultConfig();
        config.multiVaults.enabled = true;
        config.multiVaults.defaultVaults = 1;
        config.multiVaults.maxVaults = 10;
        assertEquals(1, config.clampVaults(1));
        assertEquals(5, config.clampVaults(5));
        assertEquals(10, config.clampVaults(10));
        assertEquals(10, config.clampVaults(20));
        assertEquals(1, config.clampVaults(0));
    }

    @Test
    void multiVaultDisabledReturnsOne() {
        VoidVaultConfig config = new VoidVaultConfig();
        assertFalse(config.isMultiVaultEnabled());
    }
}

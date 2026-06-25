package tblack.voidvault.service;

import tblack.voidvault.model.VaultIconEntry;

import java.util.List;

public interface VaultIconCatalog {
    boolean isValidItemId(String itemId);

    String resolveItemId(String storedItemId);

    List<VaultIconEntry> search(String query, String locale);

    VaultIconEntry describe(String itemId, String locale);

    void invalidate();
}

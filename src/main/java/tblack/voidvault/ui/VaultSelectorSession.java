package tblack.voidvault.ui;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.Map;
import java.util.UUID;

public record VaultSelectorSession(
        UUID viewerUuid,
        int x,
        int y,
        int z,
        int rotationIndex,
        BlockType blockType,
        int vaultCount,
        String locale,
        Map<Integer, String> vaultNames
) {
    public String vaultName(int vaultId) {
        if (vaultNames == null) return null;
        String name = vaultNames.get(vaultId);
        return name == null || name.isBlank() ? null : name;
    }
}

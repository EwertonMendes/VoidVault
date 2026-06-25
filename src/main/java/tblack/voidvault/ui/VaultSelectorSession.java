package tblack.voidvault.ui;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.model.VaultSummary;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class VaultSelectorSession {
    private final UUID viewerUuid;
    private final int x;
    private final int y;
    private final int z;
    private final int rotationIndex;
    private final BlockType blockType;
    private final int vaultCount;
    private final String locale;
    private final Map<Integer, String> vaultNames;
    private final List<VaultSummary> summaries;
    private final Map<Integer, VaultMetadata> metadataMap;
    private final AtomicLong activePageToken = new AtomicLong();

    public VaultSelectorSession(
            UUID viewerUuid,
            int x,
            int y,
            int z,
            int rotationIndex,
            BlockType blockType,
            int vaultCount,
            String locale,
            Map<Integer, String> vaultNames,
            List<VaultSummary> summaries,
            Map<Integer, VaultMetadata> metadataMap
    ) {
        this.viewerUuid = viewerUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotationIndex = rotationIndex;
        this.blockType = blockType;
        this.vaultCount = vaultCount;
        this.locale = locale;
        this.vaultNames = vaultNames == null ? Map.of() : Map.copyOf(vaultNames);
        this.summaries = summaries == null ? List.of() : List.copyOf(summaries);
        this.metadataMap = metadataMap == null ? Map.of() : Map.copyOf(metadataMap);
    }

    public UUID viewerUuid() { return viewerUuid; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int rotationIndex() { return rotationIndex; }
    public BlockType blockType() { return blockType; }
    public int vaultCount() { return vaultCount; }
    public String locale() { return locale; }
    public Map<Integer, String> vaultNames() { return vaultNames; }
    public List<VaultSummary> summaries() { return summaries; }
    public Map<Integer, VaultMetadata> metadataMap() { return metadataMap; }

    public long activatePage() {
        return activePageToken.incrementAndGet();
    }

    public boolean isCurrentPage(long token) {
        return activePageToken.get() == token;
    }

    public String vaultName(int vaultId) {
        String name = vaultNames.get(vaultId);
        return name == null || name.isBlank() ? null : name;
    }

    public VaultSummary summary(int vaultId) {
        for (VaultSummary summary : summaries) {
            if (summary.vaultId() == vaultId) return summary;
        }
        return null;
    }

    public VaultMetadata metadata(int vaultId) {
        return metadataMap.get(vaultId);
    }
}

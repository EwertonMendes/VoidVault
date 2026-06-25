package tblack.voidvault.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.voidvault.i18n.I18n;
import tblack.voidvault.model.DepositMatchingResult;
import tblack.voidvault.model.SortResult;
import tblack.voidvault.model.VaultIconEntry;
import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.model.VaultSummary;
import tblack.voidvault.service.VaultIconCatalog;
import tblack.voidvault.service.VaultMetadataService;
import tblack.voidvault.service.VaultOrganizationService;
import tblack.voidvault.service.VaultSummaryService;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.storage.VaultManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VaultSelectorService {
    private final VaultManager vaultManager;
    private final VaultMetadataService metadataService;
    private final VaultSummaryService summaryService;
    private final VaultIconCatalog iconCatalog;
    private final Map<UUID, VaultSelectorSession> sessions = new ConcurrentHashMap<>();

    public VaultSelectorService(
            VaultManager vaultManager,
            VaultMetadataService metadataService,
            VaultSummaryService summaryService,
            VaultOrganizationService ignoredOrganizationService,
            VaultIconCatalog iconCatalog
    ) {
        this.vaultManager = vaultManager;
        this.metadataService = metadataService;
        this.summaryService = summaryService;
        this.iconCatalog = iconCatalog;
    }

    public void open(Player viewer, int vaultCount) {
        open(viewer, 0, 0, 0, 0, null, vaultCount);
    }

    public void open(Player viewer, int x, int y, int z, int rotationIndex, BlockType blockType, int vaultCount) {
        if (viewer == null) return;

        UUID viewerUuid = playerUuid(viewer);
        if (viewerUuid == null) return;

        PlayerRef playerRef = Universe.get().getPlayer(viewerUuid);
        if (playerRef == null) {
            vaultManager.openVaultFromSelector(viewer, viewerUuid, DatabaseService.PRIMARY_VAULT_ID, x, y, z, rotationIndex, blockType);
            return;
        }

        Ref<EntityStore> ref = viewer.getReference();
        if (ref == null) return;

        Store<EntityStore> store = ref.getStore();
        VaultSelectorSession session = createSession(
                viewerUuid,
                x,
                y,
                z,
                rotationIndex,
                blockType,
                vaultCount,
                I18n.localeFromPlayerRef(playerRef)
        );
        sessions.put(viewerUuid, session);
        viewer.getPageManager().openCustomPage(ref, store, new VaultSelectorPage(playerRef, this, session, session.summaries()));
    }

    public boolean canSelect(VaultSelectorSession session, int vaultId) {
        return session != null
                && vaultId > 0
                && isActive(session)
                && vaultManager.canAccessVault(session.viewerUuid(), vaultId);
    }

    public boolean openSelected(Ref<EntityStore> ref, Store<EntityStore> store, VaultSelectorSession session, int vaultId) {
        if (ref == null || store == null || session == null || vaultId < 1) return false;

        Player viewer = store.getComponent(ref, Player.getComponentType());
        if (viewer == null) return false;

        sessions.remove(session.viewerUuid(), session);
        vaultManager.openVaultFromSelector(
                viewer,
                session.viewerUuid(),
                vaultId,
                session.x(),
                session.y(),
                session.z(),
                session.rotationIndex(),
                session.blockType()
        );
        return true;
    }

    public boolean openManagement(Ref<EntityStore> ref, Store<EntityStore> store, VaultSelectorSession session, int vaultId) {
        return openManagement(ref, store, session, vaultId, "");
    }

    public boolean openManagement(
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            VaultSelectorSession session,
            int vaultId,
            String statusText
    ) {
        if (ref == null || store == null || !canSelect(session, vaultId)) return false;

        Player viewer = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = Universe.get().getPlayer(session.viewerUuid());
        VaultSummary summary = getSummary(session, vaultId);
        if (viewer == null || playerRef == null || summary == null) return false;

        viewer.getPageManager().openCustomPage(
                ref,
                store,
                new VaultManagementPage(
                        playerRef,
                        this,
                        session,
                        vaultId,
                        summary,
                        getMetadata(session, vaultId),
                        statusText
                )
        );
        return true;
    }

    public boolean openColorPicker(Ref<EntityStore> ref, Store<EntityStore> store, VaultSelectorSession session, int vaultId) {
        if (ref == null || store == null || !canManage(session, vaultId)) return false;

        Player viewer = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = Universe.get().getPlayer(session.viewerUuid());
        if (viewer == null || playerRef == null) return false;

        viewer.getPageManager().openCustomPage(
                ref,
                store,
                new VaultColorPickerPage(playerRef, this, session, vaultId)
        );
        return true;
    }

    public boolean openIconPicker(Ref<EntityStore> ref, Store<EntityStore> store, VaultSelectorSession session, int vaultId) {
        if (ref == null || store == null || !canManage(session, vaultId)) return false;

        Player viewer = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = Universe.get().getPlayer(session.viewerUuid());
        if (viewer == null || playerRef == null) return false;

        viewer.getPageManager().openCustomPage(
                ref,
                store,
                new VaultIconPickerPage(playerRef, this, session, vaultId)
        );
        return true;
    }

    public boolean openSelectorFromManagement(Ref<EntityStore> ref, Store<EntityStore> store, VaultSelectorSession session) {
        if (ref == null || store == null || session == null) return false;

        Player viewer = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = Universe.get().getPlayer(session.viewerUuid());
        if (viewer == null || playerRef == null) return false;

        VaultSelectorSession refreshed = createSession(
                session.viewerUuid(),
                session.x(),
                session.y(),
                session.z(),
                session.rotationIndex(),
                session.blockType(),
                vaultManager.getVaultCount(session.viewerUuid()),
                session.locale()
        );
        sessions.put(session.viewerUuid(), refreshed);
        viewer.getPageManager().openCustomPage(ref, store, new VaultSelectorPage(playerRef, this, refreshed, refreshed.summaries()));
        return true;
    }

    public VaultSummary getSummary(VaultSelectorSession session, int vaultId) {
        if (session == null || vaultId < 1) return null;
        for (VaultSummary summary : summaryService.buildSummaries(session.viewerUuid())) {
            if (summary.vaultId() == vaultId) return summary;
        }
        return null;
    }

    public VaultMetadata getMetadata(VaultSelectorSession session, int vaultId) {
        if (session == null || vaultId < 1) return null;
        return metadataService.get(session.viewerUuid(), vaultId);
    }

    public String resolveIconItemId(String storedItemId) {
        return iconCatalog.resolveItemId(storedItemId);
    }

    public boolean isStoredIconMissing(String storedItemId) {
        return storedItemId != null && !storedItemId.isBlank() && !iconCatalog.isValidItemId(storedItemId);
    }

    public List<VaultIconEntry> searchIcons(String query, String locale) {
        return iconCatalog.search(query, locale);
    }

    public VaultIconEntry describeIcon(String itemId, String locale) {
        return iconCatalog.describe(itemId, locale);
    }

    public boolean handleRename(VaultSelectorSession session, int vaultId, String rawName) {
        if (!canManage(session, vaultId)) return false;
        return metadataService.setName(session.viewerUuid(), vaultId, vaultManager.normalizeVaultName(rawName));
    }

    public boolean handleIconChange(VaultSelectorSession session, int vaultId, String iconId) {
        if (!canManage(session, vaultId)) return false;
        return metadataService.setIcon(session.viewerUuid(), vaultId, iconId);
    }

    public boolean handleColorChange(VaultSelectorSession session, int vaultId, String colorId) {
        if (!canManage(session, vaultId)) return false;
        return metadataService.setColor(session.viewerUuid(), vaultId, colorId);
    }

    public boolean handleFavoriteToggle(VaultSelectorSession session, int vaultId, boolean favorite) {
        if (!canManage(session, vaultId)) return false;
        return metadataService.setFavorite(session.viewerUuid(), vaultId, favorite);
    }

    public boolean handleDefaultSet(VaultSelectorSession session, int vaultId) {
        if (!canManage(session, vaultId)) return false;
        return metadataService.setDefault(session.viewerUuid(), vaultId);
    }

    public SortResult handleSort(VaultSelectorSession session, int vaultId) {
        if (!canManage(session, vaultId) || !vaultManager.getConfig().organization.sortEnabled) {
            return SortResult.empty();
        }
        return vaultManager.sortVault(session.viewerUuid(), vaultId);
    }

    public DepositMatchingResult handleDeposit(VaultSelectorSession session, int vaultId) {
        if (!canManage(session, vaultId) || !vaultManager.getConfig().organization.depositMatchingEnabled) {
            return DepositMatchingResult.empty();
        }
        return vaultManager.depositSimilar(session.viewerUuid(), vaultId);
    }

    public boolean isActive(VaultSelectorSession session) {
        return session != null && sessions.get(session.viewerUuid()) == session;
    }

    public void dismiss(VaultSelectorSession session) {
        if (session == null) return;
        sessions.remove(session.viewerUuid(), session);
    }

    public void dismissIfCurrent(VaultSelectorSession session, long pageToken) {
        if (session == null || !session.isCurrentPage(pageToken)) return;
        dismiss(session);
    }

    public void clearSessions() {
        sessions.clear();
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    private boolean canManage(VaultSelectorSession session, int vaultId) {
        return canSelect(session, vaultId);
    }

    private VaultSelectorSession createSession(
            UUID viewerUuid,
            int x,
            int y,
            int z,
            int rotationIndex,
            BlockType blockType,
            int vaultCount,
            String locale
    ) {
        List<VaultSummary> summaries = summaryService.buildSummaries(viewerUuid);
        Map<Integer, VaultMetadata> metadata = metadataService.getAll(viewerUuid);
        return new VaultSelectorSession(
                viewerUuid,
                x,
                y,
                z,
                rotationIndex,
                blockType,
                vaultCount,
                locale,
                vaultManager.getVaultNames(viewerUuid),
                summaries,
                metadata
        );
    }

    @SuppressWarnings("removal")
    private UUID playerUuid(Player player) {
        return player == null ? null : player.getUuid();
    }
}

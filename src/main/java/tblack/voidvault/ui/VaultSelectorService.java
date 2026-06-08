package tblack.voidvault.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.voidvault.i18n.I18n;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.storage.VaultManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VaultSelectorService {
    private final VaultManager vaultManager;
    private final Map<UUID, VaultSelectorSession> sessions = new ConcurrentHashMap<>();

    public VaultSelectorService(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
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

        Ref ref = viewer.getReference();
        if (ref == null) return;

        Store store = ref.getStore();
        String locale = I18n.localeFromPlayerRef(playerRef);
        Map<Integer, String> names = vaultManager.getVaultNames(viewerUuid);
        VaultSelectorSession session = new VaultSelectorSession(viewerUuid, x, y, z, rotationIndex, blockType, vaultCount, locale, names);
        sessions.put(viewerUuid, session);
        viewer.getPageManager().openCustomPage(ref, store, new VaultSelectorPage(playerRef, this, session));
    }

    public boolean canSelect(VaultSelectorSession session, int vaultId) {
        if (session == null || vaultId < 1) return false;
        if (!isActive(session)) return false;
        return vaultManager.canAccessVault(session.viewerUuid(), vaultId);
    }

    public void openSelected(Ref<EntityStore> ref, Store<EntityStore> store, VaultSelectorSession session, int vaultId) {
        if (ref == null || store == null || session == null || vaultId < 1) return;

        Player viewer = store.getComponent(ref, Player.getComponentType());
        if (viewer == null) return;

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
    }

    public boolean isActive(VaultSelectorSession session) {
        if (session == null) return false;
        return sessions.get(session.viewerUuid()) == session;
    }

    public void dismiss(VaultSelectorSession session) {
        if (session == null) return;
        sessions.remove(session.viewerUuid(), session);
    }

    public void clearSessions() {
        sessions.clear();
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    @SuppressWarnings("removal")
    private UUID playerUuid(Player player) {
        return player == null ? null : player.getUuid();
    }
}

package tblack.voidvault.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import tblack.voidvault.storage.VaultManager;

import java.lang.reflect.Method;

public class VoidVaultUseBlockSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final String VOID_VAULT_BLOCK_ID = "VoidVault";
    private static final String OPEN_WINDOW_STATE = "OpenWindow";
    private static final String OPEN_SOUND_EVENT_ID = "SFX_Chest_VoidVault_Open";

    private final VaultManager manager;

    public VoidVaultUseBlockSystem(VaultManager manager) {
        super(UseBlockEvent.Pre.class);
        this.manager = manager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void handle(
            int entityIndex,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            UseBlockEvent.Pre event
    ) {
        if (event.getInteractionType() != InteractionType.Use) return;

        BlockType blockType = event.getBlockType();
        if (!isVoidVault(blockType)) return;

        Player player = resolvePlayer(entityIndex, store, event);
        if (player == null) return;

        Vector3i target = event.getTargetBlock();
        if (target == null) return;

        int rotationIndex = 0;
        manager.openVaultFromBlock(player, target.x, target.y, target.z, rotationIndex, blockType);
        playOpenSound(player, blockType, target, commandBuffer);
        event.setCancelled(true);
    }

    private boolean isVoidVault(BlockType blockType) {
        if (blockType == null) return false;

        String id = blockType.getId();
        if (id == null || id.isBlank()) return false;

        return id.contains(VOID_VAULT_BLOCK_ID);
    }

    private Player resolvePlayer(int entityIndex, Store<EntityStore> store, UseBlockEvent.Pre event) {
        Player player = resolvePlayerFromContext(store, event);
        if (player != null) return player;

        try {
            Ref ref = new Ref(store, entityIndex);
            return store.getComponent(ref, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Player resolvePlayerFromContext(Store<EntityStore> store, UseBlockEvent.Pre event) {
        try {
            InteractionContext context = event.getContext();
            if (context == null || context.getOwningEntity() == null) return null;

            Ref ref = context.getOwningEntity();
            return store.getComponent(ref, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void playOpenSound(
            Player player,
            BlockType blockType,
            Vector3i target,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        if (player == null || target == null || commandBuffer == null) return;

        int soundEventIndex = resolveOpenSoundEventIndex(blockType);
        if (soundEventIndex == 0) return;

        Ref playerRef = player.getReference();
        if (playerRef == null) return;

        double x = target.x + 0.5D;
        double y = target.y + 0.5D;
        double z = target.z + 0.5D;

        try {
            SoundUtil.playSoundEvent3dToPlayer(playerRef, soundEventIndex, SoundCategory.UI, x, y, z, commandBuffer);
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to play open sound: " + exception.getMessage());
        }
    }

    private int resolveOpenSoundEventIndex(BlockType blockType) {
        int blockStateSoundIndex = resolveBlockStateSoundEventIndex(blockType);
        if (blockStateSoundIndex != 0) return blockStateSoundIndex;

        return resolveSoundEventIndexById(OPEN_SOUND_EVENT_ID);
    }

    private int resolveBlockStateSoundEventIndex(BlockType blockType) {
        if (blockType == null) return 0;

        BlockType openState = blockType.getBlockForState(OPEN_WINDOW_STATE);
        if (openState == null) return 0;

        return openState.getInteractionSoundEventIndex();
    }

    private int resolveSoundEventIndexById(String soundEventId) {
        if (soundEventId == null || soundEventId.isBlank()) return 0;

        String[] soundEventClasses = {
                "com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent",
                "com.hypixel.hytale.server.core.asset.type.soundevent.SoundEvent",
                "com.hypixel.hytale.server.core.asset.type.sound.config.SoundEvent"
        };

        for (String className : soundEventClasses) {
            int index = resolveSoundEventIndexFromClass(className, soundEventId);
            if (index != 0) return index;
        }

        return 0;
    }

    private int resolveSoundEventIndexFromClass(String className, String soundEventId) {
        try {
            Class<?> soundEventClass = Class.forName(className);
            Object assetMap = soundEventClass.getMethod("getAssetMap").invoke(null);
            Method getIndex = assetMap.getClass().getMethod("getIndex", String.class);
            Object index = getIndex.invoke(assetMap, soundEventId);

            if (index instanceof Integer value) return value;
            return 0;
        } catch (Exception ignored) {
            return 0;
        }
    }
}

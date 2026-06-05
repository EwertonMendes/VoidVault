package dev.voidvault.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.voidvault.storage.VaultManager;

public class VoidVaultUseBlockSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
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
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, UseBlockEvent.Pre event) {
        if (event.getInteractionType() != InteractionType.Use) return;

        BlockType blockType = event.getBlockType();
        String id = blockType == null ? null : blockType.getId();
        if (id == null || !id.contains("VoidVault")) return;

        Player player = resolvePlayer(entityIndex, store, event);
        if (player == null) return;

        Vector3i target = event.getTargetBlock();
        int rotationIndex = 0;
        manager.openVault(player, target.x, target.y, target.z, rotationIndex, blockType);
        event.setCancelled(true);
    }

    private Player resolvePlayer(int entityIndex, Store<EntityStore> store, UseBlockEvent.Pre event) {
        try {
            InteractionContext context = event.getContext();
            if (context != null && context.getOwningEntity() != null) {
                Ref ref = context.getOwningEntity();
                return store.getComponent(ref, Player.getComponentType());
            }
        } catch (Exception ignored) {
        }

        try {
            Ref ref = new Ref(store, entityIndex);
            return store.getComponent(ref, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }
}

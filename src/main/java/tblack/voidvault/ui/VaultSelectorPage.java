package tblack.voidvault.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.voidvault.i18n.I18n;

import javax.annotation.Nonnull;

public class VaultSelectorPage extends InteractiveCustomUIPage<VaultSelectorPage.SelectorEventData> {
    private static final String LAYOUT = "VoidVault/VaultSelector.ui";
    private static final int PAGE_SIZE = 10;

    private final VaultSelectorService service;
    private final VaultSelectorSession session;
    private int page;

    public VaultSelectorPage(@Nonnull PlayerRef playerRef, VaultSelectorService service, VaultSelectorSession session) {
        super(playerRef, CustomPageLifetime.CanDismiss, SelectorEventData.CODEC);
        this.service = service;
        this.session = session;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        bindEvents(events);
        render(commands);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, SelectorEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data == null || data.action == null || data.action.isBlank()) {
            sendUpdate();
            return;
        }

        String action = data.action;
        if ("close".equals(action)) {
            service.dismiss(session);
            close();
            return;
        }

        if ("previous".equals(action)) {
            page = Math.max(0, page - 1);
            rebuild();
            return;
        }

        if ("next".equals(action)) {
            page = Math.min(lastPage(), page + 1);
            rebuild();
            return;
        }

        if (!action.startsWith("slot:")) {
            sendUpdate();
            return;
        }

        int vaultId = vaultIdFromSlot(action);
        if (!service.canSelect(session, vaultId)) {
            sendUpdate();
            return;
        }

        service.dismiss(session);
        close();
        service.openSelected(ref, store, session, vaultId);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        service.dismiss(session);
        super.onDismiss(ref, store);
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PreviousButton", EventData.of("Action", "previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "next"), false);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#VaultButton" + slot, EventData.of("Action", "slot:" + slot), false);
        }
    }

    private void render(UICommandBuilder commands) {
        setStaticTexts(commands);
        setNavigationState(commands);
        renderSlots(commands);
    }

    private void setStaticTexts(UICommandBuilder commands) {
        commands.set("#Title.TextSpans", Message.raw(translate("ui.selector.title")));
        commands.set("#Subtitle.TextSpans", Message.raw(translate("ui.selector.subtitle")));
        commands.set("#RenameHint.TextSpans", Message.raw(translate("ui.selector.rename_hint")));
        commands.set("#PageLabel.TextSpans", Message.raw(translate("ui.selector.page_with_numbers", page + 1, lastPage() + 1)));
        commands.set("#CloseButton.TextSpans", Message.raw(translate("ui.selector.cancel")));
    }

    private void setNavigationState(UICommandBuilder commands) {
        commands.set("#PreviousButton.Disabled", page <= 0);
        commands.set("#NextButton.Disabled", page >= lastPage());
    }

    private void renderSlots(UICommandBuilder commands) {
        int start = page * PAGE_SIZE + 1;
        int vaultCount = Math.max(1, session.vaultCount());
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int vaultId = start + slot;
            renderSlot(commands, slot, vaultId, vaultId <= vaultCount);
        }
    }

    private void renderSlot(UICommandBuilder commands, int slot, int vaultId, boolean visible) {
        String selector = "#VaultButton" + slot;
        commands.set(selector + ".Visible", visible);
        commands.set(selector + ".Disabled", !visible);
        commands.set(selector + ".TextSpans", Message.raw(visible ? vaultText(vaultId) : ""));
    }

    private String vaultText(int vaultId) {
        String baseName = translate("ui.selector.vault_prefix") + " " + vaultId;
        String customName = session.vaultName(vaultId);
        if (customName == null || customName.isBlank()) {
            return baseName;
        }
        return baseName + "\n" + customName;
    }

    private int lastPage() {
        return Math.max(0, (Math.max(1, session.vaultCount()) - 1) / PAGE_SIZE);
    }

    private int vaultIdFromSlot(String action) {
        try {
            int slot = Integer.parseInt(action.substring("slot:".length()));
            if (slot < 0 || slot >= PAGE_SIZE) return -1;
            return page * PAGE_SIZE + slot + 1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String translate(String key, Object... args) {
        return I18n.translate(session.locale(), key, args);
    }

    public static class SelectorEventData {
        public static final BuilderCodec<SelectorEventData> CODEC = BuilderCodec.builder(SelectorEventData.class, SelectorEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .build();

        public String action;
    }
}

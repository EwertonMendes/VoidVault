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
import tblack.voidvault.model.VaultSummary;

import javax.annotation.Nonnull;
import java.util.List;

public class VaultSelectorPage extends InteractiveCustomUIPage<VaultSelectorPage.SelectorEventData> {
    private static final String LAYOUT = "VoidVault/VaultSelector.ui";
    private static final int PAGE_SIZE = 6;

    private final VaultSelectorService service;
    private final VaultSelectorSession session;
    private final List<VaultSummary> summaries;
    private final long pageToken;
    private int page;

    public VaultSelectorPage(
            @Nonnull PlayerRef playerRef,
            VaultSelectorService service,
            VaultSelectorSession session,
            List<VaultSummary> summaries
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, SelectorEventData.CODEC);
        this.service = service;
        this.session = session;
        this.summaries = summaries == null ? List.of() : List.copyOf(summaries);
        this.pageToken = session.activatePage();
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        commands.append(LAYOUT);
        bindEvents(events);
        render(commands);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            SelectorEventData data
    ) {
        super.handleDataEvent(ref, store, data);
        if (!service.isActive(session) || !session.isCurrentPage(pageToken)) {
            close();
            return;
        }
        if (data == null || data.action == null || data.action.isBlank()) {
            sendUpdate();
            return;
        }

        switch (data.action) {
            case "close" -> {
                service.dismiss(session);
                close();
            }
            case "previous" -> {
                page = Math.max(0, page - 1);
                rebuild();
            }
            case "next" -> {
                page = Math.min(lastPage(), page + 1);
                rebuild();
            }
            default -> handleCardAction(ref, store, data.action);
        }
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        service.dismissIfCurrent(session, pageToken);
        super.onDismiss(ref, store);
    }

    private void handleCardAction(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        if (action.startsWith("open:")) {
            int vaultId = parseVaultId(action, "open:");
            if (!service.canSelect(session, vaultId)) {
                sendUpdate();
                return;
            }
            service.dismiss(session);
            close();
            service.openSelected(ref, store, session, vaultId);
            return;
        }

        if (action.startsWith("manage:")) {
            int vaultId = parseVaultId(action, "manage:");
            if (!service.canSelect(session, vaultId)) {
                sendUpdate();
                return;
            }
            if (!service.openManagement(ref, store, session, vaultId)) {
                sendUpdate();
            }
            return;
        }

        sendUpdate();
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PreviousButton", EventData.of("Action", "previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "next"), false);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#CardOpen" + slot, EventData.of("Action", "open:" + slot), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#CardManage" + slot, EventData.of("Action", "manage:" + slot), false);
        }
    }

    private void render(UICommandBuilder commands) {
        commands.set("#Title.TextSpans", Message.raw(translate("ui.selector.title")));
        commands.set("#Subtitle.TextSpans", Message.raw(translate("ui.selector.subtitle")));
        commands.set("#PageLabel.TextSpans", Message.raw(translate("ui.selector.page_with_numbers", page + 1, lastPage() + 1)));
        commands.set("#CloseButton.TextSpans", Message.raw(translate("ui.selector.cancel")));
        commands.set("#PreviousButton.Disabled", page <= 0);
        commands.set("#NextButton.Disabled", page >= lastPage());

        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            renderCard(commands, slot, index < summaries.size() ? summaries.get(index) : null);
        }
    }

    private void renderCard(UICommandBuilder commands, int slot, VaultSummary summary) {
        String card = "#Card" + slot;
        boolean visible = summary != null;
        commands.set(card + ".Visible", visible);
        commands.set("#CardOpen" + slot + ".Disabled", !visible || summary.capacity() <= 0);
        commands.set("#CardManage" + slot + ".Disabled", !visible || summary.capacity() <= 0);
        if (!visible) return;

        String number = translate("ui.selector.vault_prefix") + " " + summary.vaultId();
        boolean hasCustomName = summary.hasCustomName();
        String color = VaultColor.mainColor(summary.colorId());
        String accent = VaultColor.accentColor(summary.colorId());

        commands.set(card + ".OutlineColor", color);
        commands.set("#CardAccent" + slot + ".Background", color);
        commands.set("#CardIconBadge" + slot + ".OutlineColor", accent);
        String resolvedIconId = service.resolveIconItemId(summary.iconId());
        boolean hasIcon = resolvedIconId != null && !resolvedIconId.isBlank();
        commands.set("#CardIcon" + slot + ".Visible", hasIcon);
        commands.set("#CardIconFallback" + slot + ".Visible", !hasIcon);
        if (hasIcon) {
            commands.set("#CardIcon" + slot + ".ItemId", resolvedIconId);
            commands.set("#CardIcon" + slot + ".ShowItemTooltip", false);
        }
        commands.set("#CardName" + slot + ".TextSpans", Message.raw(hasCustomName ? summary.displayName() : number));
        commands.set("#CardNumber" + slot + ".Visible", hasCustomName);
        if (hasCustomName) {
            commands.set("#CardNumber" + slot + ".TextSpans", Message.raw(number));
        }

        commands.set("#CardDefaultBadge" + slot + ".Visible", summary.defaultVault());
        commands.set("#CardDefaultBadge" + slot + ".TextSpans", Message.raw(translate("ui.selector.default")));
        commands.set("#CardFavoriteBadge" + slot + ".Visible", summary.favorite());

        String occupancy = summary.capacity() > 0
                ? summary.occupiedVisibleSlots() + " / " + summary.capacity() + " " + translate("ui.selector.slots")
                : translate("ui.selector.locked");
        commands.set("#CardOccupancy" + slot + ".TextSpans", Message.raw(occupancy));
        commands.set("#CardProgressBar" + slot + ".Bar", color);
        commands.set("#CardProgressBar" + slot + ".Value", (float) summary.occupancyRatio());

        commands.set("#CardOverflowBadge" + slot + ".Visible", summary.hasOverflow());
        if (summary.hasOverflow()) {
            String overflow = summary.overflowSlots() + " " + translate("ui.selector.overflow");
            commands.set("#CardOverflowBadge" + slot + ".TextSpans", Message.raw(overflow));
        }

        commands.set("#CardOpen" + slot + ".TextSpans", Message.raw(translate("ui.selector.open")));
        commands.set("#CardManage" + slot + ".TextSpans", Message.raw(translate("ui.selector.manage")));
    }

    private int lastPage() {
        return Math.max(0, (summaries.size() - 1) / PAGE_SIZE);
    }

    private int parseVaultId(String action, String prefix) {
        try {
            int slot = Integer.parseInt(action.substring(prefix.length()));
            if (slot < 0 || slot >= PAGE_SIZE) return -1;
            int index = page * PAGE_SIZE + slot;
            return index < summaries.size() ? summaries.get(index).vaultId() : -1;
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
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

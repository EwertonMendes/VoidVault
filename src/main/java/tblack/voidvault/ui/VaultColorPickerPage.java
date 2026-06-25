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
import tblack.voidvault.model.VaultMetadata;

import javax.annotation.Nonnull;

public final class VaultColorPickerPage extends InteractiveCustomUIPage<VaultColorPickerPage.ColorPickerEventData> {
    private static final String LAYOUT = "VoidVault/VaultColorPicker.ui";

    private final VaultSelectorService service;
    private final VaultSelectorSession session;
    private final int vaultId;
    private final long pageToken;

    private String draftColor;
    private String statusText = "";

    public VaultColorPickerPage(
            @Nonnull PlayerRef playerRef,
            VaultSelectorService service,
            VaultSelectorSession session,
            int vaultId
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, ColorPickerEventData.CODEC);
        this.service = service;
        this.session = session;
        this.vaultId = vaultId;
        VaultMetadata metadata = service.getMetadata(session, vaultId);
        this.draftColor = VaultColor.mainColor(metadata == null ? null : metadata.colorId());
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
            ColorPickerEventData data
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
            case "back" -> {
                if (!service.openManagement(ref, store, session, vaultId)) sendUpdate();
            }
            case "color_changed" -> updateDraft(data.colorValue);
            case "apply" -> applyAndReturn(ref, store);
            default -> sendUpdate();
        }
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        service.dismissIfCurrent(session, pageToken);
        super.onDismiss(ref, store);
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#CustomColorPicker",
                EventData.of("@CustomColorPicker", "#CustomColorPicker.Value")
                        .append("Action", "color_changed"),
                false
        );
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", event("back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyButton", event("apply"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", event("close"), false);
    }

    private EventData event(String action) {
        return EventData.of("Action", action);
    }

    private void updateDraft(String rawValue) {
        String normalized = VaultColor.normalizeCustomHex(rawValue);
        if (normalized == null) {
            statusText = translate("ui.manage.color.custom.invalid");
            sendUpdate();
            return;
        }

        if (normalized.equals(draftColor)) {
            sendUpdate();
            return;
        }

        draftColor = normalized;
        statusText = "";

        // Keep the visible "current color" preview synchronized without rebuilding the
        // page, querying metadata, or persisting intermediate drag values. The native
        // picker can emit many ValueChanged events, so this patch intentionally updates
        // only the two lightweight preview elements.
        UICommandBuilder updates = new UICommandBuilder();
        updates.set("#CurrentColorSwatch.Background", draftColor);
        updates.set("#CurrentColorValue.TextSpans", Message.raw(draftColor));
        sendUpdate(updates, false);
    }

    private void applyAndReturn(Ref<EntityStore> ref, Store<EntityStore> store) {
        String normalized = VaultColor.normalizeCustomHex(draftColor);
        if (normalized == null) {
            statusText = translate("ui.manage.color.custom.invalid");
            rebuild();
            return;
        }
        if (!service.handleColorChange(session, vaultId, normalized)) {
            statusText = translate("ui.manage.action.failed");
            rebuild();
            return;
        }
        if (!service.openManagement(ref, store, session, vaultId, translate("ui.manage.color.custom.saved"))) {
            statusText = translate("ui.manage.action.failed");
            rebuild();
        }
    }

    private void render(UICommandBuilder commands) {
        commands.set("#Panel.OutlineColor", draftColor);
        commands.set("#HeaderGlow.Background", draftColor);
        commands.set("#Title.TextSpans", Message.raw(translate("ui.color_picker.title")));
        commands.set("#Subtitle.TextSpans", Message.raw(translate("ui.color_picker.subtitle")));
        commands.set("#CurrentColorLabel.TextSpans", Message.raw(translate("ui.color_picker.current")));
        commands.set("#CurrentColorSwatch.Background", draftColor);
        commands.set("#CurrentColorValue.TextSpans", Message.raw(draftColor));
        commands.set("#CustomColorPicker.Value", draftColor);
        commands.set("#StatusLabel.TextSpans", Message.raw(statusText));
        commands.set("#BackButton.TextSpans", Message.raw(translate("ui.color_picker.back")));
        commands.set("#ApplyButton.TextSpans", Message.raw(translate("ui.color_picker.apply")));
        commands.set("#CloseButton.TextSpans", Message.raw(translate("ui.color_picker.close")));
    }

    private String translate(String key, Object... args) {
        return I18n.translate(session.locale(), key, args);
    }

    public static final class ColorPickerEventData {
        public static final BuilderCodec<ColorPickerEventData> CODEC = BuilderCodec.builder(ColorPickerEventData.class, ColorPickerEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@CustomColorPicker", Codec.STRING), (data, value) -> data.colorValue = value, data -> data.colorValue).add()
                .build();

        public String action = "";
        public String colorValue = "";
    }
}

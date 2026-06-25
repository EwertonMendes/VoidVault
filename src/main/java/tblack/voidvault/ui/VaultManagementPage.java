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
import tblack.voidvault.model.DepositMatchingResult;
import tblack.voidvault.model.SortResult;
import tblack.voidvault.model.VaultIconEntry;
import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.model.VaultSummary;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class VaultManagementPage extends InteractiveCustomUIPage<VaultManagementPage.ManagementEventData> {
    private static final String LAYOUT = "VoidVault/VaultManagement.ui";

    private final VaultSelectorService service;
    private final VaultSelectorSession session;
    private final int vaultId;
    private final List<String> presetColorIds;
    private final long pageToken;

    private VaultSummary summary;
    private VaultMetadata metadata;
    private int currentPresetIndex;
    private String statusText;
    private String renameDraft;
    private String customColorDraft;

    public VaultManagementPage(
            @Nonnull PlayerRef playerRef,
            VaultSelectorService service,
            VaultSelectorSession session,
            int vaultId,
            VaultSummary summary,
            VaultMetadata metadata
    ) {
        this(playerRef, service, session, vaultId, summary, metadata, "");
    }

    public VaultManagementPage(
            @Nonnull PlayerRef playerRef,
            VaultSelectorService service,
            VaultSelectorSession session,
            int vaultId,
            VaultSummary summary,
            VaultMetadata metadata,
            String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, ManagementEventData.CODEC);
        this.service = service;
        this.session = session;
        this.vaultId = vaultId;
        this.summary = summary;
        this.metadata = metadata == null ? VaultMetadata.empty(session.viewerUuid(), vaultId) : metadata;
        this.presetColorIds = createPresetColorIds();
        this.statusText = statusText == null ? "" : statusText;
        this.renameDraft = this.metadata.hasCustomName() ? this.metadata.displayName() : "";
        this.customColorDraft = VaultColor.mainColor(this.metadata.colorId());
        this.pageToken = session.activatePage();
        syncPresetIndex();
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
            ManagementEventData data
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
                if (!service.openSelectorFromManagement(ref, store, session)) sendUpdate();
            }
            case "open" -> {
                service.dismiss(session);
                close();
                service.openSelected(ref, store, session, vaultId);
            }
            case "rename_value_changed" -> {
                renameDraft = data.renameValue == null ? "" : data.renameValue;
                sendUpdate();
            }
            case "rename" -> saveName(renameDraft, "ui.manage.rename.success");
            case "rename_reset" -> saveName("reset", "ui.manage.rename.cleared");
            case "choose_icon" -> {
                if (!service.openIconPicker(ref, store, session, vaultId)) sendUpdate();
            }
            case "color_prev" -> changePresetColor(-1);
            case "color_next" -> changePresetColor(1);
            case "open_color_picker" -> {
                if (!service.openColorPicker(ref, store, session, vaultId)) sendUpdate();
            }
            case "custom_color_hex_value_changed" -> {
                customColorDraft = data.customColorHexValue == null ? "" : data.customColorHexValue.trim();
                sendUpdate();
            }
            case "custom_color_hex_validating" -> {
                customColorDraft = data.customColorHexValue == null ? "" : data.customColorHexValue.trim();
                applyCustomColor();
            }
            case "custom_color_apply" -> applyCustomColor();
            case "favorite" -> {
                boolean saved = service.handleFavoriteToggle(session, vaultId, !metadata.favorite());
                statusText = saved ? "" : translate("ui.manage.action.failed");
                refreshAndRebuild();
            }
            case "default_vault" -> {
                boolean saved = service.handleDefaultSet(session, vaultId);
                statusText = saved ? translate("ui.manage.default.success") : translate("ui.manage.action.failed");
                refreshAndRebuild();
            }
            case "sort" -> {
                SortResult result = service.handleSort(session, vaultId);
                statusText = translate(result.changed() ? "ui.manage.sort.success" : "ui.manage.sort.empty");
                refreshAndRebuild();
            }
            case "deposit" -> {
                DepositMatchingResult result = service.handleDeposit(session, vaultId);
                if (result.hasMovedItems()) {
                    statusText = translate("ui.manage.deposit.success", result.movedItemCount());
                } else if (result.vaultWasFull()) {
                    statusText = translate("ui.manage.deposit.full");
                } else {
                    statusText = translate("ui.manage.deposit.none");
                }
                refreshAndRebuild();
            }
            default -> sendUpdate();
        }
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        service.dismissIfCurrent(session, pageToken);
        super.onDismiss(ref, store);
    }

    private List<String> createPresetColorIds() {
        List<String> ids = new ArrayList<>();
        ids.add("");
        for (String id : VaultColor.getAllIds()) {
            if (!VaultColor.DEFAULT_ID.equals(id)) ids.add(id);
        }
        return ids;
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", event("close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", event("back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#OpenButton", event("open"), false);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#RenameField",
                EventData.of("@RenameField", "#RenameField.Value").append("Action", "rename_value_changed"),
                false
        );
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RenameSaveButton", event("rename"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RenameResetButton", event("rename_reset"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ChooseIconButton", event("choose_icon"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ColorPrev", event("color_prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ColorNext", event("color_next"), false);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#CustomColorHexField",
                EventData.of("@CustomColorHexField", "#CustomColorHexField.Value")
                        .append("Action", "custom_color_hex_value_changed"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Validating,
                "#CustomColorHexField",
                EventData.of("@CustomColorHexField", "#CustomColorHexField.Value")
                        .append("Action", "custom_color_hex_validating"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CustomColorApplyButton",
                event("custom_color_apply"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#OpenColorPickerButton",
                event("open_color_picker"),
                false
        );
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FavoriteButton", event("favorite"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultButton", event("default_vault"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SortButton", event("sort"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositButton", event("deposit"), false);
    }

    private EventData event(String action) {
        return EventData.of("Action", action);
    }

    private void saveName(String value, String successKey) {
        boolean saved = service.handleRename(session, vaultId, value);
        statusText = translate(saved ? successKey : "ui.manage.action.failed");
        refreshState();
        if (saved) syncRenameDraftFromMetadata();
        rebuild();
    }

    private void changePresetColor(int direction) {
        currentPresetIndex = Math.floorMod(currentPresetIndex + direction, presetColorIds.size());
        String selected = presetColorIds.get(currentPresetIndex);
        String normalizedSelection = selected.isEmpty() ? null : selected;
        boolean saved = service.handleColorChange(session, vaultId, normalizedSelection);
        if (!saved) {
            statusText = translate("ui.manage.action.failed");
            sendStatusUpdate();
            return;
        }

        metadata = metadata.withColor(normalizedSelection);
        customColorDraft = VaultColor.mainColor(normalizedSelection);
        statusText = translate("ui.manage.color.saved");
        sendColorStateUpdate();
    }

    private void applyCustomColor() {
        String normalized = VaultColor.normalizeCustomHex(customColorDraft);
        if (normalized == null) {
            statusText = translate("ui.manage.color.custom.invalid");
            sendStatusUpdate();
            return;
        }

        boolean saved = service.handleColorChange(session, vaultId, normalized);
        if (!saved) {
            statusText = translate("ui.manage.action.failed");
            sendStatusUpdate();
            return;
        }

        metadata = metadata.withColor(normalized);
        customColorDraft = normalized;
        currentPresetIndex = 0;
        statusText = translate("ui.manage.color.custom.saved");
        sendColorStateUpdate();
    }

    private void sendColorStateUpdate() {
        String storedColor = metadata.colorId();
        String mainColor = VaultColor.mainColor(storedColor);
        String accentColor = VaultColor.accentColor(storedColor);

        UICommandBuilder updates = new UICommandBuilder();
        updates.set("#Panel.OutlineColor", mainColor);
        updates.set("#HeaderGlow.Background", mainColor);
        updates.set("#OccupancyBar.Bar", mainColor);
        updates.set("#IconPreviewPanel.OutlineColor", accentColor);
        updates.set("#ColorSwatch.Background", mainColor);
        updates.set("#ColorPreview.TextSpans", Message.raw(resolveColorName(storedColor)));
        updates.set("#CustomColorHexField.Value", customColorDraft);
        updates.set("#StatusLabel.TextSpans", Message.raw(statusText));
        sendUpdate(updates, false);
    }

    private void sendStatusUpdate() {
        UICommandBuilder updates = new UICommandBuilder();
        updates.set("#StatusLabel.TextSpans", Message.raw(statusText));
        sendUpdate(updates, false);
    }

    private void refreshAndRebuild() {
        refreshState();
        rebuild();
    }

    private void refreshState() {
        VaultSummary refreshedSummary = service.getSummary(session, vaultId);
        VaultMetadata refreshedMetadata = service.getMetadata(session, vaultId);
        if (refreshedSummary != null) summary = refreshedSummary;
        if (refreshedMetadata != null) metadata = refreshedMetadata;
        syncPresetIndex();
    }

    private void syncPresetIndex() {
        String storedColor = metadata.colorId();
        if (storedColor == null || VaultColor.DEFAULT_ID.equals(storedColor) || VaultColor.isCustomColor(storedColor)) {
            currentPresetIndex = 0;
            return;
        }
        int found = presetColorIds.indexOf(storedColor);
        currentPresetIndex = Math.max(0, found);
    }

    private void syncRenameDraftFromMetadata() {
        renameDraft = metadata.hasCustomName() ? metadata.displayName() : "";
    }

    private void render(UICommandBuilder commands) {
        String storedColor = metadata.colorId();
        String mainColor = VaultColor.mainColor(storedColor);
        String accentColor = VaultColor.accentColor(storedColor);
        String resolvedIconId = service.resolveIconItemId(metadata.iconId());
        boolean hasResolvedIcon = resolvedIconId != null && !resolvedIconId.isBlank();
        boolean missingStoredIcon = service.isStoredIconMissing(metadata.iconId());
        VaultIconEntry selectedIcon = missingStoredIcon || metadata.iconId() == null
                ? null
                : service.describeIcon(metadata.iconId(), session.locale());

        commands.set("#Panel.OutlineColor", mainColor);
        commands.set("#HeaderGlow.Background", mainColor);
        commands.set("#Title.TextSpans", Message.raw(translate("ui.manage.title")));
        commands.set("#VaultInfo.TextSpans", Message.raw(translate("ui.selector.vault_prefix") + " " + vaultId));

        String occupancy = summary.capacity() > 0
                ? summary.occupiedVisibleSlots() + " / " + summary.capacity() + " " + translate("ui.selector.slots")
                : translate("ui.selector.locked");
        commands.set("#OccupancyLabel.TextSpans", Message.raw(occupancy));
        commands.set("#OccupancyBar.Bar", mainColor);
        commands.set("#OccupancyBar.Value", (float) summary.occupancyRatio());
        commands.set("#OverflowLabel.Visible", summary.hasOverflow());
        if (summary.hasOverflow()) {
            commands.set("#OverflowLabel.TextSpans", Message.raw(summary.overflowSlots() + " " + translate("ui.selector.overflow")));
        }

        commands.set("#RenameLabel.TextSpans", Message.raw(translate("ui.manage.name")));
        commands.set("#RenameField.PlaceholderText", translate("ui.manage.name_placeholder"));
        commands.set("#RenameField.Value", renameDraft);
        commands.set("#RenameSaveButton.TextSpans", Message.raw(translate("ui.manage.save_name")));
        commands.set("#RenameResetButton.TextSpans", Message.raw(translate("ui.manage.reset_name")));

        commands.set("#IconLabel.TextSpans", Message.raw(translate("ui.manage.icon")));
        commands.set("#SelectedIcon.Visible", hasResolvedIcon);
        if (hasResolvedIcon) {
            commands.set("#SelectedIcon.ItemId", resolvedIconId);
            commands.set("#SelectedIcon.ShowItemTooltip", false);
        }
        String selectedIconName;
        if (metadata.iconId() == null) {
            selectedIconName = translate("ui.manage.icon.default");
        } else if (selectedIcon != null && selectedIcon.hasDisplayName()) {
            selectedIconName = selectedIcon.displayName();
        } else {
            selectedIconName = translate("ui.icon_picker.unknown_item");
        }
        commands.set("#SelectedIconName.TextSpans", Message.raw(selectedIconName));
        commands.set("#MissingIconLabel.Visible", missingStoredIcon);
        if (missingStoredIcon) {
            commands.set("#MissingIconLabel.TextSpans", Message.raw(translate("ui.manage.icon.missing")));
        }
        commands.set("#IconPreviewPanel.OutlineColor", accentColor);
        commands.set("#ChooseIconButton.TextSpans", Message.raw(translate("ui.manage.choose_icon")));

        commands.set("#ColorLabel.TextSpans", Message.raw(translate("ui.manage.color")));
        commands.set("#PresetColorLabel.TextSpans", Message.raw(translate("ui.manage.color.preset")));
        commands.set("#CustomColorLabel.TextSpans", Message.raw(translate("ui.manage.color.custom_label")));
        commands.set("#ColorPreview.TextSpans", Message.raw(resolveColorName(storedColor)));
        commands.set("#ColorSwatch.Background", mainColor);
        commands.set("#CustomColorHexField.Value", customColorDraft);
        commands.set("#CustomColorHexField.PlaceholderText", "#RRGGBB");
        commands.set("#CustomColorApplyButton.TextSpans", Message.raw(translate("ui.manage.color.apply")));
        commands.set("#OpenColorPickerButton.TextSpans", Message.raw(translate("ui.manage.color.open_picker")));

        commands.set("#FavoriteButton.TextSpans", Message.raw(metadata.favorite()
                ? translate("ui.manage.remove_favorite")
                : translate("ui.manage.add_favorite")));
        commands.set("#DefaultButton.TextSpans", Message.raw(summary.defaultVault()
                ? translate("ui.manage.is_default")
                : translate("ui.manage.set_default")));
        commands.set("#DefaultButton.Disabled", summary.defaultVault());

        commands.set("#SortButton.TextSpans", Message.raw(translate("ui.manage.sort")));
        commands.set("#DepositButton.TextSpans", Message.raw(translate("ui.manage.deposit")));
        commands.set("#SortButton.Disabled", !service.getVaultManager().getConfig().organization.sortEnabled);
        commands.set("#DepositButton.Disabled", !service.getVaultManager().getConfig().organization.depositMatchingEnabled);

        commands.set("#StatusLabel.TextSpans", Message.raw(statusText));
        commands.set("#BackButton.TextSpans", Message.raw(translate("ui.manage.back")));
        commands.set("#OpenButton.TextSpans", Message.raw(translate("ui.manage.open")));
        commands.set("#CloseButton.TextSpans", Message.raw(translate("ui.manage.close")));
    }

    private String resolveColorName(String storedColor) {
        if (storedColor == null || VaultColor.DEFAULT_ID.equals(storedColor)) {
            return translate("ui.manage.color.none");
        }
        if (VaultColor.isCustomColor(storedColor)) {
            return translate("ui.manage.color.custom") + " " + VaultColor.mainColor(storedColor);
        }
        return translate("ui.manage.color." + storedColor);
    }

    private String translate(String key, Object... args) {
        return I18n.translate(session.locale(), key, args);
    }

    public static class ManagementEventData {
        public static final BuilderCodec<ManagementEventData> CODEC = BuilderCodec.builder(ManagementEventData.class, ManagementEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@RenameField", Codec.STRING), (data, value) -> data.renameValue = value, data -> data.renameValue).add()
                .append(new KeyedCodec<>("@CustomColorHexField", Codec.STRING), (data, value) -> data.customColorHexValue = value, data -> data.customColorHexValue).add()
                .build();

        public String action = "";
        public String renameValue = "";
        public String customColorHexValue = "";
    }
}

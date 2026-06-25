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
import tblack.voidvault.model.VaultIconEntry;

import javax.annotation.Nonnull;
import java.util.List;

public final class VaultIconPickerPage extends InteractiveCustomUIPage<VaultIconPickerPage.IconPickerEventData> {
    private static final String LAYOUT = "VoidVault/VaultIconPicker.ui";
    private static final int PAGE_SIZE = 24;

    private final VaultSelectorService service;
    private final VaultSelectorSession session;
    private final int vaultId;
    private final long pageToken;

    private String query = "";
    private String searchDraft = "";
    private String statusText = "";
    private List<VaultIconEntry> matches;
    private int page;

    public VaultIconPickerPage(
            @Nonnull PlayerRef playerRef,
            VaultSelectorService service,
            VaultSelectorSession session,
            int vaultId
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, IconPickerEventData.CODEC);
        this.service = service;
        this.session = session;
        this.vaultId = vaultId;
        this.matches = service.searchIcons("", session.locale());
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
            IconPickerEventData data
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
            case "search_value_changed" -> {
                searchDraft = data.searchValue == null ? "" : data.searchValue;
                sendUpdate();
            }
            case "search" -> applySearch(data.searchValue == null ? searchDraft : data.searchValue);
            case "clear_search" -> applySearch("");
            case "previous" -> {
                page = Math.max(0, page - 1);
                rebuild();
            }
            case "next" -> {
                page = Math.min(lastPage(), page + 1);
                rebuild();
            }
            case "default_icon" -> saveAndReturn(ref, store, null, "ui.manage.icon.cleared");
            default -> {
                if (data.action.startsWith("select:")) {
                    select(ref, store, data.action);
                } else {
                    sendUpdate();
                }
            }
        }
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        service.dismissIfCurrent(session, pageToken);
        super.onDismiss(ref, store);
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", event("close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", event("back"), false);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchField",
                EventData.of("@SearchField", "#SearchField.Value").append("Action", "search_value_changed"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Validating,
                "#SearchField",
                EventData.of("@SearchField", "#SearchField.Value").append("Action", "search"),
                false
        );
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SearchButton", event("search"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearSearchButton", event("clear_search"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PreviousButton", event("previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", event("next"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DefaultIconButton", event("default_icon"), false);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#IconButton" + slot,
                    event("select:" + slot),
                    false
            );
        }
    }

    private EventData event(String action) {
        return EventData.of("Action", action);
    }

    private void applySearch(String rawQuery) {
        query = rawQuery == null ? "" : rawQuery.trim();
        searchDraft = query;
        matches = service.searchIcons(query, session.locale());
        page = 0;
        statusText = "";
        rebuild();
    }

    private void select(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        int slot;
        try {
            slot = Integer.parseInt(action.substring("select:".length()));
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            sendUpdate();
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            sendUpdate();
            return;
        }

        int index = page * PAGE_SIZE + slot;
        if (index < 0 || index >= matches.size()) {
            sendUpdate();
            return;
        }
        saveAndReturn(ref, store, matches.get(index).itemId(), "ui.manage.icon.saved");
    }

    private void saveAndReturn(
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            String itemId,
            String successKey
    ) {
        if (!service.handleIconChange(session, vaultId, itemId)) {
            statusText = translate("ui.manage.action.failed");
            rebuild();
            return;
        }
        if (!service.openManagement(ref, store, session, vaultId, translate(successKey))) {
            statusText = translate("ui.manage.action.failed");
            rebuild();
        }
    }

    private void render(UICommandBuilder commands) {
        commands.set("#Title.TextSpans", Message.raw(translate("ui.icon_picker.title")));
        commands.set("#Subtitle.TextSpans", Message.raw(translate("ui.icon_picker.subtitle")));
        commands.set("#SearchField.Value", searchDraft);
        commands.set("#SearchField.PlaceholderText", translate("ui.icon_picker.search_placeholder"));
        commands.set("#SearchButton.TextSpans", Message.raw(translate("ui.icon_picker.search")));
        commands.set("#ClearSearchButton.TextSpans", Message.raw(translate("ui.icon_picker.clear_search")));
        commands.set("#DefaultIconButton.TextSpans", Message.raw(translate("ui.icon_picker.use_default")));
        commands.set("#ResultLabel.TextSpans", Message.raw(translate("ui.icon_picker.results", matches.size())));
        commands.set("#PageLabel.TextSpans", Message.raw(translate("ui.icon_picker.page_with_numbers", page + 1, lastPage() + 1)));
        commands.set("#PreviousButton.Disabled", page <= 0);
        commands.set("#NextButton.Disabled", page >= lastPage());
        commands.set("#BackButton.TextSpans", Message.raw(translate("ui.icon_picker.back")));
        commands.set("#CloseButton.TextSpans", Message.raw(translate("ui.icon_picker.close")));
        commands.set("#StatusLabel.TextSpans", Message.raw(statusText));
        commands.set("#NoResultsLabel.Visible", matches.isEmpty());
        if (matches.isEmpty()) {
            commands.set("#NoResultsLabel.TextSpans", Message.raw(translate("ui.icon_picker.no_results")));
        }

        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            boolean visible = index < matches.size();
            commands.set("#IconCell" + slot + ".Visible", visible);
            commands.set("#IconButton" + slot + ".Disabled", !visible);
            if (!visible) continue;

            VaultIconEntry entry = matches.get(index);
            commands.set("#IconSlot" + slot + ".ItemId", entry.itemId());
            commands.set("#IconSlot" + slot + ".ShowItemTooltip", false);
            String name = entry.hasDisplayName() ? entry.displayName() : translate("ui.icon_picker.unknown_item");
            commands.set("#IconName" + slot + ".TextSpans", Message.raw(name));
        }
    }

    private int lastPage() {
        return Math.max(0, (matches.size() - 1) / PAGE_SIZE);
    }

    private String translate(String key, Object... args) {
        return I18n.translate(session.locale(), key, args);
    }

    public static final class IconPickerEventData {
        public static final BuilderCodec<IconPickerEventData> CODEC = BuilderCodec.builder(IconPickerEventData.class, IconPickerEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@SearchField", Codec.STRING), (data, value) -> data.searchValue = value, data -> data.searchValue).add()
                .build();

        public String action = "";
        public String searchValue;
    }
}

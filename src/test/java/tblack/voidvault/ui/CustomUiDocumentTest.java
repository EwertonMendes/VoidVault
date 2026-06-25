package tblack.voidvault.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CustomUiDocumentTest {

    @Test
    void selectorUsesNonInteractiveNativeItemIconsAndBottomSpacer() throws IOException {
        String document = read("Common/UI/Custom/VoidVault/VaultSelector.ui");

        assertBalanced(document);
        assertTrue(document.contains("ItemIcon #CardIcon0"));
        assertTrue(document.contains("ShowItemTooltip: false;"));
        assertTrue(document.contains("HitTestVisible: false;"));
        assertTrue(document.contains("Group #FooterSpacer { FlexWeight: 1; }"));
        for (int index = 0; index < 6; index++) {
            assertTrue(document.contains("Group #CardUpperSpacer" + index + " { FlexWeight: 1; }"));
            assertTrue(document.contains("Group #CardLowerSpacer" + index + " { FlexWeight: 1; }"));
        }
        assertTrue(document.contains("TextButton #CloseButton"));
        assertFalse(document.contains("ShowItemTooltip: true;"));
        assertFalse(document.contains("BorderColor:"));
        assertFalse(document.contains("BorderWidth:"));
        assertFalse(document.matches("(?s).*Anchor\\s*:\\s*\\([^)]*FlexWeight.*"));
        assertFalse(document.contains("HorizontalAlignment: Left"));
        assertFalse(document.contains("HorizontalAlignment: Right"));
    }

    @Test
    void managementKeepsNativePickerOutOfTheInitialPage() throws IOException {
        String document = read("Common/UI/Custom/VoidVault/VaultManagement.ui");

        assertBalanced(document);
        assertTrue(document.startsWith("$Common = \"../Common.ui\";"));
        assertTrue(document.contains("TextField #RenameField"));
        assertTrue(document.contains("ItemIcon #SelectedIcon"));
        assertTrue(document.contains("ShowItemTooltip: false;"));
        assertTrue(document.contains("HitTestVisible: false;"));
        assertTrue(document.contains("Label #SelectedIconName"));
        assertTrue(document.contains("Group #FooterSpacer { FlexWeight: 1; }"));
        assertTrue(document.contains("TextButton #ChooseIconButton"));
        assertTrue(document.contains("TextField #CustomColorHexField"));
        assertTrue(document.contains("TextButton #CustomColorApplyButton"));
        assertTrue(document.contains("TextButton #OpenColorPickerButton"));
        assertTrue(document.contains("Group #SummaryPanel"));
        assertTrue(document.contains("Anchor: (Height: 62, Bottom: 12);"));
        assertTrue(document.contains("Background: #08040D;"));
        assertFalse(document.contains("ColorPicker #"));
        assertFalse(document.contains("ItemPreviewComponent"));
        assertFalse(document.contains("SelectedIconId"));
        assertFalse(document.contains("BorderColor:"));
        assertFalse(document.contains("BorderWidth:"));
        assertFalse(document.matches("(?s).*TextField #RenameField\\s*\\{[^}]*\\bText\\s*:.*"));
    }

    @Test
    void dedicatedColorPickerUsesDocumentedDefaultStyleAndSafeDimensions() throws IOException {
        String document = read("Common/UI/Custom/VoidVault/VaultColorPicker.ui");

        assertBalanced(document);
        assertTrue(document.startsWith("$Common = \"../Common.ui\";"));
        assertTrue(document.contains("ColorPicker #CustomColorPicker"));
        assertTrue(document.contains("Style: $Common.@DefaultColorPickerStyle;"));
        assertTrue(document.contains("Anchor: (Width: 310, Height: 290);"));
        assertTrue(document.contains("Format: Rgb;"));
        assertTrue(document.contains("DisplayTextField: false;"));
        assertTrue(document.contains("Group #FooterSpacer { FlexWeight: 1; }"));
        assertTrue(document.contains("Anchor: (Width: 430, Height: 590);"));
        assertTrue(document.contains("Group #BottomSafeArea { Anchor: (Height: 14); }"));
        assertFalse(document.contains("Anchor: (Height: 42, Bottom: 12);"));
        assertTrue(document.contains("TextButton #BackButton"));
        assertTrue(document.contains("TextButton #ApplyButton"));
        assertTrue(document.contains("TextButton #CloseButton"));
    }

    @Test
    void iconPickerUsesButtonsWithNonTooltipItemIconsAndBottomSpacer() throws IOException {
        String document = read("Common/UI/Custom/VoidVault/VaultIconPicker.ui");

        assertBalanced(document);
        assertTrue(document.startsWith("$Common = \"../Common.ui\";"));
        assertTrue(document.contains("TextField #SearchField"));
        assertTrue(document.contains("Group #FooterSpacer { FlexWeight: 1; }"));
        assertFalse(document.contains("ItemSlotButton"));
        assertFalse(document.contains("ItemSlot #"));
        assertFalse(document.contains("ShowItemTooltip: true;"));
        for (int index = 0; index < 24; index++) {
            assertTrue(document.contains("Button #IconButton" + index));
            assertTrue(document.contains("ItemIcon #IconSlot" + index));
            assertTrue(document.contains("Label #IconName" + index));
        }
    }

    @Test
    void javaSelectorsExistInTheirDocuments() throws IOException {
        String selector = read("Common/UI/Custom/VoidVault/VaultSelector.ui");
        for (int index = 0; index < 6; index++) {
            for (String prefix : new String[]{
                    "#Card", "#CardOpen", "#CardManage", "#CardProgressBar", "#CardIcon", "#CardIconFallback"
            }) {
                assertTrue(selector.contains(prefix + index), "Missing selector " + prefix + index);
            }
        }

        String management = read("Common/UI/Custom/VoidVault/VaultManagement.ui");
        for (String id : new String[]{
                "#RenameField", "#RenameSaveButton", "#RenameResetButton",
                "#SelectedIcon", "#SelectedIconName", "#ChooseIconButton", "#ColorPrev", "#ColorNext",
                "#PresetColorLabel", "#CustomColorLabel", "#CustomColorHexField", "#CustomColorApplyButton",
                "#OpenColorPickerButton", "#FavoriteButton", "#DefaultButton", "#SortButton", "#DepositButton",
                "#BackButton", "#OpenButton", "#CloseButton"
        }) {
            assertTrue(management.contains(id), "Missing UI id " + id);
        }

        String colorPicker = read("Common/UI/Custom/VoidVault/VaultColorPicker.ui");
        for (String id : new String[]{
                "#Panel", "#HeaderGlow", "#Title", "#Subtitle", "#CurrentColorLabel",
                "#CurrentColorSwatch", "#CurrentColorValue", "#CustomColorPicker", "#StatusLabel",
                "#BackButton", "#ApplyButton", "#CloseButton"
        }) {
            assertTrue(colorPicker.contains(id), "Missing color picker UI id " + id);
        }
    }

    private String read(String resource) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertBalanced(String document) {
        assertEquals(count(document, '{'), count(document, '}'));
        assertEquals(count(document, '('), count(document, ')'));
        assertEquals(0, count(document, '"') % 2);
    }

    private int count(String value, char character) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) count++;
        }
        return count;
    }
}

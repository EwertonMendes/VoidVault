package tblack.voidvault.model;

import java.util.ArrayList;
import java.util.List;

public class ImportReport {
    public int playersFound;
    public int playersImported;
    public int playersSkipped;
    public int playersWithItems;
    public int totalItemSlots;
    public int invalidRows;
    public int invalidItems;
    public int overwrittenPlayers;
    public int overflowPlayers;
    public int maxSlot = -1;
    public boolean dryRun;
    public final List<String> warnings = new ArrayList<>();

    public String toChatSummary() {
        return "VoidVault import " + (dryRun ? "dry-run" : "complete") + "\n"
                + "Players found: " + playersFound + "\n"
                + "Players imported: " + playersImported + "\n"
                + "Players with items: " + playersWithItems + "\n"
                + "Stored item slots: " + totalItemSlots + "\n"
                + "Players with overflow slots: " + overflowPlayers + "\n"
                + "Invalid rows/items: " + invalidRows + "/" + invalidItems;
    }

    public String toFileReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("VoidVault import ").append(dryRun ? "dry-run" : "complete").append(System.lineSeparator());
        builder.append("playersFound=").append(playersFound).append(System.lineSeparator());
        builder.append("playersImported=").append(playersImported).append(System.lineSeparator());
        builder.append("playersSkipped=").append(playersSkipped).append(System.lineSeparator());
        builder.append("playersWithItems=").append(playersWithItems).append(System.lineSeparator());
        builder.append("totalItemSlots=").append(totalItemSlots).append(System.lineSeparator());
        builder.append("invalidRows=").append(invalidRows).append(System.lineSeparator());
        builder.append("invalidItems=").append(invalidItems).append(System.lineSeparator());
        builder.append("overwrittenPlayers=").append(overwrittenPlayers).append(System.lineSeparator());
        builder.append("overflowPlayers=").append(overflowPlayers).append(System.lineSeparator());
        builder.append("maxSlot=").append(maxSlot).append(System.lineSeparator());
        if (!warnings.isEmpty()) {
            builder.append(System.lineSeparator()).append("Warnings:").append(System.lineSeparator());
            for (String warning : warnings) {
                builder.append("- ").append(warning).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}

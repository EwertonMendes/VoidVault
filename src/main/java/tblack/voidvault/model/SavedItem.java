package tblack.voidvault.model;

/**
 * JSON-compatible item snapshot stored by VoidVault.
 * This intentionally matches the legacy EnderChest format:
 * {"0":{"id":"ItemId","amount":1,"metadata":"{...}","durability":100.0}}
 */
public class SavedItem {
    public String id;
    public int amount;
    public String metadata;
    public double durability;

    public SavedItem() {
    }

    public SavedItem(String id, int amount, String metadata, double durability) {
        this.id = id;
        this.amount = amount;
        this.metadata = metadata;
        this.durability = durability;
    }

    public boolean isValid() {
        return id != null && !id.isBlank() && amount > 0;
    }
}

package tblack.voidvault.model;

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

package tblack.voidvault.model;

public record VaultInfo(int vaultId, boolean accessible, boolean stored, int overflowItems, String displayName) {
}

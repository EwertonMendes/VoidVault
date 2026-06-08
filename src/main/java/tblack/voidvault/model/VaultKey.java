package tblack.voidvault.model;

import java.util.UUID;

public record VaultKey(UUID ownerUuid, int vaultId) {
    public VaultKey {
        if (ownerUuid == null) {
            throw new IllegalArgumentException("ownerUuid cannot be null");
        }
        if (vaultId < 1) {
            throw new IllegalArgumentException("vaultId must be greater than zero");
        }
    }
}

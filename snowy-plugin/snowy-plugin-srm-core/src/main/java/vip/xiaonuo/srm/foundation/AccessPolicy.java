package vip.xiaonuo.srm.foundation;

import java.util.Set;
/** Pure policy kernel. Principal fields must be populated from authenticated server-side bindings. */
public final class AccessPolicy {
    private AccessPolicy() {}
    public enum Scope { SELF, ORGANIZATION, ORGANIZATION_TREE, ALL }
    public enum PortalRole { ADMIN, QUOTE, SHIP, QUALITY, FINANCE }
    public enum Action { PROFILE_WRITE, QUOTE_WRITE, SHIP_WRITE, QUALITY_WRITE, FINANCE_WRITE, PERFORMANCE_READ }
    public record InternalPrincipal(String userId, String orgId, Scope scope, Set<String> authorizedOrgIds) {
        public InternalPrincipal {
            if (userId == null || userId.isBlank() || orgId == null || orgId.isBlank() || scope == null)
                throw new IllegalArgumentException("Missing trusted identity");
            authorizedOrgIds = authorizedOrgIds == null ? Set.of() : Set.copyOf(authorizedOrgIds);
        }
    }
    public record PortalPrincipal(String accountId, String supplierId, Set<PortalRole> roles, boolean enabled) {
        public PortalPrincipal {
            if (accountId == null || accountId.isBlank() || supplierId == null || supplierId.isBlank())
                throw new IllegalArgumentException("Missing trusted portal identity");
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }
    public static boolean canRead(InternalPrincipal p, String recordOrg, String creator) {
        if (p == null || recordOrg == null) return false;
        return switch (p.scope()) {
            case SELF -> p.orgId().equals(recordOrg) && p.userId().equals(creator);
            case ORGANIZATION -> p.orgId().equals(recordOrg);
            case ORGANIZATION_TREE -> p.orgId().equals(recordOrg) || p.authorizedOrgIds().contains(recordOrg);
            case ALL -> true;
        };
    }
    public static boolean canUsePortal(PortalPrincipal p, String recordSupplier, Action action) {
        if (p == null || action == null || !p.enabled() || !p.supplierId().equals(recordSupplier)) return false;
        if (p.roles().contains(PortalRole.ADMIN)) return true;
        return switch (action) {
            case PROFILE_WRITE -> false;
            case QUOTE_WRITE -> p.roles().contains(PortalRole.QUOTE);
            case SHIP_WRITE -> p.roles().contains(PortalRole.SHIP);
            case QUALITY_WRITE -> p.roles().contains(PortalRole.QUALITY);
            case FINANCE_WRITE -> p.roles().contains(PortalRole.FINANCE);
            case PERFORMANCE_READ -> p.roles().contains(PortalRole.QUOTE) || p.roles().contains(PortalRole.QUALITY) || p.roles().contains(PortalRole.FINANCE);
        };
    }
}

package vip.xiaonuo.srm;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.security.SecureRandom;
import vip.xiaonuo.srm.foundation.*;
import static org.junit.jupiter.api.Assertions.*;
import static vip.xiaonuo.srm.foundation.AccessPolicy.*;
class FoundationPolicyTest {
 @Test void selfScopeIsNotOrganizationScope() {
  var p=new InternalPrincipal("u1","a",Scope.SELF,Set.of());
  assertTrue(canRead(p,"a","u1")); assertFalse(canRead(p,"a","u2")); assertFalse(canRead(p,"b","u1"));
 }
 @Test void organizationAndTreeScopesDenyUnrelatedOrganizations() {
  assertFalse(canRead(new InternalPrincipal("u","a",Scope.ORGANIZATION,Set.of("b")),"b","u"));
  var p=new InternalPrincipal("u","a",Scope.ORGANIZATION_TREE,Set.of("b"));
  assertTrue(canRead(p,"b","x")); assertFalse(canRead(p,"c","x"));
  assertFalse(canRead(null,"a","u")); assertThrows(IllegalArgumentException.class,()->new InternalPrincipal("u","",Scope.ALL,Set.of()));
 }
 @Test void supplierBoundaryAlwaysAppliesEvenToPortalAdmin() {
  var p=new PortalPrincipal("c","s1",Set.of(PortalRole.ADMIN),true);
  for(var a:Action.values()){ assertTrue(canUsePortal(p,"s1",a));assertFalse(canUsePortal(p,"s2",a)); }
 }
 @Test void combinedRolesAndDisabledAccountAreEnforced() {
  var p=new PortalPrincipal("c","s1",Set.of(PortalRole.QUOTE,PortalRole.QUALITY),true);
  assertTrue(canUsePortal(p,"s1",Action.QUOTE_WRITE)); assertTrue(canUsePortal(p,"s1",Action.QUALITY_WRITE));
  assertFalse(canUsePortal(p,"s1",Action.FINANCE_WRITE)); assertFalse(canUsePortal(p,"s1",Action.SHIP_WRITE));
  assertFalse(canUsePortal(new PortalPrincipal("c","s1",Set.of(PortalRole.ADMIN),false),"s1",Action.PROFILE_WRITE));
 }
 private SensitiveFieldCodec codec() {
  var rng=new SecureRandom();byte[] e=new byte[16],m=new byte[32],i=new byte[32];rng.nextBytes(e);rng.nextBytes(m);rng.nextBytes(i);
  return new SensitiveFieldCodec("test-key",e,m,i);
 }
 @Test void encryptedFieldsUseRandomIvAndRoundTrip() {
  var c=codec();var a=c.encrypt("DEMO_BANK_0001");var b=c.encrypt("DEMO_BANK_0001");
  assertNotEquals(a,b);assertFalse(a.contains("DEMO_BANK"));assertEquals("DEMO_BANK_0001",c.decrypt(a));assertEquals("",c.decrypt(c.encrypt("")));
 }
 @Test void tamperingAndWrongKeysFailClosed() {
  var c=codec();var s=c.encrypt("DEMO");var chunks=s.split("\\.");
  chunks[3]=(chunks[3].startsWith("A")?"B":"A")+chunks[3].substring(1);
  assertThrows(IllegalArgumentException.class,()->c.decrypt(String.join(".",chunks)));
  assertThrows(IllegalArgumentException.class,()->codec().decrypt(s));
 }
 @Test void fingerprintIsNormalizedButOrganizationBound() {
  var c=codec();assertEquals(c.creditFingerprint("a"," demo123 "),c.creditFingerprint("a","DEMO123"));
  assertNotEquals(c.creditFingerprint("a","DEMO123"),c.creditFingerprint("b","DEMO123"));
 }
 @Test void missingKeysCannotSilentlyUseDefaults() {
  assertThrows(IllegalArgumentException.class,()->new SensitiveFieldCodec("k",null,null,null));
 }
}

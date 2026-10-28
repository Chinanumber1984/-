package vip.xiaonuo.srm.api;
/** Trusted plugin boundary; never expose arbitrary decryption via HTTP. */
public interface SrmDataProtectionApi {String encrypt(String value);String decrypt(String envelope);}

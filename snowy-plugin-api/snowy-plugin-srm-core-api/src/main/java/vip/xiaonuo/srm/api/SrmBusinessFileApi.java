package vip.xiaonuo.srm.api;
/** Aggregate owner must authorize and bind metadata before reading private storage. */
public interface SrmBusinessFileApi {String put(String fileId,byte[] bytes);byte[] read(String fileId,String engine);void delete(String fileId,String engine);}

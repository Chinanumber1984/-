package vip.xiaonuo.srm.workspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.xiaonuo.srm.api.*;
import vip.xiaonuo.srm.foundation.SensitiveFieldCodec;
import vip.xiaonuo.common.exception.CommonException;
@Service @RequiredArgsConstructor
public class SrmSharedInfrastructure implements SrmDataProtectionApi,SrmBusinessFileApi {
 private final SensitiveFieldCodec codec;private final SrmPrivateStorage storage;
 public String encrypt(String v){return codec.encrypt(v);}public String decrypt(String v){return codec.decrypt(v);}
 public String put(String id,byte[] bytes){try{storage.put(id,bytes);return storage.engine();}catch(Exception e){throw new CommonException(503,"私有附件存储暂不可用");}}
 public byte[] read(String id,String engine){try{return storage.get(id,engine);}catch(Exception e){throw new CommonException(404,"附件不存在或存储不可用");}}
 public void delete(String id,String engine){try{storage.delete(id,engine);}catch(Exception e){throw new CommonException(503,"附件存储清理失败");}}
}

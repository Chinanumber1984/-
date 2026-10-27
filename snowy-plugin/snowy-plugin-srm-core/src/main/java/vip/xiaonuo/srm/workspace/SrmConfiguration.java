package vip.xiaonuo.srm.workspace;
import java.util.Base64;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import vip.xiaonuo.srm.foundation.SensitiveFieldCodec;
@Configuration
public class SrmConfiguration {
 @Bean public SensitiveFieldCodec srmCodec(Environment e){
  try{return new SensitiveFieldCodec(e.getRequiredProperty("SRM_DATA_KEY_ID"),Base64.getDecoder().decode(e.getRequiredProperty("SRM_ENCRYPTION_KEY")),Base64.getDecoder().decode(e.getRequiredProperty("SRM_MAC_KEY")),Base64.getDecoder().decode(e.getRequiredProperty("SRM_INDEX_KEY")));}
  catch(Exception ex){throw new IllegalStateException("SRM密钥未正确配置，请使用受控配置注入；开发环境执行scripts/init-local.sh");}
 }
}

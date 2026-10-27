package vip.xiaonuo.srm.modular.cert.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import vip.xiaonuo.common.pojo.CommonEntity;
/** 数据模型骨架，暂无写接口。 */
@Getter @Setter @TableName("SRM_SUPPLIER_CERT")
public class SrmSupplierCert extends CommonEntity {
    @TableId private String id;
    private String supplierId;
    private String certType;
    private String certNo;
    private java.time.LocalDate validFrom;
    private java.time.LocalDate validTo;
    private String fileId;
    private Integer version;
    private String status;
}

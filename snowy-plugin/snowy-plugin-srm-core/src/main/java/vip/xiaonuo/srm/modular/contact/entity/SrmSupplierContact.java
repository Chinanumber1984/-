package vip.xiaonuo.srm.modular.contact.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import vip.xiaonuo.common.pojo.CommonEntity;
/** 数据模型骨架，暂无写接口。 */
@Getter @Setter @TableName("SRM_SUPPLIER_CONTACT")
public class SrmSupplierContact extends CommonEntity {
    @TableId private String id;
    private String supplierId;
    private String name;
    @com.fasterxml.jackson.annotation.JsonIgnore private String phone;
    private String email;
    private String roles;
    private Boolean enabled;
    private String accountId;
}

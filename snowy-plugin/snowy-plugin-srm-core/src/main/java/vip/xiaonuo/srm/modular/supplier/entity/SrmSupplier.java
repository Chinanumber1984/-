package vip.xiaonuo.srm.modular.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import vip.xiaonuo.common.pojo.CommonEntity;

/** 初始只读档案。敏感字段不进入 API 响应，写入能力待接入密钥管理后实现。 */
@Getter
@Setter
@TableName("SRM_SUPPLIER")
public class SrmSupplier extends CommonEntity {
    @TableId private String id;
    private String supplierCode;
    private String name;
    private String category;
    private String tier;
    private String status;
    private String orgId;
    private String address;
    @JsonIgnore private String creditCode;
    @JsonIgnore private String bankAccount;
}

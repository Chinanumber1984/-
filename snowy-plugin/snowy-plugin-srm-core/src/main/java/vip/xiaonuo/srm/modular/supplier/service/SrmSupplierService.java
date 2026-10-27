package vip.xiaonuo.srm.modular.supplier.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import vip.xiaonuo.srm.modular.supplier.entity.SrmSupplier;
public interface SrmSupplierService {
    Page<SrmSupplier> page(String orgId, long current, long size, String searchKey);
}

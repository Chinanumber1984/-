package vip.xiaonuo.srm.modular.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.xiaonuo.srm.modular.supplier.entity.SrmSupplier;
import vip.xiaonuo.srm.modular.supplier.mapper.SrmSupplierMapper;
import vip.xiaonuo.srm.modular.supplier.service.SrmSupplierService;

@Service
@RequiredArgsConstructor
public class SrmSupplierServiceImpl implements SrmSupplierService {
    private final SrmSupplierMapper mapper;
    public Page<SrmSupplier> page(String orgId, long current, long size, String searchKey) {
        if (!StringUtils.hasText(orgId)) throw new IllegalStateException("当前账号未绑定组织");
        var query = new LambdaQueryWrapper<SrmSupplier>()
                .select(SrmSupplier::getId, SrmSupplier::getSupplierCode, SrmSupplier::getName,
                        SrmSupplier::getCategory, SrmSupplier::getTier, SrmSupplier::getStatus,
                        SrmSupplier::getOrgId, SrmSupplier::getAddress)
                .eq(SrmSupplier::getOrgId, orgId);
        if (StringUtils.hasText(searchKey)) query.and(q -> q.like(SrmSupplier::getName, searchKey)
                .or().like(SrmSupplier::getSupplierCode, searchKey));
        query.orderByAsc(SrmSupplier::getSupplierCode);
        return mapper.selectPage(new Page<>(Math.max(1, current), Math.max(1, Math.min(100, size))), query);
    }
}

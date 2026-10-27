package vip.xiaonuo.srm.modular.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.xiaonuo.auth.core.util.StpLoginUserUtil;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.srm.modular.supplier.entity.SrmSupplier;
import vip.xiaonuo.srm.modular.supplier.service.SrmSupplierService;

@RestController
@RequiredArgsConstructor
public class SrmSupplierController {
    private final SrmSupplierService service;
    @SaCheckPermission("/srm/supplier/page")
    @GetMapping("/srm/supplier/page")
    public CommonResult<Page<SrmSupplier>> page(
            @RequestParam(defaultValue="1") long current,
            @RequestParam(defaultValue="20") long size,
            @RequestParam(defaultValue="") String searchKey) {
        // 组织来自受信任登录会话，忽略客户端传入的 orgId。门户尚未开放。
        return CommonResult.data(service.page(StpLoginUserUtil.getLoginUser().getOrgId(), current, size, searchKey));
    }
}

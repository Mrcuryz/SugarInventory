package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayCheckDTO;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayReportDetailQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.AssayAbnormalitiesVO;
import com.Laibin.SugarInventory.domain.vo.AssayReportDetailVO;
import com.Laibin.SugarInventory.domain.vo.AssayRecordsVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardCoverageVO;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.domain.vo.ProductsWithoutRecentAssayVO;
import com.Laibin.SugarInventory.service.AssayAbnormalitiesService;
import com.Laibin.SugarInventory.service.AssayReportDetailService;
import com.Laibin.SugarInventory.service.AssayRecordsService;
import com.Laibin.SugarInventory.service.AssayService;
import com.Laibin.SugarInventory.service.AssayStandardCoverageService;
import com.Laibin.SugarInventory.service.ProductsWithoutRecentAssayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/assay")
@Tag(name = "化验记录管理", description = "包括导入、查询、详情、更新、删除等接口")
@PreAuthorize("hasAuthority('assay:view')")
public class AssayController {

    @Autowired
    private AssayService assayService;

    @Autowired
    private AssayRecordsService assayRecordsService;

    @Autowired
    private AssayReportDetailService assayReportDetailService;

    @Autowired
    private AssayAbnormalitiesService assayAbnormalitiesService;

    @Autowired
    private ProductsWithoutRecentAssayService productsWithoutRecentAssayService;

    @Autowired
    private AssayStandardCoverageService assayStandardCoverageService;

    @PostMapping("/import")
    @LogOperation(value = "化验数据", type = OperationType.INSERT)
    @Operation(summary = "导入化验记录", description = "批量导入化验记录")
    @PreAuthorize("hasAnyAuthority('assay:create', 'quality:test')")
    public Result<Boolean> importAssays(@RequestBody List<AssaySubmitDTO> dtos,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        assayService.importAssays(dtos, loginUser.getUser().getId());
        return Result.success(true);
    }

    @Operation(summary = "检查化验记录是否存在", description = "根据产品ID和日期检查化验记录是否存在")
    @PostMapping("/exists")
    @PreAuthorize("hasAuthority('assay:view')")
    public Result<Boolean> exists(@RequestBody AssayCheckDTO dto) {
        return Result.success(assayService.existedAssay(dto));
    }

    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "查询化验记录", description = "根据查询条件分页查询化验记录")
    @PostMapping("/query")
    public Result<PageResult<AssayVO>> queryAssays(@RequestBody AssayQueryDTO query) {
        return Result.success(assayService.queryAssays(query));
    }

    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "受控化验记录分析查询", description = "按受控产品范围、采样日期范围和判定状态查询化验记录")
    @PostMapping("/records/query")
    public Result<AssayRecordsVO> queryAssayRecords(@RequestBody AssayRecordsQueryDTO query) {
        return Result.success(assayRecordsService.queryRecords(query));
    }

    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "受控化验报告详情查询", description = "通过受控 reportRef 查询单条化验报告指标、判定和标准摘要")
    @PostMapping("/report-detail/query")
    public Result<AssayReportDetailVO> getAssayReportDetail(@RequestBody AssayReportDetailQueryDTO query) {
        return Result.success(assayReportDetailService.getReportDetail(query));
    }

    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "受控化验异常分析查询", description = "按受控产品范围、采样日期范围和异常类型查询化验质量异常")
    @PostMapping("/abnormalities/query")
    public Result<AssayAbnormalitiesVO> queryAssayAbnormalities(@RequestBody AssayAbnormalitiesQueryDTO query) {
        return Result.success(assayAbnormalitiesService.queryAbnormalities(query));
    }

    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "受控缺化验库存分析查询", description = "按当前在库库存查询指定日期范围内缺少有效化验的产品或库位分组")
    @PostMapping("/products-without-recent-assay/query")
    public Result<ProductsWithoutRecentAssayVO> queryProductsWithoutRecentAssay(@RequestBody ProductsWithoutRecentAssayQueryDTO query) {
        return Result.success(productsWithoutRecentAssayService.queryProductsWithoutRecentAssay(query));
    }

    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "受控质量标准覆盖分析查询", description = "按当前在库产品查询质量标准覆盖缺口")
    @PostMapping("/standard-coverage/query")
    public Result<AssayStandardCoverageVO> queryAssayStandardCoverage(@RequestBody AssayStandardCoverageQueryDTO query) {
        return Result.success(assayStandardCoverageService.queryCoverage(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "查询化验详情", description = "根据化验记录ID查询判定详情")
    public Result<AssayVO> getAssayById(@PathVariable Integer id) {
        return Result.success(assayService.getAssayById(id));
    }

    @GetMapping("/by-product-date")
    @PreAuthorize("hasAuthority('assay:view')")
    @Operation(summary = "按产品和生产日期查询化验记录", description = "用于库存链路查看化验，不存在时返回空")
    public Result<AssayVO> getAssayByProductDate(@RequestParam Integer productId,
                                                 @RequestParam LocalDate productionDate) {
        return Result.success(assayService.getLatestByProductIdAndDate(productId, productionDate));
    }

    @PostMapping("/{id}")
    @LogOperation(value = "化验数据", type = OperationType.UPDATE)
    @Operation(summary = "更新化验记录", description = "根据化验记录ID更新化验数据")
    @PreAuthorize("hasAnyAuthority('assay:update', 'quality:test')")
    public Result<AssayVO> updateAssay(@PathVariable("id") Integer id,
                                       @RequestBody AssaySubmitDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) throws JsonProcessingException {
        return Result.success(assayService.updateAssay(id, dto, loginUser.getUser()));
    }

    @Operation(summary = "删除化验记录", description = "根据化验记录ID删除化验记录")
    @LogOperation(value = "化验数据", type = OperationType.DELETE)
    @PreAuthorize("hasAnyAuthority('assay:delete', 'quality:test')")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAssay(@PathVariable("id") Integer id) {
        assayService.deleteAssay(id);
        return Result.success(true);
    }
}

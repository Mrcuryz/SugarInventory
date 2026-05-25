package com.Laibin.SugarInventory.production.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidateQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionFinishDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelReserveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderCreateDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOutputBindQrDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOutputCreateDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrder;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBindQrResultVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderDetailVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderOptionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderPageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.Laibin.SugarInventory.production.service.ProductionOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/production/orders")
@RequiredArgsConstructor
@Tag(name = "生产订单", description = "生产订单驱动半成品领用与产出贴码")
public class ProductionOrderController {
    private final ProductionOrderService productionOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('production:order:view')")
    public Result<PageResult<ProductionOrderPageVO>> pageOrders(ProductionOrderQueryDTO query) {
        return Result.success(productionOrderService.pageOrders(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('production:order:create')")
    public Result<ProductionOrder> createOrder(@RequestBody @Valid ProductionOrderCreateDTO dto,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(productionOrderService.createOrder(dto,
                loginUser.getUser().getId(),
                loginUser.getUser().getName()));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('production:order:view','production:material:view','production:output:view')")
    public Result<List<ProductionOrderOptionVO>> listActiveOptions(String orderType) {
        return Result.success(productionOrderService.listActiveOptions(orderType));
    }

    @GetMapping("/materials/in-process")
    @PreAuthorize("hasAuthority('production:material:view')")
    public Result<PageResult<ProductionMaterialVO>> pageInProcessMaterials(ProductionInProcessMaterialQueryDTO query) {
        return Result.success(productionOrderService.pageInProcessMaterials(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('production:order:view')")
    public Result<ProductionOrderDetailVO> getOrderDetail(@PathVariable Long id) {
        return Result.success(productionOrderService.getOrderDetail(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('production:order:cancel')")
    public Result<Void> deleteOrder(@PathVariable Long id,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        productionOrderService.deleteOrder(id, loginUser.getUser().getId());
        return Result.success(null);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('production:order:cancel')")
    public Result<Void> cancelOrder(@PathVariable Long id,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        productionOrderService.cancelOrder(id, loginUser.getUser().getId());
        return Result.success(null);
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAuthority('production:order:view')")
    public Result<ProductionOrderDetailVO> getOrderTrace(@PathVariable Long id) {
        return Result.success(productionOrderService.getOrderDetail(id));
    }

    @GetMapping("/{id}/material-candidates")
    @PreAuthorize("hasAuthority('production:material:view')")
    public Result<PageResult<ProductionMaterialCandidateVO>> pageMaterialCandidates(@PathVariable Long id,
                                                                                   ProductionMaterialCandidateQueryDTO query) {
        return Result.success(productionOrderService.pageMaterialCandidates(id, query));
    }

    @PostMapping("/{id}/materials/pick")
    @PreAuthorize("hasAuthority('production:material:pick')")
    public Result<Void> pickMaterials(@PathVariable Long id,
                                      @RequestBody ProductionMaterialPickDTO dto,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        productionOrderService.pickMaterials(id, dto, loginUser.getUser().getId(), loginUser.getUser().getName());
        return Result.success(null);
    }

    @PostMapping("/{id}/materials/finish")
    @PreAuthorize("hasAuthority('production:material:pick')")
    public Result<Void> finishMaterials(@PathVariable Long id,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        productionOrderService.finishMaterials(id, loginUser.getUser().getId());
        return Result.success(null);
    }

    @PostMapping("/{id}/outputs")
    @PreAuthorize("hasAuthority('production:output:create')")
    public Result<ProductionOutputVO> addOutput(@PathVariable Long id,
                                                @RequestBody @Valid ProductionOutputCreateDTO dto,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(productionOrderService.addOutput(id, dto, loginUser.getUser().getId()));
    }

    @PutMapping("/outputs/{outputId}")
    @PreAuthorize("hasAuthority('production:output:create')")
    public Result<ProductionOutputVO> updateOutput(@PathVariable Long outputId,
                                                   @RequestBody @Valid ProductionOutputCreateDTO dto,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(productionOrderService.updateOutput(outputId, dto, loginUser.getUser().getId()));
    }

    @DeleteMapping("/outputs/{outputId}")
    @PreAuthorize("hasAuthority('production:output:create')")
    public Result<Void> deleteOutput(@PathVariable Long outputId,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        productionOrderService.deleteOutput(outputId, loginUser.getUser().getId());
        return Result.success(null);
    }

    @PostMapping("/outputs/{outputId}/bind-fixed-qrs")
    @PreAuthorize("hasAuthority('production:output:bindQr')")
    public Result<ProductionBindQrResultVO> bindFixedQrs(@PathVariable Long outputId,
                                                         @RequestBody ProductionOutputBindQrDTO dto,
                                                         @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(productionOrderService.bindFixedQrs(outputId, dto, loginUser.getUser().getId()));
    }

    @PostMapping("/outputs/{outputId}/print-codes")
    @PreAuthorize("hasAuthority('production:output:print')")
    public Result<List<ProductionOrderOutputCode>> markOutputPrinted(@PathVariable Long outputId) {
        return Result.success(productionOrderService.markOutputPrinted(outputId));
    }

    @PostMapping("/{id}/label-batches")
    @PreAuthorize("hasAuthority('production:label:reserve')")
    public Result<List<ProductionLabelBatchVO>> reserveLabels(@PathVariable Long id,
                                                              @RequestBody ProductionLabelReserveDTO dto,
                                                              @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(productionOrderService.reserveLabels(id, dto, loginUser.getUser().getId()));
    }

    @GetMapping("/{id}/label-batches")
    @PreAuthorize("hasAuthority('production:order:view')")
    public Result<List<ProductionLabelBatchVO>> listLabelBatches(@PathVariable Long id) {
        return Result.success(productionOrderService.listLabelBatches(id));
    }

    @PostMapping("/label-batches/{batchId}/print")
    @PreAuthorize("hasAuthority('production:label:print')")
    public void printLabelBatch(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        byte[] pdf = productionOrderService.printLabelBatch(batchId);
        writeDownload(response, "production-label-batch-" + batchId + "-" + LocalDate.now() + ".pdf", pdf);
    }

    @PostMapping("/{id}/production-finish")
    @PreAuthorize("hasAuthority('production:label:finish')")
    public Result<ProductionOrderDetailVO> finishProduction(@PathVariable Long id,
                                                            @RequestBody @Valid ProductionFinishDTO dto,
                                                            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(productionOrderService.finishProduction(id, dto, loginUser.getUser().getId()));
    }

    private void writeDownload(HttpServletResponse response, String fileName, byte[] content) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }
}

package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.GeneratePalletCodeDTO;
import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletQrExportDTO;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.BindTaskSemiItemsDTO;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmFinishOutBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmTransferBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiConsumeBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiOutBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiPrepareBatchDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.CreateFinishOutTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateSemiOutTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateSemiPrepareTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateTransferTaskDTO;
import com.Laibin.SugarInventory.domain.dto.DeletePalletFlowBatchDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMapBatchOperationDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMapSlotInboundDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.vo.PalletCodeInfoVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletAssayVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletBindResultVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowCyclePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowDetailVO;
import com.Laibin.SugarInventory.domain.vo.TaskSemiItemVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.WarehouseMapTaskCreateResultVO;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.service.PalletCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 托盘码接口
 */
@RestController
@RequestMapping("/api/pallet-codes")
@Tag(name = "托盘码", description = "托盘码生成与解析接口")
public class PalletCodeController {

    @Autowired
    private PalletCodeService palletCodeService;

    @Operation(summary = "批量生成托盘码", description = "根据数量批量生成托盘码")
    @PostMapping("/generate")
    public Result<List<String>> generate(@RequestBody @Valid GeneratePalletCodeDTO dto,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        try {
            List<PalletCode> codes = palletCodeService.generateCodes(dto.getCount(), loginUser.getUser().getId());
            List<String> codeStrings = codes.stream().map(PalletCode::getCode).toList();
            return Result.success(codeStrings);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "托盘码列表查询", description = "托盘码管理页分页查询接口")
    @PostMapping("")
    public Result<PageResult<PalletCodePageVO>> page(@RequestBody PalletCodeQueryDTO queryDTO) {
        try {
            PageResult<PalletCodePageVO> page = palletCodeService.pagePalletCodes(queryDTO);
            return Result.success(page);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "托盘入库任务列表", description = "托盘任务分页查询接口")
    @PostMapping("/tasks/list")
    public Result<PageResult<PalletTaskPageVO>> pageTasks(@RequestBody PalletTaskQueryDTO queryDTO) {
        try {
            PageResult<PalletTaskPageVO> page = palletCodeService.pagePalletTasks(queryDTO);
            return Result.success(page);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "托盘流转轮次分页", description = "按托盘码分页查询历史循环轮次摘要")
    @GetMapping("/{code}/flows/cycles")
    public Result<PageResult<PalletFlowCyclePageVO>> pageFlowCycles(@PathVariable("code") String code,
                                                                    @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
                                                                    @RequestParam(value = "pageSize", defaultValue = "5") Long pageSize) {
        try {
            PageResult<PalletFlowCyclePageVO> page = palletCodeService.pagePalletFlowCycles(code, pageNum, pageSize);
            return Result.success(page);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "托盘流转明细", description = "按托盘码和循环号查询流转时间线")
    @GetMapping("/{code}/flows")
    public Result<List<PalletFlowDetailVO>> listFlowsByCycle(@PathVariable("code") String code,
                                                             @RequestParam("cycleNo") Integer cycleNo) {
        try {
            List<PalletFlowDetailVO> records = palletCodeService.listPalletFlowsByCycle(code, cycleNo);
            return Result.success(records);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "批量删除托盘流转记录", description = "仅允许删除超过180天且非当前轮次的历史流转记录")
    @PostMapping("/flows/delete")
    public Result<Void> deleteFlows(@RequestBody @Valid DeletePalletFlowBatchDTO dto) {
        try {
            palletCodeService.deletePalletFlows(dto);
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    // 批量确认托盘入库，内部按任务类型自动分支
    @Operation(summary = "托盘任务确认入库", description = "批量确认托盘入库任务")
    @PostMapping("/tasks/confirm")
    public Result<List<InVO>> confirmTasks(@RequestBody @Valid ConfirmPalletInBatchDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        try {
            Integer operatorId = loginUser.getUser().getId();
            List<InVO> result = palletCodeService.confirmFinishedTaskInBatch(dto, operatorId);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "批量作废托盘码", description = "仅允许将空闲托盘码置为 INVALID")
    @PostMapping("/invalid")
    public Result<Void> invalidateCodes(@RequestBody @Valid CancelPalletBatchDTO dto,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.invalidatePalletCodes(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "批量取消入库任务", description = "按托盘码取消当前轮次待处理入库任务，并释放托盘回 FREE")
    @PostMapping("/tasks/cancel")
    public Result<Void> cancelTasks(@RequestBody @Valid CancelPalletBatchDTO dto,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.cancelTasksByCodes(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "解析托盘码", description = "小程序扫码后解析托盘码并返回基础信息")
    @GetMapping("/parse")
    public Result<PalletCodeInfoVO> parse(@RequestParam("code") String code) {
        return Result.success(palletCodeService.parseAndGetInfo(code));
    }

    @Operation(summary = "托盘码二维码", description = "生成托盘码对应的二维码图片(PNG)")
    @GetMapping("/{code}/qrcode")
    public void generateQrCode(@PathVariable("code") String code, HttpServletResponse response) throws IOException {
        byte[] png = palletCodeService.generateQrCodePng(code);
        response.setContentType("image/png");
        response.getOutputStream().write(png);
    }

    @Operation(summary = "下载托盘二维码 PNG", description = "下载白底黑码高清 PNG")
    @GetMapping("/{code}/qrcode.png")
    public void downloadQrCodePng(@PathVariable("code") String code, HttpServletResponse response) throws IOException {
        byte[] png = palletCodeService.generateQrCodePng(code);
        writeDownload(response, "image/png", safeFileName(code) + ".png", png);
    }

    @Operation(summary = "下载托盘二维码 SVG", description = "下载托盘二维码 SVG，作为高级排版选项")
    @GetMapping("/{code}/qrcode.svg")
    public void downloadQrCodeSvg(@PathVariable("code") String code, HttpServletResponse response) throws IOException {
        String svg = palletCodeService.generateQrCodeSvg(code);
        writeDownload(response, "image/svg+xml;charset=UTF-8", safeFileName(code) + ".svg", svg.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "下载托盘二维码标签 PDF", description = "下载单个托盘码的 A4 打印版 PDF 标签")
    @GetMapping("/{code}/qrcode-label.pdf")
    public void downloadQrLabelPdf(@PathVariable("code") String code, HttpServletResponse response) throws IOException {
        PalletQrExportDTO dto = new PalletQrExportDTO();
        dto.setCodes(List.of(code));
        byte[] pdf = palletCodeService.generateQrLabelPdf(dto);
        writeDownload(response, "application/pdf", safeFileName(code) + ".pdf", pdf);
    }

    @Operation(summary = "批量导出托盘二维码标签 PDF", description = "按 A4 标签版批量导出托盘二维码 PDF")
    @PostMapping("/qrcode-labels/pdf")
    public void batchDownloadQrLabelPdf(@RequestBody @Valid PalletQrExportDTO dto,
                                        HttpServletResponse response) throws IOException {
        byte[] pdf = palletCodeService.generateQrLabelPdf(dto);
        writeDownload(response, "application/pdf", "pallet-labels-batch-" + LocalDate.now() + ".pdf", pdf);
    }

    @Operation(summary = "托盘化验数据", description = "根据托盘码查询化验数据")
    @GetMapping("/{code}/assay")
    public Result<PalletAssayVO> getAssay(@PathVariable("code") String code) {
        try {
            PalletAssayVO vo = palletCodeService.getAssayByCode(code);
            return Result.success(vo);
        } catch (BusinessException e) {
            return Result.error(500, "化验记录查询失败：" + e.getMessage());
        }
    }

    @Operation(summary = "托盘库存位置", description = "根据托盘码查询当前库存位置")
    @GetMapping("/{code}/inventory")
    public Result<PalletInventoryVO> getInventory(@PathVariable("code") String code) {
        try {
            PalletInventoryVO vo = palletCodeService.getInventoryByCode(code);
            return Result.success(vo);
        } catch (BusinessException e) {
            return Result.error(500, e.getMessage());
        }
    }

    @Operation(summary = "扫码绑定托盘并创建入库任务", description = "小程序/PC 扫描托盘二维码后，绑定产品信息并创建入库任务（不处理化验记录）")
    @PostMapping("/bind")
    public Result<PalletBindResultVO> bind(@RequestBody @Valid BindPalletTaskDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        try {
            Integer operatorId = loginUser.getUser().getId();
            PalletBindResultVO vo = palletCodeService.bindPalletAndCreateTask(dto, operatorId);
            return Result.success(vo);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "成品任务绑定半成品明细", description = "为成品入库任务绑定使用的半成品托盘明细（全量覆盖）")
    @PostMapping("/tasks/semi-bind")
    public Result<List<TaskSemiItemVO>> bindSemiItems(@RequestBody @Valid BindTaskSemiItemsDTO dto,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        try {
            Integer operatorId = loginUser.getUser().getId();
            // 覆盖式绑定：先删旧明细，再保存当前提交的半成品托盘列表
            List<TaskSemiItemVO> vo = palletCodeService.bindSemiItemsToTask(dto, operatorId);
            return Result.success(vo);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "创建半成品普通出库任务", description = "扫码一个或多个半成品托盘码，创建普通出库任务")
    @PostMapping("/semi/out/create")
    public Result<Void> createSemiOutTasks(@RequestBody @Valid CreateSemiOutTaskDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.createSemiOutTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "确认半成品普通出库", description = "批量确认半成品普通出库任务")
    @PostMapping("/semi/out/confirm")
    public Result<Void> confirmSemiOutTasks(@RequestBody @Valid ConfirmSemiOutBatchDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.confirmSemiOutTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "创建半成品转入备料池任务", description = "扫码一个或多个半成品托盘码，创建转入备料池任务")
    @PostMapping("/semi/prepare/create")
    public Result<Void> createSemiPrepareTasks(@RequestBody @Valid CreateSemiPrepareTaskDTO dto,
                                               @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.createSemiPrepareTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "确认半成品转入备料池", description = "批量确认半成品转入备料池任务")
    @PostMapping("/semi/prepare/confirm")
    public Result<Void> confirmSemiPrepareTasks(@RequestBody @Valid ConfirmSemiPrepareBatchDTO dto,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.confirmSemiPrepareTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "确认半成品消耗", description = "批量确认备料池中的半成品托盘已最终消耗")
    @PostMapping("/semi/consume/confirm")
    public Result<Void> confirmSemiConsume(@RequestBody @Valid ConfirmSemiConsumeBatchDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.confirmSemiConsume(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "创建成品出库任务", description = "扫码一个或多个成品托盘码，创建成品出库任务")
    @PostMapping("/finish/out/create")
    public Result<Void> createFinishOutTasks(@RequestBody @Valid CreateFinishOutTaskDTO dto,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.createFinishOutTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "确认成品出库", description = "批量确认成品出库任务")
    @PostMapping("/finish/out/confirm")
    public Result<Void> confirmFinishOutTasks(@RequestBody @Valid ConfirmFinishOutBatchDTO dto,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.confirmFinishOutTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "创建托盘调拨任务", description = "扫码一个或多个在库托盘码，创建托盘级调拨任务")
    @PostMapping("/transfer/create")
    public Result<Void> createTransferTasks(@RequestBody @Valid CreateTransferTaskDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.createTransferTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "确认托盘调拨", description = "批量确认托盘级调拨任务")
    @PostMapping("/transfer/confirm")
    public Result<Void> confirmTransferTasks(@RequestBody @Valid ConfirmTransferBatchDTO dto,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        try {
            palletCodeService.confirmTransferTasks(dto, loginUser.getUser().getId());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "仓库平面图批量创建任务", description = "按库位、侧别和前N板创建出库/调拨/转入备料池任务；传入 codes 时按指定托盘码精确创建")
    @PostMapping("/warehouse-map/tasks/create")
    public Result<WarehouseMapTaskCreateResultVO> createWarehouseMapTasks(@RequestBody @Valid WarehouseMapBatchOperationDTO dto,
                                                                          @AuthenticationPrincipal LoginUser loginUser) {
        try {
            WarehouseMapTaskCreateResultVO result = palletCodeService.createWarehouseMapTasks(dto, loginUser.getUser().getId());
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "仓库平面图单板入库", description = "创建入库任务并直接确认到指定库位格子")
    @PostMapping("/warehouse-map/slot/inbound")
    public Result<InVO> createWarehouseMapSlotInbound(@RequestBody @Valid WarehouseMapSlotInboundDTO dto,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        try {
            InVO result = palletCodeService.createWarehouseMapSlotInbound(dto, loginUser.getUser().getId());
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    private void writeDownload(HttpServletResponse response, String contentType, String filename, byte[] bytes) throws IOException {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Content-Length", String.valueOf(bytes.length));
        response.getOutputStream().write(bytes);
    }

    private String safeFileName(String code) {
        return code == null ? "pallet-qrcode" : code.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
    }
}

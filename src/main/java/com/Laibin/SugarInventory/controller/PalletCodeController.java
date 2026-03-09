package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.GeneratePalletCodeDTO;
import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.BindTaskSemiItemsDTO;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.vo.PalletCodeInfoVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletAssayVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletBindResultVO;
import com.Laibin.SugarInventory.domain.vo.TaskSemiItemVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.util.QrCodeUtils;
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
import javax.imageio.ImageIO;
import java.io.IOException;
import java.awt.image.BufferedImage;
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

    @Operation(summary = "批量作废托盘码", description = "将托盘码置为 INVALID 并取消关联任务")
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

    @Operation(summary = "批量取消入库任务", description = "按托盘码取消任务并作废托盘码")
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
        // 校验托盘码合法性和存在性
        palletCodeService.parseAndFind(code);
        BufferedImage image = QrCodeUtils.generateQrCode(code, 256, 256);
        response.setContentType("image/png");
        ImageIO.write(image, "PNG", response.getOutputStream());
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
}

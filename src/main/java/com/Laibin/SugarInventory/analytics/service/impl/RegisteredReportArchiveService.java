package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExportAuditPO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportHistoryItemVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExportAuditMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportRunMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegisteredReportArchiveService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int RETENTION_DAYS = 30;
    private static final Pattern REPORT_RUN_ID = Pattern.compile("^report_run_[a-f0-9]{16}$");

    private final RegisteredReportRunMapper reportRunMapper;
    private final RegisteredReportExportAuditMapper exportAuditMapper;
    private final ObjectMapper objectMapper;
    private final RegisteredReportXlsxExporter xlsxExporter;

    @Transactional
    public RegisteredReportRunVO persist(
            RegisteredReportRunVO report,
            Integer ownerUserId,
            String ownerDisplayName) {
        if (report == null || ownerUserId == null) {
            throw new BusinessException(400, "报表运行结果和当前用户不能为空");
        }
        String reportRunId = safeReportRunId(report.getReportRunId());
        String payload = writePayload(report);
        LocalDateTime generatedAt = LocalDateTime.now(BUSINESS_ZONE);

        RegisteredReportRunPO stored = new RegisteredReportRunPO();
        stored.setReportRunId(reportRunId);
        stored.setOwnerUserId(ownerUserId);
        stored.setOwnerDisplayName(displayName(ownerDisplayName));
        stored.setReportDefinitionId(report.getReportDefinitionId());
        stored.setReportVersion(report.getReportVersion());
        stored.setRequiredPermission(requiredPermission(report.getReportDefinitionId()));
        stored.setPayloadJson(payload);
        stored.setContentSha256(sha256(payload.getBytes(StandardCharsets.UTF_8)));
        stored.setGeneratedAt(generatedAt);
        stored.setExpiresAt(generatedAt.plusDays(RETENTION_DAYS));
        stored.setCreatedAt(generatedAt);
        reportRunMapper.insert(stored);
        return report;
    }

    @Transactional(readOnly = true)
    public RegisteredReportRunVO load(
            String reportRunId,
            Integer ownerUserId,
            Set<String> currentAuthorities) {
        return readStored(reportRunId, ownerUserId, currentAuthorities).report();
    }

    @Transactional(readOnly = true)
    public PageResult<RegisteredReportHistoryItemVO> list(
            Integer ownerUserId,
            Set<String> currentAuthorities,
            Integer page,
            Integer size,
            String reportDefinitionId) {
        if (ownerUserId == null) {
            throw new BusinessException(401, "当前用户未登录");
        }
        int normalizedPage = page == null ? 1 : page;
        int normalizedSize = size == null ? 10 : size;
        if (normalizedPage < 1 || normalizedSize < 1 || normalizedSize > 50) {
            throw new BusinessException(400, "分页参数无效，每页最多查看 50 份报表");
        }

        Set<String> authorities = currentAuthorities == null ? Set.of() : currentAuthorities;
        Set<String> visiblePermissionSets = visiblePermissionSets(authorities);
        if (visiblePermissionSets.isEmpty()) {
            return new PageResult<>(0L, List.of());
        }

        String normalizedDefinitionId = normalizeDefinitionFilter(reportDefinitionId);
        LambdaQueryWrapper<RegisteredReportRunPO> query =
                new LambdaQueryWrapper<RegisteredReportRunPO>()
                        .eq(RegisteredReportRunPO::getOwnerUserId, ownerUserId)
                        .gt(RegisteredReportRunPO::getExpiresAt, LocalDateTime.now(BUSINESS_ZONE))
                        .in(RegisteredReportRunPO::getRequiredPermission, visiblePermissionSets)
                        .orderByDesc(RegisteredReportRunPO::getGeneratedAt)
                        .orderByDesc(RegisteredReportRunPO::getId);
        if (normalizedDefinitionId != null) {
            query.eq(RegisteredReportRunPO::getReportDefinitionId, normalizedDefinitionId);
        }

        Page<RegisteredReportRunPO> result = reportRunMapper.selectPage(
                new Page<>(normalizedPage, normalizedSize),
                query);
        List<RegisteredReportHistoryItemVO> records = result.getRecords().stream()
                .map(row -> toHistoryItem(row, authorities))
                .toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Transactional
    public ExportedReportFile exportXlsx(
            String reportRunId,
            Integer ownerUserId,
            String exporterDisplayName,
            Set<String> currentAuthorities) {
        StoredReport stored = readStored(reportRunId, ownerUserId, currentAuthorities);
        LocalDateTime exportedAt = LocalDateTime.now(BUSINESS_ZONE);
        String auditRef = "report_export_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        byte[] content = xlsxExporter.export(
                stored.report(),
                stored.row(),
                displayName(exporterDisplayName),
                auditRef,
                exportedAt);

        RegisteredReportExportAuditPO audit = new RegisteredReportExportAuditPO();
        audit.setAuditRef(auditRef);
        audit.setReportRunId(stored.row().getReportRunId());
        audit.setExporterUserId(ownerUserId);
        audit.setExporterDisplayName(displayName(exporterDisplayName));
        audit.setExportFormat("XLSX");
        audit.setContentSha256(sha256(content));
        audit.setExportedAt(exportedAt);
        exportAuditMapper.insert(audit);

        String filename = safeFilename(stored.report().getReportName())
                + "_" + stored.report().getStartDate()
                + (stored.report().getStartDate().equals(stored.report().getEndDate())
                ? "" : "_" + stored.report().getEndDate())
                + ".xlsx";
        return new ExportedReportFile(filename, content, auditRef);
    }

    private StoredReport readStored(
            String reportRunId,
            Integer ownerUserId,
            Set<String> currentAuthorities) {
        if (ownerUserId == null) {
            throw new BusinessException(401, "当前用户未登录");
        }
        String normalizedId = safeReportRunId(reportRunId);
        RegisteredReportRunPO stored = reportRunMapper.selectOne(
                new LambdaQueryWrapper<RegisteredReportRunPO>()
                        .eq(RegisteredReportRunPO::getReportRunId, normalizedId)
                        .eq(RegisteredReportRunPO::getOwnerUserId, ownerUserId)
                        .last("LIMIT 1"));
        if (stored == null) {
            throw new BusinessException(404, "未找到这份历史报表，可能已过期或不属于当前用户");
        }
        if (stored.getExpiresAt() == null
                || stored.getExpiresAt().isBefore(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new BusinessException(410, "这份历史报表已过期，请重新生成");
        }
        Set<String> authorities = currentAuthorities == null ? Set.of() : currentAuthorities;
        if (!hasAllRequiredPermissions(authorities, stored.getRequiredPermission())) {
            throw new BusinessException(403, "当前用户没有查看或导出这份报表的权限");
        }
        return new StoredReport(stored, parseVerifiedPayload(stored));
    }

    private RegisteredReportHistoryItemVO toHistoryItem(
            RegisteredReportRunPO stored,
            Set<String> currentAuthorities) {
        if (!hasAllRequiredPermissions(currentAuthorities, stored.getRequiredPermission())) {
            throw new BusinessException(403, "当前用户没有查看这份报表的权限");
        }
        RegisteredReportRunVO report = parseVerifiedPayload(stored);
        return RegisteredReportHistoryItemVO.builder()
                .reportRunId(stored.getReportRunId())
                .reportDefinitionId(report.getReportDefinitionId())
                .reportVersion(report.getReportVersion())
                .reportName(report.getReportName())
                .dateRangeLabel(report.getDateRangeLabel())
                .scopeLabel(scopeLabel(report))
                .dataAsOf(report.getDataAsOf())
                .generatedAt(stored.getGeneratedAt())
                .expiresAt(stored.getExpiresAt())
                .comparisonIncluded(report.getComparison() != null)
                .partialData(report.getDataQuality() != null
                        && report.getDataQuality().isPartial())
                .build();
    }

    private RegisteredReportRunVO parseVerifiedPayload(RegisteredReportRunPO stored) {
        String payload = stored.getPayloadJson();
        if (payload == null || stored.getContentSha256() == null
                || !sha256(payload.getBytes(StandardCharsets.UTF_8))
                .equalsIgnoreCase(stored.getContentSha256())) {
            throw new BusinessException(409, "历史报表内容校验失败，请重新生成");
        }
        try {
            return objectMapper.readValue(payload, RegisteredReportRunVO.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取历史报表快照失败", exception);
        }
    }

    private static String scopeLabel(RegisteredReportRunVO report) {
        Map<String, String> filters = report.getFiltersApplied() == null
                ? Map.of()
                : report.getFiltersApplied();
        String productScope = normalizedLabel(filters.get("productScope"));
        String taskScope = normalizedLabel(filters.get("taskScope"));
        String metricName = report.getMetricTrendSummary() == null
                ? ""
                : normalizedLabel(report.getMetricTrendSummary().getMetricName());
        return List.of(metricName, productScope, taskScope).stream()
                .filter(value -> !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + " · " + right)
                .orElse("全部范围");
    }

    private static String normalizedLabel(String value) {
        return value == null ? "" : value.trim();
    }

    private static Set<String> visiblePermissionSets(Set<String> currentAuthorities) {
        java.util.LinkedHashSet<String> visible = new java.util.LinkedHashSet<>();
        if (currentAuthorities.contains("production:order:view")) {
            visible.add("production:order:view");
            if (currentAuthorities.contains("production:material:view")) {
                visible.add("production:order:view,production:material:view");
            }
        }
        if (currentAuthorities.contains("assay:view")) {
            visible.add("assay:view");
        }
        if (currentAuthorities.contains("task:view")) {
            visible.add("task:view");
        }
        if (currentAuthorities.contains("inventory:view")) {
            visible.add("inventory:view");
        }
        if (currentAuthorities.containsAll(Set.of(
                "production:order:view",
                "production:material:view",
                "assay:view",
                "inventory:view",
                "task:view"))) {
            visible.add("production:order:view,production:material:view,assay:view,inventory:view,task:view");
        }
        return Set.copyOf(visible);
    }

    private static String normalizeDefinitionFilter(String reportDefinitionId) {
        if (reportDefinitionId == null || reportDefinitionId.isBlank()) {
            return null;
        }
        String normalized = reportDefinitionId.trim();
        if (!Set.of(
                RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID,
                QualityAssayRegisteredReportService.REPORT_ID,
                QualityMetricRegisteredReportService.REPORT_ID,
                ProductionInputOutputRegisteredReportService.REPORT_ID,
                PalletTaskCycleRegisteredReportService.REPORT_ID,
                InventoryTrendRegisteredReportService.REPORT_ID,
                RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID)
                .contains(normalized)) {
            throw new BusinessException(400, "报表类型无效");
        }
        return normalized;
    }

    private String writePayload(RegisteredReportRunVO report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存报表快照失败", exception);
        }
    }

    private static String requiredPermission(String reportDefinitionId) {
        return switch (reportDefinitionId == null ? "" : reportDefinitionId) {
            case RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID -> "production:order:view";
            case ProductionInputOutputRegisteredReportService.REPORT_ID ->
                    "production:order:view,production:material:view";
            case QualityAssayRegisteredReportService.REPORT_ID,
                    QualityMetricRegisteredReportService.REPORT_ID -> "assay:view";
            case PalletTaskCycleRegisteredReportService.REPORT_ID -> "task:view";
            case InventoryTrendRegisteredReportService.REPORT_ID -> "inventory:view";
            case RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID ->
                    "production:order:view,production:material:view,assay:view,inventory:view,task:view";
            default -> throw new BusinessException(400, "当前报表定义不允许持久化");
        };
    }

    private static boolean hasAllRequiredPermissions(
            Set<String> currentAuthorities,
            String requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isBlank()) {
            return false;
        }
        for (String required : requiredPermissions.split(",")) {
            String normalized = required.trim();
            if (normalized.isEmpty() || !currentAuthorities.contains(normalized)) {
                return false;
            }
        }
        return true;
    }

    private static String safeReportRunId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!REPORT_RUN_ID.matcher(normalized).matches()) {
            throw new BusinessException(400, "报表运行编号无效");
        }
        return normalized;
    }

    private static String displayName(String value) {
        if (value == null || value.isBlank()) {
            return "当前用户";
        }
        String normalized = value.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private static String safeFilename(String value) {
        String normalized = value == null ? "业务报表" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return normalized.isEmpty() ? "业务报表" : normalized;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private record StoredReport(RegisteredReportRunPO row, RegisteredReportRunVO report) {
    }

    public record ExportedReportFile(String filename, byte[] content, String auditRef) {
    }
}

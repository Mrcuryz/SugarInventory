package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyMetricValueDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyProductLineDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportHeaderSaveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportImportConfirmDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportImportSectionDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportSectionSaveDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyMetricValue;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyProductLine;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyReport;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyReportSection;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyMetricValueVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductLineVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductOptionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportGroupVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportImportVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportListVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportSectionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyMetricValueMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyProductLineMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportSectionMapper;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog.GroupDefinition;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog.MetricDefinition;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog.SectionDefinition;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportService;
import com.Laibin.SugarInventory.production.service.impl.ProductionDailyReportXlsxImporter.ParsedProductLine;
import com.Laibin.SugarInventory.production.service.impl.ProductionDailyReportXlsxImporter.ParsedReport;
import com.Laibin.SugarInventory.production.service.impl.ProductionDailyReportXlsxImporter.ParsedSection;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionDailyReportServiceImpl implements ProductionDailyReportService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_IMPORT_FILE_SIZE = 10L * 1024L * 1024L;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionDailyReportMapper reportMapper;
    private final ProductionDailyReportSectionMapper sectionMapper;
    private final ProductionDailyMetricValueMapper metricValueMapper;
    private final ProductionDailyProductLineMapper productLineMapper;
    private final ProductMapper productMapper;
    private final ProductionDailyReportXlsxExporter xlsxExporter;
    private final ProductionDailyReportXlsxImporter xlsxImporter;

    @Override
    public ProductionDailyReportVO getReport(LocalDate reportDate) {
        if (reportDate == null) {
            throw new BusinessException(400, "日报日期不能为空");
        }
        ProductionDailyReport report = reportMapper.selectByReportDate(reportDate);
        return assembleReport(reportDate, report);
    }

    @Override
    public PageResult<ProductionDailyReportListVO> pageReports(ProductionDailyReportQueryDTO query) {
        ProductionDailyReportQueryDTO safeQuery = query == null ? new ProductionDailyReportQueryDTO() : query;
        int page = safeQuery.getPage() == null || safeQuery.getPage() < 1 ? 1 : safeQuery.getPage();
        int size = safeQuery.getSize() == null || safeQuery.getSize() < 1
                ? 10 : Math.min(safeQuery.getSize(), MAX_PAGE_SIZE);
        safeQuery.setPage(page);
        safeQuery.setSize(size);
        return new PageResult<>(reportMapper.countReports(safeQuery),
                reportMapper.pageReports(safeQuery, (page - 1) * size, size));
    }

    @Override
    @Transactional
    public ProductionDailyReportVO saveHeader(LocalDate reportDate, ProductionDailyReportHeaderSaveDTO dto,
                                              Integer operatorId, String operatorName) {
        if (dto == null) {
            throw new BusinessException(400, "请求内容不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        ProductionDailyReport report = reportMapper.selectByReportDate(reportDate);
        if (report == null) {
            report = newReport(reportDate, operatorId, operatorName, now);
            report.setPreparedDate(dto.getPreparedDate());
            report.setPreparedByName(blankToNull(dto.getPreparedByName()));
            report.setVersion(1);
            report.setUpdatedBy(operatorId);
            report.setUpdatedByName(operatorName);
            report.setUpdatedAt(now);
            try {
                reportMapper.insert(report);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_CONFLICT);
            }
        } else {
            int expectedVersion = dto.getVersion() == null ? 0 : dto.getVersion();
            int updated = reportMapper.updateHeaderWithVersion(report.getId(), expectedVersion,
                    dto.getPreparedDate(), blankToNull(dto.getPreparedByName()),
                    operatorId, operatorName, now);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_CONFLICT);
            }
        }
        return getReport(reportDate);
    }

    @Override
    @Transactional
    public ProductionDailyReportVO saveSection(LocalDate reportDate, String departmentCode,
                                               ProductionDailyReportSectionSaveDTO dto,
                                               Integer operatorId, String operatorName) {
        if (dto == null) {
            throw new BusinessException(400, "请求内容不能为空");
        }
        SectionDefinition definition = requireSection(departmentCode);
        LocalDateTime now = LocalDateTime.now();
        ProductionDailyReport report = ensureReport(reportDate, operatorId, operatorName, now);
        ProductionDailyReportSection section = ensureSection(report.getId(), definition, operatorId, operatorName, now);
        int expectedVersion = dto.getVersion() == null ? 0 : dto.getVersion();
        if (sectionMapper.updateForSave(section.getId(), expectedVersion, operatorId, operatorName, now) == 0) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_CONFLICT);
        }

        List<ProductionDailyMetricValue> metricValues = validateAndBuildMetrics(
                section.getId(), definition, dto.getMetricValues(), operatorId, now);
        List<ProductionDailyProductLine> productLines = validateAndBuildProducts(
                section.getId(), definition, dto.getProductLines(), operatorId, now);

        metricValueMapper.deleteBySectionId(section.getId());
        for (ProductionDailyMetricValue value : metricValues) {
            metricValueMapper.insert(value);
        }
        productLineMapper.deleteBySectionId(section.getId());
        for (ProductionDailyProductLine line : productLines) {
            productLineMapper.insert(line);
        }
        reportMapper.touchDraft(report.getId(), operatorId, operatorName, now);
        return getReport(reportDate);
    }

    @Override
    @Transactional
    public ProductionDailyReportVO submitSection(LocalDate reportDate, String departmentCode,
                                                 Integer operatorId, String operatorName) {
        SectionDefinition definition = requireSection(departmentCode);
        LocalDateTime now = LocalDateTime.now();
        ProductionDailyReport report = ensureReport(reportDate, operatorId, operatorName, now);
        ProductionDailyReportSection section = ensureSection(report.getId(), definition, operatorId, operatorName, now);
        sectionMapper.submitSection(section.getId(), operatorId, operatorName, now);
        return getReport(reportDate);
    }

    @Override
    @Transactional
    public ProductionDailyReportVO submitReport(LocalDate reportDate, Integer operatorId, String operatorName) {
        LocalDateTime now = LocalDateTime.now();
        ProductionDailyReport report = ensureReport(reportDate, operatorId, operatorName, now);
        for (SectionDefinition definition : ProductionDailyReportCatalog.sections()) {
            ensureSection(report.getId(), definition, operatorId, operatorName, now);
        }
        sectionMapper.submitByReportId(report.getId(), operatorId, operatorName, now);
        reportMapper.submitReport(report.getId(), operatorId, operatorName, now);
        return getReport(reportDate);
    }

    @Override
    public List<ProductionDailyProductOptionVO> listProductOptions(String status, String type, String name) {
        return productMapper.selectProductsByName(blankToNull(name), blankToNull(type), blankToNull(status)).stream()
                .map(product -> {
                    ProductionDailyProductOptionVO vo = new ProductionDailyProductOptionVO();
                    vo.setId(product.getId());
                    vo.setProductName(product.getProductName());
                    vo.setProductStatus(product.getStatus());
                    vo.setProductType(product.getProductType());
                    vo.setPackagingMethod(product.getPackagingMethod());
                    return vo;
                })
                .toList();
    }

    @Override
    public ExportedFile exportReport(LocalDate reportDate) {
        ProductionDailyReportVO report = getReport(reportDate);
        return new ExportedFile("来冰公司" + FILE_DATE.format(reportDate) + "生产日报表.xlsx",
                xlsxExporter.export(report));
    }

    @Override
    @Transactional
    public ProductionDailyReportImportVO importReport(MultipartFile file, Integer operatorId, String operatorName) {
        validateImportFile(file);
        ParsedReport imported;
        try (InputStream inputStream = file.getInputStream()) {
            imported = xlsxImporter.parse(inputStream);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_IMPORT_INVALID.getCode(),
                    "无法读取Excel文件");
        }

        Map<String, List<Product>> productsByName = productMapper.selectProductsByName(null, null, null).stream()
                .collect(Collectors.groupingBy(
                        product -> ProductionDailyReportXlsxImporter.normalize(product.getProductName()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ResolvedImportSection> resolvedSections = new ArrayList<>();
        int unmatchedCount = 0;

        for (ParsedSection importedSection : imported.sections()) {
            SectionDefinition sectionDefinition = requireSection(importedSection.departmentCode());
            Map<String, GroupDefinition> groups = sectionDefinition.groups().stream()
                    .filter(GroupDefinition::productGroup)
                    .collect(Collectors.toMap(GroupDefinition::code, Function.identity()));
            List<ResolvedImportProduct> resolvedProducts = new ArrayList<>();
            for (ParsedProductLine importedProduct : importedSection.productLines()) {
                GroupDefinition group = groups.get(importedProduct.categoryCode());
                List<Product> matches = productsByName.getOrDefault(
                                ProductionDailyReportXlsxImporter.normalize(importedProduct.productName()), List.of())
                        .stream()
                        .filter(product -> group != null && group.allowedProductStatuses().contains(product.getStatus()))
                        .toList();
                Product matchedProduct = matches.size() == 1 ? matches.get(0) : null;
                if (matchedProduct == null) {
                    unmatchedCount++;
                }
                resolvedProducts.add(new ResolvedImportProduct(importedProduct, matchedProduct));
            }
            resolvedSections.add(new ResolvedImportSection(importedSection, resolvedProducts));
        }

        ProductionDailyReportVO current = getReport(imported.reportDate());
        if (unmatchedCount > 0) {
            return new ProductionDailyReportImportVO(false, unmatchedCount,
                    buildImportPreview(current, imported, resolvedSections));
        }

        ProductionDailyReportVO saved = persistImportedReport(current, imported, resolvedSections,
                operatorId, operatorName);
        return new ProductionDailyReportImportVO(true, 0, saved);
    }

    @Override
    @Transactional
    public ProductionDailyReportVO confirmImport(ProductionDailyReportImportConfirmDTO dto,
                                                 Integer operatorId, String operatorName) {
        if (dto == null || dto.getReportDate() == null) {
            throw invalidImport("导入内容缺少日报日期");
        }
        Map<String, ProductionDailyReportImportSectionDTO> sectionInputs = new LinkedHashMap<>();
        for (ProductionDailyReportImportSectionDTO section : safeList(dto.getSections())) {
            if (sectionInputs.put(section.getDepartmentCode(), section) != null) {
                throw invalidImport("导入内容存在重复车间");
            }
        }
        Set<String> expectedSections = ProductionDailyReportCatalog.sections().stream()
                .map(SectionDefinition::code)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!sectionInputs.keySet().equals(expectedSections)) {
            throw invalidImport("导入内容必须包含完整的四个车间");
        }

        ProductionDailyReportHeaderSaveDTO header = new ProductionDailyReportHeaderSaveDTO();
        header.setVersion(dto.getVersion());
        header.setPreparedDate(dto.getPreparedDate());
        header.setPreparedByName(dto.getPreparedByName());
        saveHeader(dto.getReportDate(), header, operatorId, operatorName);

        for (SectionDefinition definition : ProductionDailyReportCatalog.sections()) {
            ProductionDailyReportImportSectionDTO importedSection = sectionInputs.get(definition.code());
            ProductionDailyReportSectionSaveDTO sectionInput = new ProductionDailyReportSectionSaveDTO();
            sectionInput.setVersion(importedSection.getVersion());
            sectionInput.setMetricValues(importedSection.getMetricValues());
            sectionInput.setProductLines(importedSection.getProductLines());
            saveSection(dto.getReportDate(), definition.code(), sectionInput,
                    operatorId, operatorName);
        }
        return getReport(dto.getReportDate());
    }

    private ProductionDailyReportVO persistImportedReport(
            ProductionDailyReportVO current, ParsedReport imported,
            List<ResolvedImportSection> resolvedSections,
            Integer operatorId, String operatorName) {
        ProductionDailyReportHeaderSaveDTO header = new ProductionDailyReportHeaderSaveDTO();
        header.setVersion(current.getVersion());
        header.setPreparedDate(imported.preparedDate());
        header.setPreparedByName(imported.preparedByName());
        saveHeader(imported.reportDate(), header, operatorId, operatorName);

        Map<String, Integer> currentSectionVersions = current.getSections().stream()
                .collect(Collectors.toMap(ProductionDailyReportSectionVO::getDepartmentCode,
                        ProductionDailyReportSectionVO::getVersion));
        for (ResolvedImportSection resolvedSection : resolvedSections) {
            ProductionDailyReportSectionSaveDTO sectionInput = new ProductionDailyReportSectionSaveDTO();
            sectionInput.setVersion(currentSectionVersions.getOrDefault(
                    resolvedSection.section().departmentCode(), 0));
            sectionInput.setMetricValues(new ArrayList<>(resolvedSection.section().metricValues()));
            sectionInput.setProductLines(resolvedSection.products().stream()
                    .map(product -> toProductInput(product.imported(), product.product().getId()))
                    .toList());
            saveSection(imported.reportDate(), resolvedSection.section().departmentCode(), sectionInput,
                    operatorId, operatorName);
        }
        return getReport(imported.reportDate());
    }

    private ProductionDailyReportVO buildImportPreview(
            ProductionDailyReportVO preview, ParsedReport imported,
            List<ResolvedImportSection> resolvedSections) {
        preview.setPreparedDate(imported.preparedDate());
        preview.setPreparedByName(imported.preparedByName());
        preview.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);

        Map<String, ProductionDailyReportSectionVO> sections = preview.getSections().stream()
                .collect(Collectors.toMap(ProductionDailyReportSectionVO::getDepartmentCode, Function.identity()));
        for (ResolvedImportSection resolvedSection : resolvedSections) {
            ProductionDailyReportSectionVO section = sections.get(resolvedSection.section().departmentCode());
            section.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);
            Map<String, ProductionDailyMetricValueVO> metrics = section.getGroups().stream()
                    .flatMap(group -> group.getMetricValues().stream())
                    .collect(Collectors.toMap(ProductionDailyMetricValueVO::getMetricCode, Function.identity()));
            metrics.values().forEach(this::clearMetricValue);
            for (ProductionDailyMetricValueDTO importedMetric : resolvedSection.section().metricValues()) {
                ProductionDailyMetricValueVO metric = metrics.get(importedMetric.getMetricCode());
                if (metric != null) {
                    copyMetricValue(importedMetric, metric);
                }
            }

            Map<String, ProductionDailyReportGroupVO> productGroups = section.getGroups().stream()
                    .filter(ProductionDailyReportGroupVO::isProductGroup)
                    .collect(Collectors.toMap(ProductionDailyReportGroupVO::getGroupCode, Function.identity()));
            productGroups.values().forEach(group -> group.setProductLines(new ArrayList<>()));
            for (ResolvedImportProduct product : resolvedSection.products()) {
                ProductionDailyReportGroupVO group = productGroups.get(product.imported().categoryCode());
                if (group != null) {
                    group.getProductLines().add(toImportPreviewLine(product));
                }
            }
        }
        return preview;
    }

    private void clearMetricValue(ProductionDailyMetricValueVO metric) {
        metric.setDailyActual(null);
        metric.setConvertedTons(null);
        metric.setMonthQuantity(null);
        metric.setMonthTons(null);
        metric.setYearTons(null);
        metric.setRemark(null);
    }

    private void copyMetricValue(ProductionDailyMetricValueDTO source, ProductionDailyMetricValueVO target) {
        target.setDailyActual(source.getDailyActual());
        target.setConvertedTons(source.getConvertedTons());
        target.setMonthQuantity(source.getMonthQuantity());
        target.setMonthTons(source.getMonthTons());
        target.setYearTons(source.getYearTons());
        target.setRemark(source.getRemark());
    }

    private ProductionDailyProductLineVO toImportPreviewLine(ResolvedImportProduct resolved) {
        ParsedProductLine imported = resolved.imported();
        Product product = resolved.product();
        ProductionDailyProductLineVO line = new ProductionDailyProductLineVO();
        line.setCategoryCode(imported.categoryCode());
        line.setProductId(product == null ? null : product.getId());
        line.setProductName(product == null ? imported.productName() : product.getProductName());
        line.setImportedProductName(imported.productName());
        line.setImportError(product == null ? "未找到匹配产品" : null);
        line.setProductStatus(product == null ? null : product.getStatus());
        line.setProductType(product == null ? null : product.getProductType());
        line.setUnit(ProductionDailyReportCatalog.PRODUCT_UNIT);
        line.setDailyActual(imported.dailyActual());
        line.setConvertedTons(imported.convertedTons());
        line.setMonthQuantity(imported.monthQuantity());
        line.setMonthTons(imported.monthTons());
        line.setYearTons(imported.yearTons());
        line.setRemark(imported.remark());
        line.setDisplayOrder(imported.displayOrder());
        return line;
    }

    private ProductionDailyProductLineDTO toProductInput(ParsedProductLine imported, Integer productId) {
        ProductionDailyProductLineDTO input = new ProductionDailyProductLineDTO();
        input.setCategoryCode(imported.categoryCode());
        input.setProductId(productId);
        input.setUnit(ProductionDailyReportCatalog.PRODUCT_UNIT);
        input.setDailyActual(imported.dailyActual());
        input.setConvertedTons(imported.convertedTons());
        input.setMonthQuantity(imported.monthQuantity());
        input.setMonthTons(imported.monthTons());
        input.setYearTons(imported.yearTons());
        input.setRemark(imported.remark());
        input.setDisplayOrder(imported.displayOrder());
        return input;
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_IMPORT_INVALID.getCode(),
                    "请选择Excel文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(xlsx|xls)$")) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_IMPORT_INVALID.getCode(),
                    "仅支持.xlsx或.xls文件");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_IMPORT_INVALID.getCode(),
                    "Excel文件不能超过10MB");
        }
    }

    private BusinessException invalidImport(String message) {
        return new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_IMPORT_INVALID.getCode(), message);
    }

    private ProductionDailyReportVO assembleReport(LocalDate reportDate, ProductionDailyReport report) {
        ProductionDailyReportVO vo = new ProductionDailyReportVO();
        vo.setReportDate(reportDate);
        vo.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);
        vo.setVersion(0);

        Map<String, ProductionDailyReportSection> persistedSections = Map.of();
        if (report != null) {
            vo.setId(report.getId());
            vo.setPreparedDate(report.getPreparedDate());
            vo.setPreparedByName(report.getPreparedByName());
            vo.setStatus(report.getStatus());
            vo.setVersion(report.getVersion());
            vo.setUpdatedByName(report.getUpdatedByName());
            vo.setUpdatedAt(report.getUpdatedAt());
            vo.setSubmittedByName(report.getSubmittedByName());
            vo.setSubmittedAt(report.getSubmittedAt());
            persistedSections = sectionMapper.selectByReportId(report.getId()).stream()
                    .collect(Collectors.toMap(ProductionDailyReportSection::getDepartmentCode,
                            Function.identity(), (left, right) -> left, LinkedHashMap::new));
        }

        for (SectionDefinition definition : ProductionDailyReportCatalog.sections()) {
            vo.getSections().add(assembleSection(definition, persistedSections.get(definition.code())));
        }
        return vo;
    }

    private ProductionDailyReportSectionVO assembleSection(SectionDefinition definition,
                                                           ProductionDailyReportSection section) {
        ProductionDailyReportSectionVO vo = new ProductionDailyReportSectionVO();
        vo.setDepartmentCode(definition.code());
        vo.setDepartmentName(definition.name());
        vo.setStatus(section == null ? ProductionDailyReportCatalog.STATUS_DRAFT : section.getStatus());
        vo.setVersion(section == null ? 0 : section.getVersion());
        if (section != null) {
            vo.setUpdatedByName(section.getUpdatedByName());
            vo.setUpdatedAt(section.getUpdatedAt());
            vo.setSubmittedByName(section.getSubmittedByName());
            vo.setSubmittedAt(section.getSubmittedAt());
        }

        Map<String, ProductionDailyMetricValue> savedMetrics = section == null
                ? Map.of()
                : metricValueMapper.selectBySectionId(section.getId()).stream()
                .collect(Collectors.toMap(ProductionDailyMetricValue::getMetricCode, Function.identity()));
        Map<String, List<ProductionDailyProductLine>> productsByCategory = section == null
                ? Map.of()
                : productLineMapper.selectBySectionId(section.getId()).stream()
                .collect(Collectors.groupingBy(ProductionDailyProductLine::getCategoryCode));

        for (GroupDefinition group : definition.groups()) {
            ProductionDailyReportGroupVO groupVO = new ProductionDailyReportGroupVO();
            groupVO.setGroupCode(group.code());
            groupVO.setGroupName(group.name());
            groupVO.setProductGroup(group.productGroup());
            groupVO.setAllowedProductStatuses(new ArrayList<>(group.allowedProductStatuses()));
            for (MetricDefinition metric : group.metrics()) {
                groupVO.getMetricValues().add(toMetricVO(metric, savedMetrics.get(metric.code())));
            }
            productsByCategory.getOrDefault(group.code(), List.of()).stream()
                    .sorted(Comparator.comparing(ProductionDailyProductLine::getDisplayOrder,
                            Comparator.nullsLast(Integer::compareTo)).thenComparing(ProductionDailyProductLine::getId))
                    .map(this::toProductLineVO)
                    .forEach(groupVO.getProductLines()::add);
            vo.getGroups().add(groupVO);
        }
        return vo;
    }

    private ProductionDailyMetricValueVO toMetricVO(MetricDefinition definition,
                                                     ProductionDailyMetricValue saved) {
        ProductionDailyMetricValueVO vo = new ProductionDailyMetricValueVO();
        vo.setMetricCode(definition.code());
        vo.setMetricName(definition.name());
        vo.setUnit(definition.unit());
        vo.setPosition(definition.position());
        vo.setDisplayOrder(definition.displayOrder());
        if (saved != null) {
            vo.setDailyActual(saved.getDailyActual());
            vo.setConvertedTons(saved.getConvertedTons());
            vo.setMonthQuantity(saved.getMonthQuantity());
            vo.setMonthTons(saved.getMonthTons());
            vo.setYearTons(saved.getYearTons());
            vo.setRemark(saved.getRemark());
        }
        return vo;
    }

    private ProductionDailyProductLineVO toProductLineVO(ProductionDailyProductLine line) {
        ProductionDailyProductLineVO vo = new ProductionDailyProductLineVO();
        vo.setId(line.getId());
        vo.setCategoryCode(line.getCategoryCode());
        vo.setProductId(line.getProductId());
        vo.setProductName(line.getProductNameSnapshot());
        vo.setProductStatus(line.getProductStatusSnapshot());
        vo.setProductType(line.getProductTypeSnapshot());
        vo.setUnit(ProductionDailyReportCatalog.PRODUCT_UNIT);
        vo.setDailyActual(line.getDailyActual());
        vo.setConvertedTons(line.getConvertedTons());
        vo.setMonthQuantity(line.getMonthQuantity());
        vo.setMonthTons(line.getMonthTons());
        vo.setYearTons(line.getYearTons());
        vo.setRemark(line.getRemark());
        vo.setDisplayOrder(line.getDisplayOrder());
        return vo;
    }

    private List<ProductionDailyMetricValue> validateAndBuildMetrics(
            Long sectionId, SectionDefinition sectionDefinition,
            List<ProductionDailyMetricValueDTO> inputs, Integer operatorId, LocalDateTime now) {
        Map<String, MetricContext> allowed = new HashMap<>();
        for (GroupDefinition group : sectionDefinition.groups()) {
            for (MetricDefinition metric : group.metrics()) {
                allowed.put(metric.code(), new MetricContext(group, metric));
            }
        }
        Set<String> seen = new HashSet<>();
        List<ProductionDailyMetricValue> result = new ArrayList<>();
        for (ProductionDailyMetricValueDTO input : safeList(inputs)) {
            MetricContext context = allowed.get(input.getMetricCode());
            if (context == null || !seen.add(input.getMetricCode())) {
                throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_METRIC_INVALID);
            }
            ProductionDailyMetricValue value = new ProductionDailyMetricValue();
            value.setReportSectionId(sectionId);
            value.setMetricCode(context.metric().code());
            value.setMetricNameSnapshot(context.metric().name());
            value.setGroupCode(context.group().code());
            value.setGroupNameSnapshot(context.group().name());
            value.setUnitSnapshot(context.metric().unit());
            value.setDailyActual(input.getDailyActual());
            value.setConvertedTons(input.getConvertedTons());
            value.setMonthQuantity(input.getMonthQuantity());
            value.setMonthTons(input.getMonthTons());
            value.setYearTons(input.getYearTons());
            value.setRemark(blankToNull(input.getRemark()));
            value.setDisplayOrder(context.metric().displayOrder());
            value.setUpdatedBy(operatorId);
            value.setUpdatedAt(now);
            result.add(value);
        }
        return result;
    }

    private List<ProductionDailyProductLine> validateAndBuildProducts(
            Long sectionId, SectionDefinition sectionDefinition,
            List<ProductionDailyProductLineDTO> inputs, Integer operatorId, LocalDateTime now) {
        Map<String, GroupDefinition> allowedGroups = sectionDefinition.groups().stream()
                .filter(GroupDefinition::productGroup)
                .collect(Collectors.toMap(GroupDefinition::code, Function.identity()));
        List<ProductionDailyProductLineDTO> meaningful = safeList(inputs).stream()
                .filter(this::hasProductLineContent)
                .toList();
        if (meaningful.stream().anyMatch(input -> input.getProductId() == null || blankToNull(input.getCategoryCode()) == null)) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_PRODUCT_INVALID);
        }

        Set<Integer> productIds = meaningful.stream().map(ProductionDailyProductLineDTO::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<String, Integer> nextOrder = new HashMap<>();
        List<ProductionDailyProductLine> result = new ArrayList<>();
        for (ProductionDailyProductLineDTO input : meaningful) {
            GroupDefinition group = allowedGroups.get(input.getCategoryCode());
            Product product = products.get(input.getProductId());
            if (group == null || product == null || !group.allowedProductStatuses().contains(product.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_PRODUCT_INVALID);
            }
            int order = input.getDisplayOrder() == null || input.getDisplayOrder() < 1
                    ? nextOrder.merge(group.code(), 1, Integer::sum)
                    : input.getDisplayOrder();
            nextOrder.merge(group.code(), order, Math::max);

            ProductionDailyProductLine line = new ProductionDailyProductLine();
            line.setReportSectionId(sectionId);
            line.setCategoryCode(group.code());
            line.setCategoryNameSnapshot(group.name());
            line.setProductId(product.getId());
            line.setProductNameSnapshot(product.getProductName());
            line.setProductStatusSnapshot(product.getStatus());
            line.setProductTypeSnapshot(product.getProductType());
            line.setUnit(ProductionDailyReportCatalog.PRODUCT_UNIT);
            line.setDailyActual(input.getDailyActual());
            line.setConvertedTons(input.getConvertedTons());
            line.setMonthQuantity(input.getMonthQuantity());
            line.setMonthTons(input.getMonthTons());
            line.setYearTons(input.getYearTons());
            line.setRemark(blankToNull(input.getRemark()));
            line.setDisplayOrder(order);
            line.setCreatedBy(operatorId);
            line.setCreatedAt(now);
            line.setUpdatedBy(operatorId);
            line.setUpdatedAt(now);
            result.add(line);
        }
        return result;
    }

    private ProductionDailyReport ensureReport(LocalDate reportDate, Integer operatorId,
                                               String operatorName, LocalDateTime now) {
        if (reportDate == null) {
            throw new BusinessException(400, "日报日期不能为空");
        }
        ProductionDailyReport existing = reportMapper.selectByReportDate(reportDate);
        if (existing != null) {
            return existing;
        }
        ProductionDailyReport report = newReport(reportDate, operatorId, operatorName, now);
        try {
            reportMapper.insert(report);
            return report;
        } catch (DuplicateKeyException exception) {
            ProductionDailyReport concurrent = reportMapper.selectByReportDate(reportDate);
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
    }

    private ProductionDailyReport newReport(LocalDate reportDate, Integer operatorId,
                                            String operatorName, LocalDateTime now) {
        ProductionDailyReport report = new ProductionDailyReport();
        report.setReportDate(reportDate);
        report.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);
        report.setVersion(0);
        report.setCreatedBy(operatorId);
        report.setCreatedByName(operatorName);
        report.setCreatedAt(now);
        return report;
    }

    private ProductionDailyReportSection ensureSection(Long reportId, SectionDefinition definition,
                                                       Integer operatorId, String operatorName, LocalDateTime now) {
        ProductionDailyReportSection existing = sectionMapper.selectByReportAndDepartment(reportId, definition.code());
        if (existing != null) {
            return existing;
        }
        ProductionDailyReportSection section = new ProductionDailyReportSection();
        section.setReportId(reportId);
        section.setDepartmentCode(definition.code());
        section.setDepartmentNameSnapshot(definition.name());
        section.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);
        section.setVersion(0);
        section.setUpdatedBy(operatorId);
        section.setUpdatedByName(operatorName);
        section.setUpdatedAt(now);
        try {
            sectionMapper.insert(section);
            return section;
        } catch (DuplicateKeyException exception) {
            ProductionDailyReportSection concurrent = sectionMapper.selectByReportAndDepartment(reportId, definition.code());
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
    }

    private SectionDefinition requireSection(String departmentCode) {
        return ProductionDailyReportCatalog.findSection(departmentCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_SECTION_INVALID));
    }

    private boolean hasProductLineContent(ProductionDailyProductLineDTO input) {
        return input != null && (input.getProductId() != null
                || blankToNull(input.getCategoryCode()) != null
                || input.getDailyActual() != null
                || input.getConvertedTons() != null
                || input.getMonthQuantity() != null
                || input.getMonthTons() != null
                || input.getYearTons() != null
                || blankToNull(input.getRemark()) != null);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record MetricContext(GroupDefinition group, MetricDefinition metric) {
    }

    private record ResolvedImportSection(ParsedSection section, List<ResolvedImportProduct> products) {
    }

    private record ResolvedImportProduct(ParsedProductLine imported, Product product) {
    }
}

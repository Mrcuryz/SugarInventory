package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.InventoryDistributionVO;
import com.Laibin.SugarInventory.mapper.InventoryDistributionMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.model.InventoryDistributionAggregateRow;
import com.Laibin.SugarInventory.mapper.model.InventoryDistributionGroupRow;
import com.Laibin.SugarInventory.service.impl.InventoryDistributionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryDistributionServiceImplTest {
    private InventoryDistributionMapper distributionMapper;
    private ProductMapper productMapper;
    private WarehouseMapper warehouseMapper;
    private InventoryDistributionService service;

    @BeforeEach
    void setUp() {
        distributionMapper = mock(InventoryDistributionMapper.class);
        productMapper = mock(ProductMapper.class);
        warehouseMapper = mock(WarehouseMapper.class);
        service = new InventoryDistributionServiceImpl(distributionMapper, productMapper, warehouseMapper);
    }

    @Test
    void aggregatesAndNormalizesSingleProductByWarehouse() {
        when(productMapper.selectById(84)).thenReturn(product());
        when(distributionMapper.selectAggregate(any())).thenReturn(aggregate(13, 30, 550, 2, 1, 13, 40, 40));
        when(distributionMapper.selectGroups(any())).thenReturn(List.of(
                warehouseGroup("2", 3, 20, 140, 3, LocalDate.of(2026, 7, 6)),
                warehouseGroup("3号库位", 10, 10, 410, 10, LocalDate.of(2026, 7, 5))));

        InventoryDistributionVO result = service.getDistribution(singleProductQuery(20));

        assertThat(result.getProductLabel()).isEqualTo("黄冰糖（袋） 25kg/件 40件/板");
        assertThat(result.getNormalizedPallets()).isEqualTo(13);
        assertThat(result.getNormalizedLoosePieces()).isEqualTo(30);
        assertThat(result.getTotalEquivalentPieces()).isEqualTo(550);
        assertThat(result.getTotalStockText()).isEqualTo("13板30件");
        assertThat(result.getTotalWeightText()).isEqualTo("13750kg");
        assertThat(result.getGroups()).extracting("warehouseLabel")
                .containsExactly("2号库位", "3号库位");
        assertThat(result.getGroups().get(0).getStockText()).isEqualTo("3板20件");
        assertThat(result.getGroups().get(0).getPercentageText()).isEqualTo("25.5%");
        assertThat(result.toString()).doesNotContain("productId", "warehouseId", "inventoryId");
    }

    @Test
    void returnsSafeEmptyResultUsingSelectedProductScale() {
        when(productMapper.selectById(84)).thenReturn(product());
        when(distributionMapper.selectAggregate(any())).thenReturn(new InventoryDistributionAggregateRow());
        when(distributionMapper.selectGroups(any())).thenReturn(List.of());

        InventoryDistributionVO result = service.getDistribution(singleProductQuery(null));

        assertThat(result.getTotalStockText()).isEqualTo("0板0件");
        assertThat(result.getNormalizedPallets()).isZero();
        assertThat(result.getGroups()).isEmpty();
        ArgumentCaptor<InventoryDistributionQueryDTO> captor = ArgumentCaptor.forClass(InventoryDistributionQueryDTO.class);
        verify(distributionMapper).selectGroups(captor.capture());
        assertThat(captor.getValue().getLimit()).isEqualTo(20);
    }

    @Test
    void supportsProductTypeScopeFiltersAndMixedScaleGrouping() {
        InventoryDistributionQueryDTO query = baseQuery("PRODUCT_TYPE_GROUP", "product");
        query.getProductScope().setProductType("冰糖");
        query.getStatusFilter().setProductStatuses(List.of("成品"));
        query.getStatusFilter().setAssayStatus("PASS");
        query.getStatusFilter().setEntryDateFrom(LocalDate.of(2026, 7, 1));
        when(distributionMapper.selectAggregate(any())).thenReturn(aggregate(13, 50, 700, 3, 2, 14, 40, 50));
        InventoryDistributionGroupRow group = productGroup("黄冰糖", "袋", 40, 550);
        when(distributionMapper.selectGroups(any())).thenReturn(List.of(group));

        InventoryDistributionVO result = service.getDistribution(query);

        assertThat(result.getScopeLabel()).isEqualTo("全部冰糖大类");
        assertThat(result.getGroupBy()).isEqualTo("product");
        assertThat(result.getTotalStockText()).isEqualTo("700件（跨规格）");
        assertThat(result.getNormalizedPallets()).isNull();
        assertThat(result.getNotes()).anyMatch(note -> note.contains("不同每板件数"));
        assertThat(result.getNotes()).anyMatch(note -> note.contains("过滤条件"));
        assertThat(result.getGroups().get(0).getProductLabel()).contains("黄冰糖（袋）");
        assertThat(result.getGroups().get(0).getCanonicalProductName()).isEqualTo("黄冰糖");
        assertThat(result.getCalculationNote()).contains("一个二维码板位", "不再叠加整板", "产品管理当前配置");
        assertThat(result.getNotes()).contains(result.getCalculationNote());
        assertThat(result.getNotes()).noneMatch(note -> note.contains("尚未由业务负责人确认"));
    }

    @Test
    void validatesSingleWarehouseAndKeepsItInMapperQuery() {
        InventoryDistributionQueryDTO query = singleProductQuery(10);
        query.getWarehouseScope().setType("SINGLE_WAREHOUSE");
        query.getWarehouseScope().setWarehouseId(7);
        when(productMapper.selectById(84)).thenReturn(product());
        Warehouse warehouse = new Warehouse();
        warehouse.setId(7);
        when(warehouseMapper.selectById(7)).thenReturn(warehouse);
        when(distributionMapper.selectAggregate(any())).thenReturn(aggregate(0, 0, 0, 0, 0, 0, 40, 40));
        when(distributionMapper.selectGroups(any())).thenReturn(List.of());

        service.getDistribution(query);

        verify(warehouseMapper).selectById(7);
        ArgumentCaptor<InventoryDistributionQueryDTO> captor = ArgumentCaptor.forClass(InventoryDistributionQueryDTO.class);
        verify(distributionMapper).selectAggregate(captor.capture());
        assertThat(captor.getValue().getWarehouseScope().getWarehouseId()).isEqualTo(7);
    }

    @Test
    void rejectsInvalidScopeFilterDateAndLimit() {
        InventoryDistributionQueryDTO invalidScope = baseQuery("PRODUCT_TYPE_GROUP", "warehouse");
        assertThatThrownBy(() -> service.getDistribution(invalidScope))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("产品大类");

        InventoryDistributionQueryDTO invalidFilter = baseQuery("ALL", "warehouse");
        invalidFilter.getStatusFilter().setProductStatuses(List.of("已删除"));
        assertThatThrownBy(() -> service.getDistribution(invalidFilter))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("产品状态");

        InventoryDistributionQueryDTO invalidDates = baseQuery("ALL", "warehouse");
        invalidDates.getStatusFilter().setEntryDateFrom(LocalDate.of(2026, 7, 7));
        invalidDates.getStatusFilter().setEntryDateTo(LocalDate.of(2026, 7, 1));
        assertThatThrownBy(() -> service.getDistribution(invalidDates))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期");

        assertThatThrownBy(() -> service.getDistribution(singleProductQuery(101)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void rejectsUnknownProductAndWarehouse() {
        when(productMapper.selectById(84)).thenReturn(null);
        assertThatThrownBy(() -> service.getDistribution(singleProductQuery(20)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("产品不存在");

        InventoryDistributionQueryDTO query = baseQuery("ALL", "warehouse");
        query.getWarehouseScope().setType("SINGLE_WAREHOUSE");
        query.getWarehouseScope().setWarehouseId(999);
        when(warehouseMapper.selectById(999)).thenReturn(null);
        assertThatThrownBy(() -> service.getDistribution(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("找不到库位");
    }

    private InventoryDistributionQueryDTO singleProductQuery(Integer limit) {
        InventoryDistributionQueryDTO query = baseQuery("SINGLE_PRODUCT", "warehouse");
        query.getProductScope().setProductId(84);
        query.setLimit(limit);
        return query;
    }

    private InventoryDistributionQueryDTO baseQuery(String productScopeType, String groupBy) {
        InventoryDistributionQueryDTO.ProductScope productScope = new InventoryDistributionQueryDTO.ProductScope();
        productScope.setType(productScopeType);
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope = new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("ALL");
        InventoryDistributionQueryDTO query = new InventoryDistributionQueryDTO();
        query.setProductScope(productScope);
        query.setWarehouseScope(warehouseScope);
        query.setGroupBy(groupBy);
        return query;
    }

    private Product product() {
        Product product = new Product();
        product.setId(84);
        product.setProductName("黄冰糖");
        product.setPackagingMethod("袋");
        product.setWeightPerPiece(new BigDecimal("25.0"));
        product.setPiecesPerPallet(40);
        return product;
    }

    private InventoryDistributionAggregateRow aggregate(long pallets, long loosePieces, long equivalentPieces,
                                                         long warehouses, long products, long codes,
                                                         int minPiecesPerPallet, int maxPiecesPerPallet) {
        InventoryDistributionAggregateRow row = new InventoryDistributionAggregateRow();
        row.setRawFullPallets(pallets);
        row.setRawLoosePieces(loosePieces);
        row.setTotalEquivalentPieces(equivalentPieces);
        row.setTotalWeight(BigDecimal.valueOf(equivalentPieces).multiply(new BigDecimal("25")));
        row.setWarehouseCount(warehouses);
        row.setProductCount(products);
        row.setPalletCount(codes);
        row.setMinPiecesPerPallet(minPiecesPerPallet);
        row.setMaxPiecesPerPallet(maxPiecesPerPallet);
        return row;
    }

    private InventoryDistributionGroupRow warehouseGroup(String warehouse, long pallets, long loosePieces,
                                                          long equivalentPieces, long codes,
                                                          LocalDate latestInboundTime) {
        InventoryDistributionGroupRow row = new InventoryDistributionGroupRow();
        row.setWarehouseName(warehouse);
        row.setRawFullPallets(pallets);
        row.setRawLoosePieces(loosePieces);
        row.setTotalEquivalentPieces(equivalentPieces);
        row.setTotalWeight(BigDecimal.valueOf(equivalentPieces).multiply(new BigDecimal("25")));
        row.setPalletCount(codes);
        row.setWarehouseCount(1L);
        row.setProductCount(1L);
        row.setMinPiecesPerPallet(40);
        row.setMaxPiecesPerPallet(40);
        row.setLatestInboundTime(latestInboundTime);
        return row;
    }

    private InventoryDistributionGroupRow productGroup(String name, String packaging, int piecesPerPallet,
                                                        long equivalentPieces) {
        InventoryDistributionGroupRow row = new InventoryDistributionGroupRow();
        row.setProductName(name);
        row.setPackagingMethod(packaging);
        row.setWeightPerPiece(new BigDecimal("25"));
        row.setPiecesPerPallet(piecesPerPallet);
        row.setTotalEquivalentPieces(equivalentPieces);
        row.setTotalWeight(BigDecimal.valueOf(equivalentPieces).multiply(new BigDecimal("25")));
        row.setProductCount(1L);
        row.setWarehouseCount(2L);
        row.setMinPiecesPerPallet(piecesPerPallet);
        row.setMaxPiecesPerPallet(piecesPerPallet);
        return row;
    }
}

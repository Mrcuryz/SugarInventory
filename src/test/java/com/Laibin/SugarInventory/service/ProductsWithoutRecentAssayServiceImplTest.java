package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.ProductsWithoutRecentAssayVO;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ProductsWithoutRecentAssayMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.model.ProductsWithoutRecentAssayAggregateRow;
import com.Laibin.SugarInventory.mapper.model.ProductsWithoutRecentAssayGroupRow;
import com.Laibin.SugarInventory.service.impl.ProductsWithoutRecentAssayServiceImpl;
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

class ProductsWithoutRecentAssayServiceImplTest {
    private ProductsWithoutRecentAssayMapper mapper;
    private ProductMapper productMapper;
    private WarehouseMapper warehouseMapper;
    private ProductsWithoutRecentAssayService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductsWithoutRecentAssayMapper.class);
        productMapper = mock(ProductMapper.class);
        warehouseMapper = mock(WarehouseMapper.class);
        service = new ProductsWithoutRecentAssayServiceImpl(mapper, productMapper, warehouseMapper);
    }

    @Test
    void queriesCurrentInventoryGroupsWithoutRecentAssay() {
        when(productMapper.selectById(84)).thenReturn(product());
        when(warehouseMapper.selectById(1)).thenReturn(warehouse());
        when(mapper.selectAggregate(any())).thenReturn(aggregate());
        when(mapper.selectGroups(any())).thenReturn(List.of(group()));

        ProductsWithoutRecentAssayVO result = service.queryProductsWithoutRecentAssay(query());

        assertThat(result.getScopeLabel()).contains("黄冰糖（袋）");
        assertThat(result.getWarehouseScopeLabel()).isEqualTo("1号库位");
        assertThat(result.getDateRangeLabel()).isEqualTo("最近7天");
        assertThat(result.getSummaryText()).contains("1 个当前在库分组缺少有效化验");
        assertThat(result.getRiskLabels()).containsExactly("存在无有效化验库存");
        assertThat(result.getGroups()).hasSize(1);
        assertThat(result.getGroups().get(0).getRiskLabels()).containsExactly("无有效化验");
        assertThat(result.getGroups().get(0).getStockText()).isEqualTo("2板20件");
        assertThat(result.toString()).doesNotContain("productId", "warehouseId", "assayId");

        ArgumentCaptor<ProductsWithoutRecentAssayQueryDTO> captor = ArgumentCaptor.forClass(ProductsWithoutRecentAssayQueryDTO.class);
        verify(mapper).selectAggregate(captor.capture());
        assertThat(captor.getValue().getPopulation()).isEqualTo("CURRENT_INVENTORY");
        assertThat(captor.getValue().getResolvedFrom()).isNotNull();
        assertThat(captor.getValue().getResolvedTo()).isNotNull();
    }

    @Test
    void defaultsDateRangeToTodayAndRejectsUnsupportedPopulation() {
        ProductsWithoutRecentAssayQueryDTO defaultToday = query();
        defaultToday.setDateRange(null);
        when(productMapper.selectById(84)).thenReturn(product());
        when(warehouseMapper.selectById(1)).thenReturn(warehouse());
        when(mapper.selectAggregate(any())).thenReturn(new ProductsWithoutRecentAssayAggregateRow());
        when(mapper.selectGroups(any())).thenReturn(List.of());

        ProductsWithoutRecentAssayVO result = service.queryProductsWithoutRecentAssay(defaultToday);

        assertThat(result.getDateRangeLabel()).isEqualTo("今天");
        assertThat(defaultToday.getResolvedFrom()).isEqualTo(LocalDate.now());
        assertThat(defaultToday.getResolvedTo()).isEqualTo(LocalDate.now());
        assertThat(result.getNotes()).contains("未传日期范围时按今天计算。");

        ProductsWithoutRecentAssayQueryDTO invalid = query();
        invalid.setPopulation("ACTIVE_PRODUCTS");
        assertThatThrownBy(() -> service.queryProductsWithoutRecentAssay(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CURRENT_INVENTORY");
    }

    private ProductsWithoutRecentAssayQueryDTO query() {
        AssayRecordsQueryDTO.ProductScope productScope = new AssayRecordsQueryDTO.ProductScope();
        productScope.setType("SINGLE_PRODUCT");
        productScope.setProductId(84);
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope = new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("SINGLE_WAREHOUSE");
        warehouseScope.setWarehouseId(1);
        AssayRecordsQueryDTO.DateRange range = new AssayRecordsQueryDTO.DateRange();
        range.setType("LAST_DAYS");
        range.setDays(7);
        ProductsWithoutRecentAssayQueryDTO query = new ProductsWithoutRecentAssayQueryDTO();
        query.setProductScope(productScope);
        query.setWarehouseScope(warehouseScope);
        query.setDateRange(range);
        query.setGroupBy("product_warehouse");
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

    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1);
        warehouse.setWarehouseName("1");
        return warehouse;
    }

    private ProductsWithoutRecentAssayAggregateRow aggregate() {
        ProductsWithoutRecentAssayAggregateRow row = new ProductsWithoutRecentAssayAggregateRow();
        row.setRawFullPallets(2L);
        row.setRawLoosePieces(20L);
        row.setTotalEquivalentPieces(100L);
        row.setTotalWeight(new BigDecimal("2500"));
        row.setInventoryRecordCount(2L);
        row.setPalletCount(2L);
        row.setWarehouseCount(1L);
        row.setProductCount(1L);
        row.setMinPiecesPerPallet(40);
        row.setMaxPiecesPerPallet(40);
        return row;
    }

    private ProductsWithoutRecentAssayGroupRow group() {
        ProductsWithoutRecentAssayGroupRow row = new ProductsWithoutRecentAssayGroupRow();
        row.setWarehouseName("1");
        row.setProductName("黄冰糖");
        row.setPackagingMethod("袋");
        row.setWeightPerPiece(new BigDecimal("25"));
        row.setPiecesPerPallet(40);
        row.setRawFullPallets(2L);
        row.setRawLoosePieces(20L);
        row.setTotalEquivalentPieces(100L);
        row.setTotalWeight(new BigDecimal("2500"));
        row.setInventoryRecordCount(2L);
        row.setPalletCount(2L);
        row.setWarehouseCount(1L);
        row.setProductCount(1L);
        row.setMinPiecesPerPallet(40);
        row.setMaxPiecesPerPallet(40);
        row.setLatestInboundTime(LocalDate.of(2026, 7, 1));
        row.setWarehouseNames("1");
        return row;
    }
}

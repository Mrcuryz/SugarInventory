package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayStandardCoverageVO;
import com.Laibin.SugarInventory.mapper.AssayStandardCoverageMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.model.AssayStandardCoverageGroupRow;
import com.Laibin.SugarInventory.service.impl.AssayStandardCoverageServiceImpl;
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

class AssayStandardCoverageServiceImplTest {
    private AssayStandardCoverageMapper mapper;
    private ProductMapper productMapper;
    private AssayStandardCoverageService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AssayStandardCoverageMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new AssayStandardCoverageServiceImpl(mapper, productMapper);
    }

    @Test
    void queriesCurrentInventoryProductsWithoutEffectiveStandard() {
        when(productMapper.selectById(84)).thenReturn(product());
        when(mapper.selectProductWithoutStandardGroups(any())).thenReturn(List.of(group()));

        AssayStandardCoverageVO result = service.queryCoverage(query("PRODUCT_WITHOUT_STANDARD"));

        assertThat(result.getScopeLabel()).contains("黄冰糖（袋）");
        assertThat(result.getCoverageType()).isEqualTo("PRODUCT_WITHOUT_STANDARD");
        assertThat(result.getSummaryText()).contains("1 个当前在库产品未绑定有效质量标准");
        assertThat(result.getRiskLabels()).containsExactly("存在当前在库产品无有效质量标准");
        assertThat(result.getGroups()).hasSize(1);
        assertThat(result.getGroups().get(0).getCoverageLabel()).isEqualTo("未启用有效标准关系");
        assertThat(result.getGroups().get(0).getAffectedStockText()).isEqualTo("2板20件");
        assertThat(result.getNotes()).anyMatch(note -> note.contains("不等同于化验不合格"));
        assertThat(result.toString()).doesNotContain("productId", "standardId", "qualityStandardId");

        ArgumentCaptor<AssayStandardCoverageQueryDTO> captor = ArgumentCaptor.forClass(AssayStandardCoverageQueryDTO.class);
        verify(mapper).selectProductWithoutStandardGroups(captor.capture());
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
    }

    @Test
    void rejectsUnsupportedCoverageTypeUntilBusinessRuleIsConfirmed() {
        assertThatThrownBy(() -> service.queryCoverage(query("UNUSED_STANDARD")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PRODUCT_WITHOUT_STANDARD");
    }

    private AssayStandardCoverageQueryDTO query(String coverageType) {
        AssayRecordsQueryDTO.ProductScope scope = new AssayRecordsQueryDTO.ProductScope();
        scope.setType("SINGLE_PRODUCT");
        scope.setProductId(84);
        AssayStandardCoverageQueryDTO query = new AssayStandardCoverageQueryDTO();
        query.setProductScope(scope);
        query.setCoverageType(coverageType);
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

    private AssayStandardCoverageGroupRow group() {
        AssayStandardCoverageGroupRow row = new AssayStandardCoverageGroupRow();
        row.setProductId(84);
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
        row.setMinPiecesPerPallet(40);
        row.setMaxPiecesPerPallet(40);
        row.setLatestInboundTime(LocalDate.of(2026, 7, 1));
        row.setWarehouseNames("1");
        row.setRelationCount(1L);
        row.setEnabledRelationCount(0L);
        row.setActiveStandardCount(0L);
        row.setIncompleteActiveStandardCount(0L);
        return row;
    }
}

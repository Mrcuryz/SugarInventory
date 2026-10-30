package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.QualityCatalogAgentQueries;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;
import com.Laibin.SugarInventory.service.impl.QualityCatalogAgentReadServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QualityCatalogAgentReadServiceImplTest {
    @Test
    void groupCatalogBindsReferencesToAuthenticatedOwnerWithoutExposingIds() throws Exception {
        var groups = mock(AssayGroupService.class);
        var refs = mock(com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService.class);
        var row = new com.Laibin.SugarInventory.domain.vo.AssayGroupVO();
        row.setId(9); row.setStandardName("冰糖组"); row.setRelatedProducts("84,85");
        when(groups.queryAssays(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        when(refs.encodeGroup(9, 12)).thenReturn("aer_owner12.signature");
        var service = new QualityCatalogAgentReadServiceImpl(groups, mock(QualityStandardService.class),
                mock(ProductService.class), mock(ProductQualityStandardRelationService.class), refs);
        var result = service.queryAssayGroups(new QualityCatalogAgentQueries.AssayGroups(), 12);
        assertThat(result.getRecords().getFirst().getGroupRef()).isEqualTo("aer_owner12.signature");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("\"id\"", "groupId", "relatedProducts", "productId");
        verify(refs).encodeGroup(9, 12);
        assertThat(service.queryAssayGroups(new QualityCatalogAgentQueries.AssayGroups()).getRecords().getFirst().getGroupRef()).isNull();
        verifyNoMoreInteractions(refs);
    }

    @Test
    void standardCatalogOmitsInternalIdsAndDoesNotClaimReportUsage() {
        QualityStandardService standards = mock(QualityStandardService.class);
        QualityStandardVO row = new QualityStandardVO(); row.setId(99); row.setStandardCode("QS-001");
        row.setStandardName("白冰糖标准"); row.setVersion(2); row.setStatus("ENABLED"); row.setCreatedBy(88);
        when(standards.pageQualityStandards(null, null, null, 1, 20)).thenReturn(new PageResult<>(1L, List.of(row)));
        var service = new QualityCatalogAgentReadServiceImpl(mock(AssayGroupService.class), standards,
                mock(ProductService.class), mock(ProductQualityStandardRelationService.class),
                mock(com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService.class));

        var result = service.queryStandards(new QualityCatalogAgentQueries.Standards());

        assertThat(result.getRecords()).singleElement().satisfies(item -> assertThat(item.getStandardCode()).isEqualTo("QS-001"));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不表示某次化验实际采用"));
    }

    @Test
    void standardDetailRequiresExactCodeAndVersion() {
        QualityStandardService standards = mock(QualityStandardService.class);
        QualityStandardVO row = new QualityStandardVO(); row.setStandardCode("QS-001"); row.setStandardName("标准"); row.setVersion(2);
        when(standards.listQualityStandards(null, null, null)).thenReturn(List.of(row));
        var service = new QualityCatalogAgentReadServiceImpl(mock(AssayGroupService.class), standards,
                mock(ProductService.class), mock(ProductQualityStandardRelationService.class),
                mock(com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService.class));
        var query = new QualityCatalogAgentQueries.StandardDetail(); query.setStandardCode("QS-001"); query.setVersion(2);

        var result = service.getStandardDetail(query);

        assertThat(result.getStandardCode()).isEqualTo("QS-001");
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("确定性标准匹配"));
    }
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "入库请求DTO")
public class InStockRequestDTO  extends BaseInStockDTO{

    @Schema(description = "半成品DTO，包含半成品产品id、半成品生产日期、生产该批成品使用的原料数量")
    private List<SemiRecordDTO> semiRecords;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "半成品记录id列表")
@Data
public class BatchGetSemiProductRecordDTO {
    List<Integer> ids;
}

package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.mapper.model.AssayRecordRow;
import com.Laibin.SugarInventory.mapper.model.AssayRecordsSummaryRow;
import com.Laibin.SugarInventory.mapper.sql.AssayRecordsSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface AssayRecordsMapper {
    @SelectProvider(type = AssayRecordsSqlProvider.class, method = "selectSummary")
    AssayRecordsSummaryRow selectSummary(@Param("query") AssayRecordsQueryDTO query);

    @SelectProvider(type = AssayRecordsSqlProvider.class, method = "selectRecords")
    List<AssayRecordRow> selectRecords(@Param("query") AssayRecordsQueryDTO query,
                                       @Param("offset") int offset,
                                       @Param("size") int size);
}

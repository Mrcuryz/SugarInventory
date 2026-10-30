package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentAssayEntryPreview;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentAssayEntryPreviewMapper extends BaseMapper<AgentAssayEntryPreview> {
    @Select("SELECT * FROM agent_assay_entry_preview WHERE preview_ref=#{ref} FOR UPDATE")
    AgentAssayEntryPreview selectForUpdate(@Param("ref") String ref);

    @Update("UPDATE agent_assay_entry_preview SET status='SUPERSEDED',updated_at=NOW(6) "
            + "WHERE agent_session_id=#{session} AND owner_user_id=#{owner} AND status IN ('READY','CONFIRMED')")
    int supersedeActive(@Param("session") String session, @Param("owner") Integer owner);

    @Update("UPDATE agent_assay_entry_preview SET status=#{status},updated_at=NOW(6) WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}

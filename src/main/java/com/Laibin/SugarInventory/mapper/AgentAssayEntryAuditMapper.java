package com.Laibin.SugarInventory.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentAssayEntryAuditMapper {
    @Insert("INSERT INTO agent_assay_entry_audit(owner_user_id,agent_session_id,preview_ref,event_code,result_code,assay_id,occurred_at) "
            + "VALUES(#{owner},#{session},#{ref},#{event},#{code},#{assayId},NOW(6))")
    int append(@Param("owner") Integer owner, @Param("session") String session, @Param("ref") String ref,
               @Param("event") String event, @Param("code") Integer code, @Param("assayId") Integer assayId);
}

package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM agent_session WHERE id = #{id} FOR UPDATE")
    AgentSession selectForAssayEntry(@org.apache.ibatis.annotations.Param("id") String id);
}

package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchOptionVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;

import java.util.List;

public interface AutoInboundParseService {

    /**
     * 从报数文本解析出一批待确认入库任务，并缓存到 Redis。
     */
    AutoInboundParseResponse parse(AutoInboundParseRequest request, User ser);

    /**
     * 根据批次ID获取缓存中的任务列表。
     */
    AutoInboundParseResponse getBatch(String batchId, User user);

    /**
     * 只允许批次所有者更新 Redis 中的受控状态。
     */
    void saveBatch(String batchId, List<AutoInboundTask> tasks, User user);

    List<AutoInboundBatchOptionVO> listBatches(User user);
}


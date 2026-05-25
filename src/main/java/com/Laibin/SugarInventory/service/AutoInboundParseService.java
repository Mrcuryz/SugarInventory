package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.po.User;
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
    AutoInboundParseResponse getBatch(String batchId);

    List<AutoInboundBatchOptionVO> listBatches(User user);
}


package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.bo.AssayResolveResult;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletTask;

import java.time.LocalDate;

public interface AssayResolveService {
    AssayResolveResult resolveForPallet(PalletCode palletCode,
                                        PalletTask task,
                                        Inventory inventory,
                                        LocalDate businessDate,
                                        Integer operatorId,
                                        boolean writeBack);
}

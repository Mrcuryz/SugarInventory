package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeUpdateDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-20
 */
public interface EmployeeService extends IService<EmployeeRoster> {

    @Transactional
    void importEmployeeRoster(MultipartFile file);

    PageResult<EmployeeRoster> queryEmployee(EmployeeQueryDTO queryDTO);

    @Transactional
    EmployeeRoster updateEmployee(EmployeeUpdateDTO dto);

    @Transactional
    int clearResignedEmployees();
}

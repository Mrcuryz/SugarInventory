package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeUpdateDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.Role;
import com.Laibin.SugarInventory.mapper.EmployeeRosterMapper;
import com.Laibin.SugarInventory.mapper.RoleMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.service.EmployeeService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-20
 */
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeRosterMapper, EmployeeRoster> implements EmployeeService, LoggableService<EmployeeRoster> {
    @Autowired
    private EmployeeRosterMapper rosterMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    private static final String ACTIVE_STATUS = "在职";
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    @Override
    public boolean save(EmployeeRoster entity) {
        validateRoleCode(entity.getRoleCode());
        return super.save(entity);
    }

    @Override
    @Transactional
    public void importEmployeeRoster(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);  // 默认取第一个sheet
            List<EmployeeRoster> list = new ArrayList<>();

            // 从第二行开始（第一行为标题）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue; // 跳过空行

                EmployeeRoster emp = new EmployeeRoster();
                emp.setEmployeeId(getCellStringValue(row.getCell(0)));
                emp.setName(getCellStringValue(row.getCell(1)));
                emp.setMobile(getCellStringValue(row.getCell(2)));
                emp.setDepartment(getCellStringValue(row.getCell(3)));
                emp.setPosition(getCellStringValue(row.getCell(4)));
                emp.setStatus(getCellStringValue(row.getCell(5)));
                emp.setRoleCode(getCellStringValue(row.getCell(6)));
                validateRoleCode(emp.getRoleCode());

                EmployeeRoster existing = rosterMapper.selectByEmployeeId(emp.getEmployeeId());
                if (existing != null) {
                    emp.setId(existing.getId());
                    rosterMapper.updateById(emp);
                } else {
                    rosterMapper.insert(emp);
                }
                list.add(emp);
            }
        } catch (Exception e) {
            throw new RuntimeException("导入员工名册失败：" + e.getMessage(), e);
        }
    }

    @Override
    public PageResult<EmployeeRoster> queryEmployee(EmployeeQueryDTO queryDTO) {
        // 计算分页偏移量
        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();
        // 查询数据列表
        List<EmployeeRoster> list = rosterMapper.selectEmployeeList(queryDTO, offset, queryDTO.getSize());
        // 查询总记录数
        Long total = rosterMapper.countEmployee(queryDTO);
        return new PageResult<>(total, list);
    }

    @Transactional
    @Override
    public EmployeeRoster updateEmployee(EmployeeUpdateDTO dto) {
        EmployeeRoster current = rosterMapper.selectById(dto.getId());
        if (current == null) {
            throw new BusinessException("员工不存在");
        }
        validateRoleCode(dto.getRoleCode() != null ? dto.getRoleCode() : current.getRoleCode());
        ensureLastAdminNotDisabled(current, dto);

        int rows = rosterMapper.updateEmployee(dto);
        if (rows < 1) {
            throw new RuntimeException("更新失败，员工ID不存在或数据未变更");
        }
        if (dto.getRoleCode() != null) {
            try {
                userMapper.updateRoleByEmployeeId(current.getEmployeeId(), dto.getRoleCode());
            } catch (Exception e) {
                throw new RuntimeException("更新员工角色失败：" + e.getMessage(), e);
            }
        }
        if (dto.getEmployeeId() != null && !Objects.equals(dto.getEmployeeId(), current.getEmployeeId()) && dto.getRoleCode() != null) {
            try {
                userMapper.updateRoleByEmployeeId(dto.getEmployeeId(), dto.getRoleCode());
            } catch (Exception ignored) {
                // user 表中不存在该工号时，保留员工名册更新结果
            }
        }
        return rosterMapper.selectById(dto.getId());
    }

    @Transactional
    @Override
    public int clearResignedEmployees() {
        int rows = rosterMapper.deleteResignedEmployees();
        if (rows < 1) {
            throw new RuntimeException("没有离职员工可清理");
        }
        return rows;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    @Override
    public EmployeeRoster findById(Integer id) {
        return rosterMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "员工名册";
    }

    private void validateRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException("角色不能为空");
        }
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode)
                .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(role.getStatus())) {
            throw new BusinessException("角色已停用，不能分配给员工");
        }
    }

    private void ensureLastAdminNotDisabled(EmployeeRoster current, EmployeeUpdateDTO dto) {
        boolean isCurrentAdmin = ADMIN_ROLE_CODE.equalsIgnoreCase(current.getRoleCode());
        if (!isCurrentAdmin || !ACTIVE_STATUS.equals(current.getStatus())) {
            return;
        }

        String nextRoleCode = dto.getRoleCode() != null ? dto.getRoleCode() : current.getRoleCode();
        String nextStatus = dto.getStatus() != null ? dto.getStatus() : current.getStatus();
        boolean demoted = !ADMIN_ROLE_CODE.equalsIgnoreCase(nextRoleCode);
        boolean disabled = !ACTIVE_STATUS.equals(nextStatus);
        if (!demoted && !disabled) {
            return;
        }

        long activeAdminCount = rosterMapper.selectCount(new LambdaQueryWrapper<EmployeeRoster>()
                .eq(EmployeeRoster::getRoleCode, ADMIN_ROLE_CODE)
                .eq(EmployeeRoster::getStatus, ACTIVE_STATUS));
        if (activeAdminCount <= 1) {
            throw new BusinessException("最后一个管理员不可被停用或降权");
        }
    }
}

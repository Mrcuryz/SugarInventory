package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeRosterAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RolePermissionSummaryAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleQueryDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.Permission;
import com.Laibin.SugarInventory.domain.vo.AdministrationAgentVO;
import com.Laibin.SugarInventory.domain.vo.RoleVO;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.service.AdministrationAgentReadService;
import com.Laibin.SugarInventory.service.EmployeeService;
import com.Laibin.SugarInventory.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministrationAgentReadServiceImpl implements AdministrationAgentReadService {
    private final EmployeeService employeeService;
    private final RoleService roleService;
    private final PermissionMapper permissionMapper;

    @Override
    public AdministrationAgentVO.EmployeeRosterResult queryEmployeeRoster(EmployeeRosterAgentQueryDTO query) {
        EmployeeRosterAgentQueryDTO source = query == null ? new EmployeeRosterAgentQueryDTO() : query;
        int page = page(source.getPage());
        int size = size(source.getSize());
        EmployeeQueryDTO criteria = new EmployeeQueryDTO();
        criteria.setEmployeeId(text(source.getEmployeeId(), 50, "employeeId"));
        criteria.setName(text(source.getName(), 100, "name"));
        criteria.setDepartment(text(source.getDepartment(), 100, "department"));
        criteria.setPosition(text(source.getPosition(), 100, "position"));
        criteria.setStatus(text(source.getStatus(), 20, "status"));
        criteria.setRoleCode(text(source.getRoleCode(), 50, "roleCode"));
        criteria.setPage(page);
        criteria.setSize(size);
        PageResult<EmployeeRoster> result = employeeService.queryEmployee(criteria);
        List<AdministrationAgentVO.EmployeeRow> rows = safe(result == null ? null : result.getRecords()).stream()
                .map(this::employeeRow).toList();
        return AdministrationAgentVO.EmployeeRosterResult.builder()
                .dataScope("CURRENT_EMPLOYEE_ROSTER").total(result == null ? 0 : result.getTotal())
                .page(page).size(size).records(rows)
                .limitations(List.of("手机号仅以掩码形式展示，不返回内部员工记录 ID、登录凭据、微信绑定信息或鉴权令牌。",
                        "名册反映当前配置状态，不证明员工当前在岗、已登录或实际执行过某项业务。"))
                .build();
    }

    @Override
    public AdministrationAgentVO.RoleCatalogResult queryRoles(RoleCatalogAgentQueryDTO query) {
        RoleCatalogAgentQueryDTO source = query == null ? new RoleCatalogAgentQueryDTO() : query;
        int page = page(source.getPage()); int size = size(source.getSize());
        RoleQueryDTO criteria = new RoleQueryDTO();
        criteria.setKeyword(text(source.getKeyword(), 100, "keyword"));
        criteria.setStatus(text(source.getStatus(), 20, "status"));
        criteria.setPage(page); criteria.setSize(size);
        PageResult<RoleVO> result = roleService.queryRoles(criteria);
        List<AdministrationAgentVO.RoleRow> rows = safe(result == null ? null : result.getRecords()).stream()
                .map(this::roleRow).toList();
        return AdministrationAgentVO.RoleCatalogResult.builder().dataScope("CURRENT_RBAC_ROLE_CATALOG")
                .total(result == null ? 0 : result.getTotal()).page(page).size(size).records(rows)
                .limitations(List.of("仅展示当前角色目录及汇总数量，不返回内部角色 ID、权限 ID或员工姓名清单。",
                        "角色存在或启用不等于某位员工当前具备该角色，也不绕过实际请求时的 RBAC 校验。"))
                .build();
    }

    @Override
    public AdministrationAgentVO.RolePermissionSummaryResult getRolePermissionSummary(RolePermissionSummaryAgentQueryDTO query) {
        String key = query == null ? null : text(query.getRoleCodeOrName(), 100, "roleCodeOrName");
        if (key == null) throw new BusinessException(400, "roleCodeOrName 不能为空");
        RoleQueryDTO criteria = new RoleQueryDTO(); criteria.setKeyword(key); criteria.setPage(1); criteria.setSize(50);
        List<RoleVO> matches = safe(roleService.queryRoles(criteria).getRecords()).stream()
                .filter(item -> key.equalsIgnoreCase(item.getRoleCode()) || key.equals(item.getRoleName())).toList();
        if (matches.isEmpty()) throw new BusinessException(404, "未找到编码或名称完全匹配的角色");
        if (matches.size() > 1) throw new BusinessException(409, "角色名称或编码存在多个完全匹配项，无法安全选择");
        RoleVO role = matches.getFirst();
        List<AdministrationAgentVO.PermissionRow> permissions = safe(permissionMapper.selectPermissionsByRoleCode(role.getRoleCode()))
                .stream().map(this::permissionRow).toList();
        return AdministrationAgentVO.RolePermissionSummaryResult.builder().dataScope("CURRENT_RBAC_ROLE_PERMISSION_SUMMARY")
                .roleName(role.getRoleName()).roleCode(role.getRoleCode()).status(role.getStatus())
                .activeEmployeeCount(role.getUserCount()).permissionCount(permissions.size()).permissions(permissions)
                .limitations(List.of("权限摘要只描述当前角色配置；实际访问仍由登录身份、角色状态和 Java Gateway 鉴权共同决定。",
                        "结果不包含内部权限 ID、Agent 工具白名单、模型上下文、密钥或员工身份详情。"))
                .build();
    }

    private AdministrationAgentVO.EmployeeRow employeeRow(EmployeeRoster item) {
        return AdministrationAgentVO.EmployeeRow.builder().employeeId(item.getEmployeeId()).name(item.getName())
                .maskedMobile(maskMobile(item.getMobile())).department(item.getDepartment()).position(item.getPosition())
                .status(item.getStatus()).roleCode(item.getRoleCode()).build();
    }
    private AdministrationAgentVO.RoleRow roleRow(RoleVO item) {
        return AdministrationAgentVO.RoleRow.builder().roleName(item.getRoleName()).roleCode(item.getRoleCode())
                .description(item.getDescription()).status(item.getStatus()).permissionCount(item.getPermissionCount())
                .activeEmployeeCount(item.getUserCount()).build();
    }
    private AdministrationAgentVO.PermissionRow permissionRow(Permission item) {
        return AdministrationAgentVO.PermissionRow.builder().permissionCode(item.getPermCode()).permissionName(item.getPermName())
                .permissionGroup(permissionGroup(item.getPermCode())).description(item.getDescription()).build();
    }
    private String permissionGroup(String code) {
        if (code == null || code.isBlank()) return "OTHER";
        int separator = code.indexOf(':');
        return (separator < 0 ? code : code.substring(0, separator)).toUpperCase();
    }
    private String maskMobile(String value) {
        if (value == null || value.isBlank()) return null;
        String mobile = value.trim();
        if (mobile.length() < 7) return "****";
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
    private int page(Integer value) { if (value == null) return 1; if (value < 1) throw new BusinessException(400, "page 必须大于 0"); return value; }
    private int size(Integer value) { int result = value == null ? 20 : value; if (result < 1 || result > 50) throw new BusinessException(400, "size 必须在 1 到 50 之间"); return result; }
    private String text(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new BusinessException(400, field + " 长度超限"); return result; }
    private <T> List<T> safe(List<T> rows) { return rows == null ? List.of() : rows; }
}

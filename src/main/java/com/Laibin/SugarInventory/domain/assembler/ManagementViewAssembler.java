package com.Laibin.SugarInventory.domain.assembler;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.EmployeeCreateDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.EmployeeRosterVO;
import com.Laibin.SugarInventory.domain.vo.OperationLogVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.domain.vo.ScreenMeshVO;
import com.Laibin.SugarInventory.domain.vo.WarehouseVO;
import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class ManagementViewAssembler {
    private ManagementViewAssembler() {
    }

    public static EmployeeRoster toEmployeeRoster(EmployeeCreateDTO dto) {
        EmployeeRoster entity = new EmployeeRoster();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    public static EmployeeRosterVO toEmployeeRosterVO(EmployeeRoster entity) {
        return copy(entity, EmployeeRosterVO::new);
    }

    public static PageResult<EmployeeRosterVO> toEmployeeRosterPage(PageResult<EmployeeRoster> page) {
        return mapPage(page, ManagementViewAssembler::toEmployeeRosterVO);
    }

    public static OperationLogVO toOperationLogVO(OperationLog entity) {
        return copy(entity, OperationLogVO::new);
    }

    public static PageResult<OperationLogVO> toOperationLogPage(PageResult<OperationLog> page) {
        return mapPage(page, ManagementViewAssembler::toOperationLogVO);
    }

    public static ProductVO toProductVO(Product entity) {
        ProductVO vo = copy(entity, ProductVO::new);
        if (vo != null) {
            vo.setType(vo.getProductType());
        }
        return vo;
    }

    public static List<ProductVO> toProductVOs(List<Product> entities) {
        return mapList(entities, ManagementViewAssembler::toProductVO);
    }

    public static PageResult<ProductVO> toProductPage(PageResult<Product> page) {
        return mapPage(page, ManagementViewAssembler::toProductVO);
    }

    public static ScreenMeshVO toScreenMeshVO(ScreenMesh entity) {
        return copy(entity, ScreenMeshVO::new);
    }

    public static List<ScreenMeshVO> toScreenMeshVOs(List<ScreenMesh> entities) {
        return mapList(entities, ManagementViewAssembler::toScreenMeshVO);
    }

    public static PageResult<ScreenMeshVO> toScreenMeshPage(PageResult<ScreenMesh> page) {
        return mapPage(page, ManagementViewAssembler::toScreenMeshVO);
    }

    public static WarehouseVO toWarehouseVO(Warehouse entity) {
        WarehouseVO vo = copy(entity, WarehouseVO::new);
        if (vo != null && entity.getId() != null) {
            vo.setWarehouseId(String.valueOf(entity.getId()));
        }
        return vo;
    }

    public static List<WarehouseVO> toWarehouseVOs(List<Warehouse> entities) {
        return mapList(entities, ManagementViewAssembler::toWarehouseVO);
    }

    private static <S, T> T copy(S source, java.util.function.Supplier<T> targetFactory) {
        if (source == null) {
            return null;
        }
        T target = targetFactory.get();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private static <S, T> List<T> mapList(List<S> records, Function<S, T> mapper) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream().map(mapper).toList();
    }

    private static <S, T> PageResult<T> mapPage(PageResult<S> page, Function<S, T> mapper) {
        if (page == null) {
            return new PageResult<>(0L, Collections.emptyList());
        }
        return new PageResult<>(page.getTotal(), mapList(page.getRecords(), mapper));
    }
}

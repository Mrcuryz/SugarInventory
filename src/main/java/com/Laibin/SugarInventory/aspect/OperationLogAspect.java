package com.Laibin.SugarInventory.aspect;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.BaseDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.BaseVO;
import com.Laibin.SugarInventory.mapper.OperationLogMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ScreenMeshMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

@Aspect
@Component
@Slf4j
public class OperationLogAspect {
    private static final Set<String> INTERNAL_AUDIT_FIELDS = Set.of(
            "appliedStandardId",
            "appliedStandardName",
            "appliedStandardVersion",
            "judgeResult",
            "failedMetricCount",
            "failedMetricsJson",
            "standardSnapshotJson",
            "judgeMessage"
    );
    private static final Set<String> HIDDEN_ID_FIELDS = Set.of(
            "id",
            "productId",
            "product_id",
            "screenMeshId",
            "warehouseId",
            "assayId",
            "relatedId"
    );
    private static final Set<String> SENSITIVE_AUDIT_FIELDS = Set.of(
            "password",
            "authorization",
            "accessToken",
            "refreshToken",
            "token",
            "secret",
            "openid",
            "sessionKey"
    );

    @Autowired
    private ApplicationContext applicationContext;

    private Map<String, LoggableService<?>> tableServiceMap;
    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ScreenMeshMapper screenMeshMapper;

    @Autowired
    private WarehouseMapper warehouseMapper;

    @Autowired
    private UserMapper userMapper;

    private ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 使 ObjectMapper 支持 Java 8 时间类型
        objectMapper = new ObjectMapper() {
            @Override
            public ObjectMapper configure(MapperFeature feature, boolean state) {
                // 强制禁用所有排序功能
                return super.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
                        .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            }
        };

        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 关键配置：禁用所有排序
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false);

        // 获取所有LoggableService的实例
        Map<String, LoggableService> beans = applicationContext.getBeansOfType(LoggableService.class);
        tableServiceMap = new HashMap<>();
        for (LoggableService<?> service : beans.values()) {
            String tableName = service.getTableName();
            tableServiceMap.put(tableName, service);
        }
    }

    @Pointcut("@annotation(com.Laibin.SugarInventory.annotation.LogOperation)")
    public void operationLogPointcut() {
    }

    @Around("operationLogPointcut()")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean businessMethodInvoked = false;
        boolean businessMethodCompleted = false;
        Object result = null;
        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogOperation opLogAnnotation = method.getAnnotation(LogOperation.class);
            String tableName = opLogAnnotation.value();
            OperationType operationType = opLogAnnotation.type();

            String operator = "system";
            // 获取当前操作人
            Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                    ? null
                    : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) principal;
                operator = loginUser.getUser().getName();
                // 使用 operator 进行日志记录
            }
            // 对于 UPDATE 和 DELETE 操作，在执行前获取旧数据
            Object[] args = joinPoint.getArgs();

            Object newData = null;
            Object auditInput = null;
            List<Integer> ids = new ArrayList<>();

            for (Object arg : args) {
                if (arg != null && (arg instanceof BaseEntity
                        || arg instanceof BaseDTO
                        || arg instanceof List<?>
                        || arg.getClass().getPackageName().contains(".domain.dto"))) {
                    auditInput = arg;
                }
                if (operationType == OperationType.UPDATE || operationType == OperationType.DELETE) {
                    if (arg instanceof Integer integerId && integerId > 0 && !ids.contains(integerId)) {
                        ids.add(integerId);
                    } else if (arg instanceof BaseDTO) {
                        Integer dtoId = ((BaseDTO) arg).getId();
                        if (dtoId != null && dtoId > 0 && !ids.contains(dtoId)) {
                            ids.add(dtoId);
                        }
                    } else if (arg instanceof BaseEntity) {
                        Integer entityId = ((BaseEntity) arg).getId();
                        if (entityId != null && entityId > 0 && !ids.contains(entityId)) {
                            ids.add(entityId);
                        }
                    } else if (arg instanceof List<?>) {
                        // **批量情况**
                        for (Object item : (List<?>) arg) {
                            if (item instanceof BaseDTO) {
                                Integer itemId = ((BaseDTO) item).getId();
                                if (itemId != null && itemId > 0 && !ids.contains(itemId)) {
                                    ids.add(itemId);
                                }
                            }
                        }
                    }
                }
                if (operationType == OperationType.INSERT) {
                    if (arg instanceof BaseEntity || arg instanceof BaseDTO) {
                        newData = arg;
                    } else if (arg instanceof List<?>) {
                        newData = arg; // 直接记录整个批量数据
                    }
                }
            }

            List<Object> oldDataList = new ArrayList<>();
            if ((operationType == OperationType.UPDATE || operationType == OperationType.DELETE) && !ids.isEmpty()) {
                LoggableService<?> service = tableServiceMap == null ? null : tableServiceMap.get(tableName);
                if (service != null) {
                    for (Integer id : ids) {
                        oldDataList.add(service.findById(id));
                    }
                }
            }

            businessMethodInvoked = true;
            result = joinPoint.proceed();
            businessMethodCompleted = true;

            if (result instanceof Result) {
                if (((Result<?>) result).getCode() != 200)
                    return result;
            }

            List<Object> newDataList = new ArrayList<>();
            if (operationType == OperationType.UPDATE) {
                if (result instanceof List<?>) {
                    newDataList.addAll((List<?>) result);
                } else if (result instanceof BaseVO || result instanceof BaseEntity) {
                    newDataList.add(result);
                } else if (result instanceof Result<?> resultWrapper && resultWrapper.getData() != null) {
                    Object resultData = resultWrapper.getData();
                    if (resultData instanceof List<?> listData) {
                        newDataList.addAll(listData);
                    } else {
                        newDataList.add(resultData);
                    }
                }
            }

            List<OperationLog> logEntries = new ArrayList<>();
            if (operationType == OperationType.INSERT) {
                if (newData instanceof List<?>) {
                    for (Object item : (List<?>) newData) {
                        logEntries.add(createLog(tableName, operationType, null, item, operator));
                    }
                } else {
                    logEntries.add(createLog(tableName, operationType, null, newData, operator));
                }
            } else if (operationType == OperationType.UPDATE || operationType == OperationType.DELETE) {
                // **批量更新 / 删除**
                for (int i = 0; i < ids.size(); i++) {
                    Object oldItem = i < oldDataList.size() ? oldDataList.get(i) : null;
                    Object newItem = (operationType == OperationType.UPDATE && i < newDataList.size()) ? newDataList.get(i) : null;
                    logEntries.add(createLog(tableName, operationType, oldItem, newItem, operator));
                }
                if (ids.isEmpty()) {
                    Object oldItem = operationType == OperationType.DELETE ? auditInput : null;
                    Object newItem = operationType == OperationType.UPDATE ? auditInput : null;
                    logEntries.add(createLog(tableName, operationType, oldItem, newItem, operator));
                }
            }

            for (OperationLog log : logEntries) {
                operationLogMapper.insert(log);
            }
            return result;
        } catch (Exception e) {
            if (businessMethodInvoked && !businessMethodCompleted) {
                throw e;
            }
            if (businessMethodCompleted) {
                log.error("Operation audit failed after business method completed: {}", joinPoint.getSignature(), e);
                return result;
            }
            log.error("Operation audit preparation failed; continuing business method once: {}", joinPoint.getSignature(), e);
            return joinPoint.proceed();
        }
    }

    private OperationLog createLog(String tableName, OperationType operationType, Object oldData, Object newData, String operator) {
        OperationLog log = new OperationLog();
        String changedFieldsJson = "{}";
        if (operationType == OperationType.UPDATE) {
            Map<String, Object> changedFields = oldData == null
                    ? normalizeDisplayFields(getOrderedFieldMap(newData, true))
                    : normalizeDisplayFields(getChangedFields(oldData, newData));
            changedFieldsJson = convertToJson(changedFields);
        } else if (operationType == OperationType.INSERT) {
            Map<String, Object> newDataMap = normalizeDisplayFields(getOrderedFieldMap(newData, true));
            changedFieldsJson = convertToJson(newDataMap);
        }

        log.setTableName(tableName);
        log.setOperationType(operationType.name());
        log.setOperationTime(LocalDateTime.now());
        log.setChangedFields(changedFieldsJson);
        Map<String, Object> oldDataMap = normalizeDisplayFields(getOrderedFieldMap(oldData, true));
        log.setOldData(convertToJson(oldDataMap));
        log.setOperator(operator);
        return log;
    }

    private Map<String, Object> getChangedFields(Object oldData, Object newData) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (oldData == null || newData == null) return changes;

        try {
            Map<String, Field> oldFields = getDeclaredFieldsMap(oldData.getClass());
            Map<String, Field> newFields = getDeclaredFieldsMap(newData.getClass());

            // 遍历旧数据字段（按声明顺序）
            for (Field oldField : oldFields.values()) {
                String fieldName = oldField.getName();
                if (isIgnoredField(fieldName)) continue;

                Field newField = newFields.get(fieldName);
                if (newField == null) continue;

                oldField.setAccessible(true);
                newField.setAccessible(true);

                Object oldVal = oldField.get(oldData);
                Object newVal = newField.get(newData);

                if (!isEqual(oldVal, newVal)) {
                    changes.put(fieldName, newVal);
                }
            }
        } catch (IllegalAccessException e) {
            log.warn("Failed to compare fields while building operation audit", e);
        }
        return changes;
    }

    private Map<String, Field> getDeclaredFieldsMap(Class<?> clazz) {
        Map<String, Field> fieldMap = new LinkedHashMap<>(); // 保持顺序
        for (Field field : clazz.getDeclaredFields()) {
            fieldMap.put(field.getName(), field);
        }
        return fieldMap;
    }

    private boolean isEqual(Object oldVal, Object newVal) {
        if (oldVal instanceof LocalDateTime && newVal instanceof LocalDateTime) {
            return ((LocalDateTime) oldVal).isEqual((LocalDateTime) newVal);
        }

        // 对于枚举类型，比较名称
        if (oldVal instanceof Enum && newVal instanceof Enum) {
            return ((Enum<?>) oldVal).name().equals(((Enum<?>) newVal).name());
        }

        return Objects.equals(oldVal, newVal);
    }

    Map<String, Object> normalizeDisplayFields(Map<String, Object> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        String resolvedProductName = resolveProductName(getIntegerValue(dataMap, "productId", "product_id"));
        String resolvedScreenMeshName = resolveScreenMeshName(getIntegerValue(dataMap, "screenMeshId"));
        String resolvedWarehouseName = resolveWarehouseName(getIntegerValue(dataMap, "warehouseId"));
        String resolvedTesterName = resolveUserName(getIntegerValue(dataMap, "testedBy"));

        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (shouldHideFromDisplay(fieldName)) {
                if (("productId".equals(fieldName) || "product_id".equals(fieldName))
                        && isBlankValue(normalized.get("productName")) && resolvedProductName != null) {
                    normalized.put("productName", resolvedProductName);
                } else if ("screenMeshId".equals(fieldName)
                        && isBlankValue(normalized.get("screenMeshName")) && resolvedScreenMeshName != null) {
                    normalized.put("screenMeshName", resolvedScreenMeshName);
                } else if ("warehouseId".equals(fieldName)
                        && isBlankValue(normalized.get("warehouseName")) && resolvedWarehouseName != null) {
                    normalized.put("warehouseName", resolvedWarehouseName);
                } else if ("testedBy".equals(fieldName)
                        && isBlankValue(normalized.get("testerName")) && resolvedTesterName != null) {
                    normalized.put("testerName", resolvedTesterName);
                }
                continue;
            }

            normalized.put(fieldName, value);
        }
        return normalized;
    }

    private Map<String, Object> getOrderedFieldMap(Object obj, boolean filterIgnored) {
        if (obj == null) return Collections.emptyMap();

        Map<String, Object> map = new LinkedHashMap<>();
        try {
            Class<?> clazz = obj.getClass();
            List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));

            for (Field field : fields) {
                String fieldName = field.getName();
                if (filterIgnored && isIgnoredField(fieldName)) continue;

                field.setAccessible(true);
                Object value = field.get(obj);
                map.put(fieldName, value);
            }
        } catch (IllegalAccessException e) {
            log.warn("Failed to read fields while building operation audit", e);
        }
        return map;
    }

    private boolean isIgnoredField(String field) {
        return field.equals("id") || HIDDEN_ID_FIELDS.contains(field) || SENSITIVE_AUDIT_FIELDS.contains(field)
                || field.equals("createdAt") || field.equals("updatedAt") || field.equals("testedBy") ||
                field.equals("createdBy") || field.equals("updatedBy") || field.equals("selectType") ||
                field.equals("relatedId") || INTERNAL_AUDIT_FIELDS.contains(field) || field.isEmpty();
    }

    private boolean shouldHideFromDisplay(String field) {
        return field == null || field.isEmpty()
                || HIDDEN_ID_FIELDS.contains(field)
                || SENSITIVE_AUDIT_FIELDS.contains(field)
                || INTERNAL_AUDIT_FIELDS.contains(field)
                || field.equals("testedBy")
                || field.equals("createdBy")
                || field.equals("updatedBy")
                || field.equals("createdAt")
                || field.equals("updatedAt")
                || field.equals("selectType");
    }

    private Integer getIntegerValue(Map<String, Object> dataMap, String... keys) {
        for (String key : keys) {
            Object value = dataMap.get(key);
            if (value instanceof Integer integerValue) {
                return integerValue;
            }
            if (value instanceof Number numberValue) {
                return numberValue.intValue();
            }
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                try {
                    return Integer.parseInt(stringValue.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String resolveProductName(Integer productId) {
        if (productId == null) {
            return null;
        }
        Product product = productMapper.selectById(productId);
        return product == null ? null : product.getProductName();
    }

    private String resolveScreenMeshName(Integer screenMeshId) {
        if (screenMeshId == null) {
            return null;
        }
        ScreenMesh screenMesh = screenMeshMapper.selectById(screenMeshId);
        return screenMesh == null ? null : screenMesh.getMeshName();
    }

    private String resolveWarehouseName(Integer warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        return warehouse == null ? null : warehouse.getWarehouseName();
    }

    private String resolveUserName(Integer userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getName();
    }

    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    private String convertToJson(Object obj) {
        try {
            if (obj == null) return "{}";
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize operation audit payload", e);
            return "{}"; // 发生异常时，避免返回 null
        }
    }
}

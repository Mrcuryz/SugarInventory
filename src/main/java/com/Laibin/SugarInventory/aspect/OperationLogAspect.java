package com.Laibin.SugarInventory.aspect;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.BaseDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.domain.vo.BaseVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.mapper.OperationLogMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private ApplicationContext applicationContext;

    private Map<String, LoggableService<?>> tableServiceMap;
    @Autowired
    private OperationLogMapper operationLogMapper;

    private ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 使 ObjectMapper 支持 Java 8 时间类型
        objectMapper.registerModule(new JavaTimeModule());
        // 获取所有LoggableService的实例
        Map<String, LoggableService> beans = applicationContext.getBeansOfType(LoggableService.class);
        tableServiceMap = new HashMap<>();
        for (LoggableService<?> service : beans.values()) {
            String tableName = service.getTableName();
            tableServiceMap.put(tableName, service);
        }
    }

    @Pointcut("@annotation(com.Laibin.SugarInventory.annotation.LogOperation)")
    public void operationLogPointcut() {}

    @Around("operationLogPointcut()")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogOperation opLogAnnotation = method.getAnnotation(LogOperation.class);
            String tableName = opLogAnnotation.value();
            OperationType operationType = opLogAnnotation.type();

            OperationLog logEntity = new OperationLog();

            String operator = "system";
            // 获取当前操作人
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) principal;
                operator = loginUser.getUser().getName();
                // 使用 operator 进行日志记录
            }
            // 对于 UPDATE 和 DELETE 操作，在执行前获取旧数据
            Object[] args = joinPoint.getArgs();

            Object oldData = null;
            Object newData = null;
            List<Integer> ids = new ArrayList<>();

            // **1. 处理参数**
            for (Object arg : args) {
                if (operationType == OperationType.UPDATE || operationType == OperationType.DELETE) {
                    if (arg instanceof Integer) {
                        ids.add((Integer) arg);
                    } else if (arg instanceof BaseDTO) {
                        ids.add(((BaseDTO) arg).getId());
                    } else if (arg instanceof BaseEntity) {
                        ids.add(((BaseEntity) arg).getId());
                    } else if (arg instanceof List<?>) {
                        // **批量情况**
                        for (Object item : (List<?>) arg) {
                            if (item instanceof BaseDTO) {
                                ids.add(((BaseDTO) item).getId());
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

            // **2. 预查询旧数据（批量 `UPDATE` / `DELETE`）**
            List<Object> oldDataList = new ArrayList<>();
            if ((operationType == OperationType.UPDATE || operationType == OperationType.DELETE) && !ids.isEmpty()) {
                LoggableService<?> service = tableServiceMap.get(tableName);
                if (service != null) {
                    for (Integer id : ids) {
                        oldDataList.add(service.findById(id));
                    }
                }
            }

            // **3. 执行目标方法**
            Object result = joinPoint.proceed();

            if(result instanceof Result){
                if(((Result<?>) result).getCode() != 200)
                    return result;
            }

            // **4. 获取新数据**
            List<Object> newDataList = new ArrayList<>();
            if (operationType == OperationType.UPDATE) {
                if (result instanceof List<?>) {
                    newDataList.addAll((List<?>) result);
                } else if (result instanceof BaseVO || result instanceof BaseEntity) {
                    newDataList.add(result);
                } else if (result instanceof Result && ((Result<?>) result).getData() instanceof BaseVO
                        || ((Result<?>) result).getData() instanceof BaseEntity) {
                    newDataList.add(((Result<?>) result).getData());
                }
            }

            // **5. 记录日志**
            List<OperationLog> logEntries = new ArrayList<>();
            if (operationType == OperationType.INSERT) {
                // **批量插入**
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
                    Object oldItem = oldDataList.get(i);
                    Object newItem = (operationType == OperationType.UPDATE && i < newDataList.size()) ? newDataList.get(i) : null;
                    logEntries.add(createLog(tableName, operationType, oldItem, newItem, operator));
                }
            }

            // **6. 批量插入日志**
            for (OperationLog log : logEntries) {
                operationLogMapper.insert(log);
            }

            return result;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println("参数错误：" + e.getMessage());
            return joinPoint.proceed(); // 跳过日志记录，继续执行原方法
        }
    }

    private OperationLog createLog(String tableName, OperationType operationType, Object oldData, Object newData, String operator) {
        OperationLog log = new OperationLog();
        String changedFieldsJson = "{}";
        if (operationType == OperationType.UPDATE) {
            Map<String, Object> changedFields = getChangedFields(oldData, newData);
            changedFieldsJson = convertToJson(changedFields);
        } else if (operationType == OperationType.INSERT) {
            changedFieldsJson = convertToJson(newData);
        }

        log.setTableName(tableName);
        log.setOperationType(operationType.name());
        log.setOperationTime(LocalDateTime.now());
        log.setChangedFields(changedFieldsJson);
        log.setOldData(convertToJson(oldData));
        log.setOperator(operator);
        return log;
    }

    private Map<String, Object> getChangedFields(Object oldData, Object newData) {
        Map<String, Object> changes = new HashMap<>();
        if (oldData == null || newData == null) {
            return changes;
        }
        BeanWrapper oldWrapper = new BeanWrapperImpl(oldData);
        BeanWrapper newWrapper = new BeanWrapperImpl(newData);
        for (PropertyDescriptor pd : oldWrapper.getPropertyDescriptors()) {
            String field = pd.getName();
            if ("class".equals(field) || isIgnoredField(field)) { // 过滤字段
                continue;
            }
            Object oldVal = oldWrapper.getPropertyValue(field);
            Object newVal = newWrapper.getPropertyValue(field);
            if (!isEqual(oldVal, newVal)) { // 精准比较值
                changes.put(field, newVal);
            }
        }
        return changes;
    }

    // 精准比较值（处理日期类型）
    private boolean isEqual(Object oldVal, Object newVal) {
        if (oldVal instanceof LocalDateTime && newVal instanceof LocalDateTime) {
            return ((LocalDateTime) oldVal).isEqual((LocalDateTime) newVal);
        }
        return Objects.equals(oldVal, newVal);
    }

    // 忽略自动填充字段（如 createdAt/updatedAt）
    private boolean isIgnoredField(String field) {
        return field.equals("createdAt") || field.equals("updatedAt") || field.equals("createdBy") || field.equals("updatedBy");
    }

    // 将对象转换为 JSON 字符串
    private String convertToJson(Object obj) {
        try {
            if (obj == null) return "{}";
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            System.err.println("JSON 序列化失败：" + e.getMessage());
            e.printStackTrace();
            return "{}"; // 发生异常时，避免返回 null
        }
    }
}

package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.production.controller.ProductionBoilingBatchController;
import com.Laibin.SugarInventory.production.controller.ProductionOrderController;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerProductionBoundaryTest {
    private static final List<Class<?>> DTO_VO_BOUNDARY_CONTROLLERS = List.of(
            AssayGroupController.class,
            EmployeeRosterController.class,
            OperationLogController.class,
            ProductController.class,
            QualityStandardController.class,
            ScreenMeshController.class,
            WarehouseController.class,
            ProductionBoilingBatchController.class,
            ProductionOrderController.class
    );

    @Test
    void managementControllersDoNotExposePersistenceObjectsInPublicSignatures() {
        DTO_VO_BOUNDARY_CONTROLLERS.stream()
                .flatMap(controller -> List.of(controller.getDeclaredMethods()).stream())
                .filter(method -> !method.isSynthetic())
                .forEach(method -> {
                    assertNoPersistenceObject(method, method.getGenericReturnType());
                    for (Type parameterType : method.getGenericParameterTypes()) {
                        assertNoPersistenceObject(method, parameterType);
                    }
                });
    }

    @Test
    void controllersDelegateExceptionsAndContainNoConsoleDebugging() throws IOException {
        Path controllerRoot = Path.of("src/main/java/com/Laibin/SugarInventory");
        try (var paths = Files.walk(controllerRoot)) {
            List<String> violations = paths.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .flatMap(path -> sourceViolations(path).stream())
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void criticalBusinessWritesHaveGenericOperationAudit() throws Exception {
        assertAudited(EmployeeRosterController.class, "importEmployeeRoster");
        assertAudited(EmployeeRosterController.class, "addEmployee");
        assertAudited(InStockController.class, "stockIn");
        assertAudited(OutStockController.class, "createOutStock");
        assertAudited(OutStockController.class, "transferOut");
        assertAudited(OutStockController.class, "createStackOutStock");
        assertAudited(SemiProductRecordController.class, "addSemiProductRecord");
        assertAudited(SemiProductRecordController.class, "stackModeInStock");
        assertAudited(RoleController.class, "createRole");
        assertAudited(RoleController.class, "updateRolePermissions");
        assertAudited(PalletCodeController.class, "confirmTasks");
        assertAudited(PalletCodeController.class, "invalidateCodes");
        assertAudited(PalletCodeController.class, "createWarehouseMapTasks");
        assertAudited(ProductionBoilingBatchController.class, "createBatch");
        assertAudited(ProductionOrderController.class, "createOrder");
        assertAudited(ProductionOrderController.class, "pickMaterials");
        assertAudited(ProductionOrderController.class, "finishProduction");

        Method readOnlyMethod = ProductController.class.getDeclaredMethod("getProductWarehouse", Integer.class);
        assertThat(readOnlyMethod.getAnnotation(LogOperation.class)).isNull();
    }

    @Test
    void webLoginPasswordIsExternalizedAndNeverHardCoded() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/Laibin/SugarInventory/service/impl/AuthServiceImpl.java"));
        assertThat(source).contains("${auth.web-login.password:}");
        assertThat(source).doesNotContain("WEB_LOGIN_PASSWORD =");
        assertThat(source).doesNotContain("\"lbsp\"");
    }

    private List<String> sourceViolations(Path path) {
        try {
            String source = Files.readString(path);
            return List.of(
                            source.contains("catch (") ? path + ": local exception swallowing" : "",
                            source.contains("System.out") || source.contains("System.err") || source.contains("printStackTrace")
                                    ? path + ": console debugging" : "")
                    .stream()
                    .filter(value -> !value.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertAudited(Class<?> controller, String methodName) {
        List<Method> matches = List.of(controller.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(methodName))
                .toList();
        assertThat(matches).as(controller.getSimpleName() + "#" + methodName).hasSize(1);
        assertThat(matches.getFirst().getAnnotation(LogOperation.class)).isNotNull();
    }

    private void assertNoPersistenceObject(Method method, Type type) {
        if (type instanceof Class<?> clazz) {
            assertThat(clazz.getPackageName())
                    .as(method.toGenericString())
                    .doesNotStartWith("com.Laibin.SugarInventory.domain.po");
            return;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            assertNoPersistenceObject(method, parameterizedType.getRawType());
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                assertNoPersistenceObject(method, argument);
            }
        }
    }
}

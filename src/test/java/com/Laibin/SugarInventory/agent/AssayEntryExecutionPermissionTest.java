package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.controller.AssayEntryExecutionController;
import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.domain.po.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryExecutionPermissionTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void controllerIsAbsentByDefault() {
        try (var context = context(false)) { assertThat(context.getBeansOfType(AssayEntryExecutionController.class)).isEmpty(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"assay:view", "assay:create", "quality:test", "agent:use", "ROLE_ADMIN"})
    void allControlEndpointsDenyPartialPermissions(String codes) {
        try (var context = context(true)) {
            var user = authenticate(codes); var controller = context.getBean(AssayEntryExecutionController.class);
            assertThatThrownBy(() -> controller.preview(user, "s", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.previewGroup(user, "s", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.previewFromGroup(user, "s", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.previewFromProduct(user, "s", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.confirm(user, "s", "r")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.execute(user, "s", "r", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.result(user, "s", "r")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.cancel(user, "s", "r")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.recover(user, "s", "r")).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(AssayEntryArchiveService.class), context.getBean(AssayEntryExecutionService.class), context.getBean(AssayEntryExecutionFacade.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"agent:use,assay:view,assay:create", "agent:use,assay:view,quality:test"})
    void permitsBothSupportedCreationAuthorities(String codes) {
        try (var context = context(true)) {
            var user = authenticate(codes);
            assertThat(context.getBean(AssayEntryExecutionController.class).confirm(user, "s", "r").getCode()).isEqualTo(200);
            verify(context.getBean(AssayEntryExecutionFacade.class)).confirm(7, "s", "r");
            assertThat(context.getBean(AssayEntryExecutionController.class).previewGroup(user, "s", AssayGroupEntryPreviewTest.request()).getCode()).isEqualTo(200);
            verify(context.getBean(AssayEntryArchiveService.class)).createGroup(eq(7), eq("s"), any());
        }
    }

    @Test void businessPermissionsDoNotGrantAgentAccess() {
        try (var context = context(true)) {
            var user = authenticate("assay:view,assay:create");
            assertThatThrownBy(() -> context.getBean(AssayEntryExecutionController.class).confirm(user, "s", "r"))
                    .isInstanceOf(com.Laibin.SugarInventory.common.BusinessException.class).extracting("code").isEqualTo(403);
            verifyNoInteractions(context.getBean(AssayEntryExecutionFacade.class));
        }
    }

    private AnnotationConfigApplicationContext context(boolean enabled) {
        var context = new AnnotationConfigApplicationContext();
        if (enabled) context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("agent.assay-entry.execute-enabled", "true")));
        context.register(Config.class, AssayEntryExecutionController.class); context.refresh(); return context;
    }
    private LoginUser authenticate(String codes) {
        var user = new User(); user.setId(7); user.setRoleCode("QC");
        var principal = new LoginUser(user, Arrays.stream(codes.split(",")).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
        return principal;
    }
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean AssayEntryArchiveService archive() { return mock(AssayEntryArchiveService.class); }
        @Bean AssayEntryExecutionService execution() { return mock(AssayEntryExecutionService.class); }
        @Bean AssayEntryExecutionFacade facade() { return mock(AssayEntryExecutionFacade.class); }
        @Bean AssayEntryReferenceService references() { return mock(AssayEntryReferenceService.class); }
    }
}

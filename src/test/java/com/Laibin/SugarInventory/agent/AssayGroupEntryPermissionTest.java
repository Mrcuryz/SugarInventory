package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.AssayGroupEntryPreviewService;
import com.Laibin.SugarInventory.controller.AssayGroupEntryPreviewController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayGroupEntryPermissionTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @ParameterizedTest @ValueSource(strings={"assay:view", "assay:create", "quality:test", "agent:use", "ROLE_ADMIN"})
    void deniesPartialPermission(String codes) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(codes);
            assertThatThrownBy(() -> context.getBean(AssayGroupEntryPreviewController.class).preview(null, AssayGroupEntryPreviewTest.request())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> context.getBean(AssayGroupEntryPreviewController.class).previewFromReference(null, null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(AssayGroupEntryPreviewService.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"assay:view,assay:create", "assay:view,quality:test"})
    void permitsTheExistingPermissionIntersection(String codes) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(codes);
            assertThat(context.getBean(AssayGroupEntryPreviewController.class).preview(null, AssayGroupEntryPreviewTest.request()).getCode()).isEqualTo(200);
        }
    }
    private void authenticate(String codes) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "", Arrays.stream(codes.split(",")).map(SimpleGrantedAuthority::new).toList()));
    }
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean AssayGroupEntryPreviewService service() { return mock(AssayGroupEntryPreviewService.class); }
        @Bean AssayGroupEntryPreviewController controller(AssayGroupEntryPreviewService service) { return new AssayGroupEntryPreviewController(service, mock(com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService.class)); }
    }
}

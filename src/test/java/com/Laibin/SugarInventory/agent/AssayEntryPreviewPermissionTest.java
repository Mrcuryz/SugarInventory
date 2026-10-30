package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.AssayEntryPreviewService;
import com.Laibin.SugarInventory.controller.AssayEntryPreviewController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryPreviewPermissionTest {
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"assay:view", "assay:create", "quality:test", "agent:use", "ROLE_ADMIN"})
    void deniesPartialPermissionsBeforeServiceInvocation(String authorities) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(authorities);
            assertThatThrownBy(() -> context.getBean(AssayEntryPreviewController.class)
                    .preview(null, AssayEntryInputValidatorTest.valid())).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(AssayEntryPreviewService.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"assay:view,assay:create", "assay:view,quality:test"})
    void permitsActualViewingAndCreationAuthority(String authorities) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(authorities);
            var request = AssayEntryInputValidatorTest.valid();
            assertThat(context.getBean(AssayEntryPreviewController.class).preview(null, request).getCode()).isEqualTo(200);
            verify(context.getBean(AssayEntryPreviewService.class)).preview(request);
        }
    }

    private void authenticate(String authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("tester", "",
                Arrays.stream(authorities.split(",")).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean AssayEntryPreviewService service() { return mock(AssayEntryPreviewService.class); }
        @Bean AssayEntryPreviewController controller(AssayEntryPreviewService service) {
            return new AssayEntryPreviewController(service, mock(com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService.class));
        }
    }
}

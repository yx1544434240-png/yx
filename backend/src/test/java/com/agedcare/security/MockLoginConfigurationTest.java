package com.agedcare.security;

import com.agedcare.modules.auth.controller.MockAuthController;
import com.agedcare.modules.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MockLoginConfigurationTest {
    private ApplicationContextRunner runner(String profiles, String enabled) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                .withPropertyValues("app.dev-mode=" + enabled)
                .withBean(AuthService.class, () -> mock(AuthService.class))
                .withUserConfiguration(MockAuthController.class);
    }

    @Test void prodNeverRegistersMockLoginEvenWithDevProfileAndFlag() {
        for (String profiles : new String[]{"prod", "prod,dev", "prod,test"}) {
            runner(profiles, "true").run(context -> assertEquals(0, context.getBeansOfType(MockAuthController.class).size()));
        }
    }

    @Test void devRequiresExplicitFlag() {
        runner("dev", "false").run(context -> assertEquals(0, context.getBeansOfType(MockAuthController.class).size()));
        runner("dev", "true").run(context -> assertEquals(1, context.getBeansOfType(MockAuthController.class).size()));
    }
}

package com.huohuo.scenicaiguide.system.security;

import com.huohuo.scenicaiguide.common.AbstractIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AOP 鉴权切面集成测试
 * <p>
 * 验证 @RequireAuth / @RequireAuthenticated / @RequireAdmin / @RequireRoot / @RequireFence
 * 五种注解在不同角色 token 下的拦截行为
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.strict-mode=true")
@Import(AuthAspectTest.TestControllerConfig.class)
class AuthAspectTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisSessionUtil sessionUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 测试 token
    private String anonToken;
    private String authenticatedToken;
    private String adminToken;
    private String rootToken;

    // Session key（用于清理）
    private static final String ANON_SUB = "anon_test_device";
    private static final String USER_SUB = "user_1";
    private static final String ADMIN_SUB = "admin_1";
    private static final String ROOT_SUB = "admin_root_1";

    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        public TestAuthController testAuthController() {
            return new TestAuthController();
        }
    }

    @RestController
    @RequestMapping("/test-auth")
    @RequireAuth
    static class TestAuthController {
        @GetMapping("/auth")
        public String auth() {
            return "auth";
        }

        @GetMapping("/authenticated")
        @RequireAuthenticated
        public String authenticated() {
            return "authenticated";
        }

        @GetMapping("/fence")
        @RequireFence
        public String fence() {
            return "fence";
        }

        @GetMapping("/admin")
        @RequireAdmin
        public String admin() {
            return "admin";
        }

        @GetMapping("/root")
        @RequireRoot
        public String root() {
            return "root";
        }

        @GetMapping("/override")
        @RequireAdmin
        public String override() {
            return "override";
        }
    }

    @BeforeEach
    void setUp() {
        // 匿名 token（L1）
        anonToken = jwtUtil.generateToken(ANON_SUB, "TOURIST", "ANONYMOUS", 60);
        sessionUtil.create(ANON_SUB, "1", 60);

        // 手机认证游客 token（L2）
        authenticatedToken = jwtUtil.generateToken(USER_SUB, "TOURIST", "AUTHENTICATED", 60);
        sessionUtil.create(USER_SUB, "1", 60);

        // 普通管理员 token
        adminToken = jwtUtil.generateToken(ADMIN_SUB, "ADMIN", null, false, 60);
        sessionUtil.create(ADMIN_SUB, "1", 60);

        // Root 管理员 token
        rootToken = jwtUtil.generateToken(ROOT_SUB, "ADMIN", null, true, 60);
        sessionUtil.create(ROOT_SUB, "1", 60);
    }

    @AfterEach
    void tearDown() {
        sessionUtil.delete(ANON_SUB);
        sessionUtil.delete(USER_SUB);
        sessionUtil.delete(ADMIN_SUB);
        sessionUtil.delete(ROOT_SUB);
    }

    // ========== 匿名游客测试（L1） ==========

    @Test
    void anonymous_canAccessAuth() throws Exception {
        mockMvc.perform(get("/test-auth/auth")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isOk());
    }

    @Test
    void anonymous_forbiddenOnAuthenticated() throws Exception {
        mockMvc.perform(get("/test-auth/authenticated")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_forbiddenOnFence() throws Exception {
        mockMvc.perform(get("/test-auth/fence")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_forbiddenOnAdmin() throws Exception {
        mockMvc.perform(get("/test-auth/admin")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_forbiddenOnRoot() throws Exception {
        mockMvc.perform(get("/test-auth/root")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isForbidden());
    }

    // ========== 手机认证游客测试（L2） ==========

    @Test
    void authenticated_canAccessAuth() throws Exception {
        mockMvc.perform(get("/test-auth/auth")
                .header("Authorization", "Bearer " + authenticatedToken))
            .andExpect(status().isOk());
    }

    @Test
    void authenticated_canAccessAuthenticated() throws Exception {
        mockMvc.perform(get("/test-auth/authenticated")
                .header("Authorization", "Bearer " + authenticatedToken))
            .andExpect(status().isOk());
    }

    @Test
    void authenticated_forbiddenOnFence() throws Exception {
        mockMvc.perform(get("/test-auth/fence")
                .header("Authorization", "Bearer " + authenticatedToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void authenticated_forbiddenOnAdmin() throws Exception {
        mockMvc.perform(get("/test-auth/admin")
                .header("Authorization", "Bearer " + authenticatedToken))
            .andExpect(status().isForbidden());
    }

    // ========== 普通管理员测试 ==========

    @Test
    void admin_canAccessAuth() throws Exception {
        mockMvc.perform(get("/test-auth/auth")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void admin_forbiddenOnAuthenticated() throws Exception {
        // 管理员的 authLevel 为 null，不满足 @RequireAuthenticated 的 TOURIST + L2+ 要求
        mockMvc.perform(get("/test-auth/authenticated")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void admin_canAccessAdmin() throws Exception {
        mockMvc.perform(get("/test-auth/admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void admin_forbiddenOnRoot() throws Exception {
        mockMvc.perform(get("/test-auth/root")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    // ========== Root 测试 ==========

    @Test
    void root_canAccessAdmin() throws Exception {
        mockMvc.perform(get("/test-auth/admin")
                .header("Authorization", "Bearer " + rootToken))
            .andExpect(status().isOk());
    }

    @Test
    void root_canAccessRoot() throws Exception {
        mockMvc.perform(get("/test-auth/root")
                .header("Authorization", "Bearer " + rootToken))
            .andExpect(status().isOk());
    }

    // ========== 方法级覆盖类级测试 ==========

    @Test
    void methodOverride_classIsRequireAuth_methodIsRequireAdmin_anonymousForbidden() throws Exception {
        // 类级是 @RequireAuth，方法级是 @RequireAdmin
        // 方法级应覆盖类级，匿名游客访问应被拒绝
        mockMvc.perform(get("/test-auth/override")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void methodOverride_adminCanAccess() throws Exception {
        mockMvc.perform(get("/test-auth/override")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    // ========== 无 token 测试 ==========

    @Test
    void noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/test-auth/auth"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void noToken_unauthorized_responseContainsTraceId() throws Exception {
        mockMvc.perform(get("/test-auth/auth"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录或令牌无效"))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void anonymous_forbiddenOnAdmin_responseContainsTraceId() throws Exception {
        mockMvc.perform(get("/test-auth/admin")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isForbidden())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("无权限访问该资源"))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ========== Session 过期测试 ==========

    @Test
    void expiredSession_unauthorized() throws Exception {
        // 删除 Redis Session，模拟过期
        sessionUtil.delete(ANON_SUB);

        mockMvc.perform(get("/test-auth/auth")
                .header("Authorization", "Bearer " + anonToken))
            .andExpect(status().isUnauthorized());
    }
}

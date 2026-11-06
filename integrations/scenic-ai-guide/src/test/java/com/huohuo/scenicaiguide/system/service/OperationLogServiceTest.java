package com.huohuo.scenicaiguide.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.system.entity.SysOperationLog;
import com.huohuo.scenicaiguide.system.entity.SysUser;
import com.huohuo.scenicaiguide.system.mapper.SysOperationLogMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志服务测试
 * <p>
 * 纯逻辑离线测试，不起 Spring 容器。钉住三件对审计价值影响最大的行为：
 * 参数脱敏不能漏、日志写入不能弄坏业务、保留期误配不能清空审计
 */
class OperationLogServiceTest {

    private final SysOperationLogMapper logMapper = mock(SysOperationLogMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OperationLogService service =
        new OperationLogService(logMapper, userMapper, configService, objectMapper);

    // ========== 参数脱敏 ==========

    public static class LoginPayload {
        public String username;
        public String password;
        public String smsCode;
        public String phone;
    }

    public static class NestedPayload {
        public String name;
        public LoginPayload inner;
        public List<LoginPayload> items;
    }

    @Test
    void sensitiveFieldsAreFullyMasked() {
        when(configService.getInt(anyString(), anyInt())).thenReturn(2000);
        LoginPayload payload = new LoginPayload();
        payload.username = "admin";
        payload.password = "P@ssw0rd";
        payload.smsCode = "123456";
        payload.phone = "13800001111";

        String params = service.sanitizeParams(new Object[]{payload});

        assertThat(params).contains("admin");
        assertThat(params).doesNotContain("P@ssw0rd").doesNotContain("123456");
        // 手机号保留头尾：审计要能认出是谁，但不必留全量 PII
        assertThat(params).contains("138****1111").doesNotContain("13800001111");
    }

    @Test
    void maskingRecursesIntoNestedObjectsAndArrays() {
        when(configService.getInt(anyString(), anyInt())).thenReturn(2000);
        LoginPayload inner = new LoginPayload();
        inner.password = "inner-secret";
        LoginPayload listed = new LoginPayload();
        listed.password = "listed-secret";
        NestedPayload payload = new NestedPayload();
        payload.name = "batch";
        payload.inner = inner;
        payload.items = List.of(listed);

        String params = service.sanitizeParams(new Object[]{payload});

        // 嵌套一层就漏掉的话，凡是包一层 wrapper 的请求体都会明文落库
        assertThat(params).doesNotContain("inner-secret").doesNotContain("listed-secret");
        assertThat(params).contains("batch");
    }

    @Test
    void unserializableArgumentsAreReplacedByTypeName() {
        when(configService.getInt(anyString(), anyInt())).thenReturn(2000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

        String params = service.sanitizeParams(new Object[]{request, file, "keep"});

        // 请求对象和文件流没有审计价值，硬转还可能触发流读取
        assertThat(params).contains("MockHttpServletRequest").contains("MockMultipartFile").contains("keep");
    }

    @Test
    void emptyArgumentsProduceNull() {
        assertThat(service.sanitizeParams(null)).isNull();
        assertThat(service.sanitizeParams(new Object[0])).isNull();
    }

    @Test
    void longParamsAreTruncatedByConfiguredLimit() {
        when(configService.getInt(eq("ops-operation-log-param-max-length"), anyInt())).thenReturn(50);

        String params = service.sanitizeParams(new Object[]{"x".repeat(500)});

        // 地图草稿这类 payload 动辄上兆，不截断会把审计功能本身变成事故源
        assertThat(params).hasSizeLessThan(80).endsWith("...(已截断)");
    }

    @Test
    void truncateKeepsShortTextIntact() {
        assertThat(OperationLogService.truncate("短文本", 2000)).isEqualTo("短文本");
        assertThat(OperationLogService.truncate(null, 10)).isNull();
    }

    @Test
    void partialMaskFallsBackToFullMaskForShortValues() {
        assertThat(OperationLogService.maskPartial("123")).isEqualTo("***");
        assertThat(OperationLogService.maskPartial(null)).isEqualTo("***");
    }

    // ========== 开关与容错 ==========

    @Test
    void enabledDefaultsToTrueWhenConfigReadFails() {
        when(configService.getBoolean(anyString(), anyBoolean()))
            .thenThrow(new IllegalStateException("Redis down"));

        // 配置中心抖动不能让审计静默停摆——宁可多记
        assertThat(service.enabled()).isTrue();
    }

    @Test
    void recordSwallowsInsertFailure() {
        when(logMapper.insert(any(SysOperationLog.class))).thenThrow(new RuntimeException("table missing"));

        // 审计缺一条远好过让管理员的正常操作因为日志表故障而失败
        assertThatCode(() -> service.record(new SysOperationLog())).doesNotThrowAnyException();
    }

    @Test
    void recordStampsCreatedAt() {
        SysOperationLog log = new SysOperationLog();

        service.record(log);

        assertThat(log.getCreatedAt()).isNotNull();
        verify(logMapper).insert(log);
    }

    // ========== 操作人还原 ==========

    @Test
    void usernameIsDenormalizedFromUserTable() {
        SysUser user = new SysUser();
        user.setUsername("ops-lily");
        when(userMapper.selectById(7L)).thenReturn(user);

        assertThat(service.resolveUsername(7L, "admin_7")).isEqualTo("ops-lily");
    }

    @Test
    void usernameFallsBackToSubjectWhenUserMissing() {
        when(userMapper.selectById(7L)).thenReturn(null);

        // 账号被删了，"谁干的"不能跟着消失
        assertThat(service.resolveUsername(7L, "admin_7")).isEqualTo("admin_7");
        assertThat(service.resolveUsername(null, "anonymous")).isEqualTo("anonymous");
    }

    // ========== 过期清理 ==========

    @Test
    void nonPositiveRetentionMeansKeepForever() {
        when(configService.getInt(eq("ops-operation-log-retention-days"), anyInt())).thenReturn(0);

        assertThat(service.purgeExpired()).isZero();
        // 保留天数误配成 0 时若照删不误，一次调度就能抹掉全部审计
        verify(logMapper, never()).delete(any());
    }

    @Test
    void positiveRetentionDeletesExpiredRows() {
        when(configService.getInt(eq("ops-operation-log-retention-days"), anyInt())).thenReturn(30);
        when(logMapper.delete(any())).thenReturn(12);

        assertThat(service.purgeExpired()).isEqualTo(12);
        verify(logMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void retentionDaysFallsBackWhenConfigUnavailable() {
        when(configService.getInt(anyString(), anyInt())).thenThrow(new IllegalStateException("Redis down"));

        assertThat(service.retentionDays()).isEqualTo(90);
    }

    // ========== 模块列表 ==========

    @Test
    void modulesFilterOutBlankValues() {
        SysOperationLog withModule = new SysOperationLog();
        withModule.setModule("角色权限");
        SysOperationLog blank = new SysOperationLog();
        blank.setModule("  ");
        when(logMapper.selectList(any())).thenReturn(List.of(withModule, blank));

        assertThat(service.modules()).containsExactly("角色权限");
    }

    @Test
    void mapArgumentsAreMaskedToo() {
        when(configService.getInt(anyString(), anyInt())).thenReturn(2000);

        String params = service.sanitizeParams(new Object[]{Map.of("newPassword", "abc123", "keep", "ok")});

        assertThat(params).doesNotContain("abc123").contains("ok");
    }
}

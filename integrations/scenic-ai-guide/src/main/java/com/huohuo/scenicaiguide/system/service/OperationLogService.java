package com.huohuo.scenicaiguide.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.system.dto.response.OperationLogResponse;
import com.huohuo.scenicaiguide.system.entity.SysOperationLog;
import com.huohuo.scenicaiguide.system.entity.SysUser;
import com.huohuo.scenicaiguide.system.mapper.SysOperationLogMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 操作日志服务
 * <p>
 * 写入侧的第一原则是<b>绝不能弄坏业务</b>：记录失败一律吞掉只告警，
 * 审计缺一条远好过让管理员的正常操作因为日志表故障而失败
 */
@Service
public class OperationLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogService.class);

    private static final String MASK = "***";

    /**
     * 需要整体打码的字段名（小写比对）
     * <p>
     * 刻意不含 {@code code}：角色标识、景区编码都叫 code，打掉它们审计就没法看了；
     * 短信验证码字段名是 {@code smsCode}，已单独列入
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "oldpassword", "newpassword", "confirmpassword", "rawpassword",
        "secret", "apikey", "api_key", "accesskeysecret", "privatekey",
        "token", "accesstoken", "refreshtoken",
        "smscode", "captcha", "verifycode");

    /**
     * 需要脱敏但保留可辨识度的字段名
     */
    private static final Set<String> PARTIAL_FIELDS = Set.of("phone", "mobile", "telephone");

    private final SysOperationLogMapper logMapper;
    private final SysUserMapper userMapper;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public OperationLogService(SysOperationLogMapper logMapper,
                               SysUserMapper userMapper,
                               ConfigService configService,
                               ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    /**
     * 把 userId 还原成账号名并落库
     * <p>
     * 审计记录必须能脱离 sys_user 独立解读——账号删了，"谁干的"不能跟着消失，
     * 所以这里在写入时就把账号名冗余下来，而不是查询时再关联
     */
    public String resolveUsername(Long userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        try {
            SysUser user = userMapper.selectById(userId);
            return user != null && user.getUsername() != null ? user.getUsername() : fallback;
        } catch (Exception e) {
            LOGGER.debug("操作日志解析账号名失败 userId={}：{}", userId, e.getMessage());
            return fallback;
        }
    }

    // ========== 开关与阈值 ==========

    public boolean enabled() {
        try {
            return configService.getBoolean("ops-operation-log-enabled", true);
        } catch (Exception e) {
            // 配置读不出来时默认继续记录：审计宁可多记，不可因为配置中心抖动就静默停摆
            LOGGER.debug("读取操作日志开关失败，按开启处理：{}", e.getMessage());
            return true;
        }
    }

    private int paramMaxLength() {
        try {
            return configService.getInt("ops-operation-log-param-max-length", 2000);
        } catch (Exception e) {
            return 2000;
        }
    }

    // ========== 写入 ==========

    /**
     * 记录一条日志，任何异常都不向上抛
     */
    public void record(SysOperationLog log) {
        try {
            if (log.getCreatedAt() == null) {
                log.setCreatedAt(LocalDateTime.now());
            }
            logMapper.insert(log);
        } catch (Exception e) {
            LOGGER.warn("操作日志写入失败 uri={} action={}：{}", log.getUri(), log.getAction(), e.getMessage());
        }
    }

    /**
     * 把方法入参序列化成可读且安全的字符串
     * <p>
     * 三件事：跳过无法序列化的容器对象、按字段名脱敏、按配置截断。
     * 截断尤其必要——地图草稿的 payload 动辄上兆，整条塞进日志表会把审计功能变成事故源
     */
    public String sanitizeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            ArrayNode array = objectMapper.createArrayNode();
            for (Object arg : args) {
                array.add(toSafeNode(arg));
            }
            String text = array.size() == 1
                ? objectMapper.writeValueAsString(array.get(0))
                : objectMapper.writeValueAsString(array);
            return truncate(text, paramMaxLength());
        } catch (Exception e) {
            LOGGER.debug("请求参数序列化失败，按不可序列化处理：{}", e.getMessage());
            return "<参数不可序列化>";
        }
    }

    private JsonNode toSafeNode(Object arg) {
        if (arg == null) {
            return objectMapper.nullNode();
        }
        if (isUnserializable(arg)) {
            return objectMapper.getNodeFactory().textNode("<" + arg.getClass().getSimpleName() + ">");
        }
        try {
            return mask(objectMapper.valueToTree(arg));
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode("<" + arg.getClass().getSimpleName() + ">");
        }
    }

    /**
     * 请求体、响应体、文件流这类对象没有序列化价值，硬转还可能触发流读取
     * <p>
     * 用 instanceof 而不是比对包名：容器传进来的是 {@code RequestFacade} 这类实现类，
     * 包名根本不在 {@code jakarta.servlet} 下，按包名判断会漏网
     */
    private boolean isUnserializable(Object arg) {
        return arg instanceof ServletRequest
            || arg instanceof ServletResponse
            || arg instanceof MultipartFile
            || arg instanceof InputStream
            || arg instanceof OutputStream
            || arg instanceof byte[];
    }

    /**
     * 递归按字段名脱敏，字段名比对不区分大小写
     */
    private JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fields = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                String lower = field.toLowerCase(Locale.ROOT);
                if (SENSITIVE_FIELDS.contains(lower)) {
                    objectNode.put(field, MASK);
                } else if (PARTIAL_FIELDS.contains(lower)) {
                    objectNode.put(field, maskPartial(objectNode.get(field).asText(null)));
                } else {
                    mask(objectNode.get(field));
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::mask);
        }
        return node;
    }

    /**
     * 手机号保留头尾，中间打码——审计要能认出是谁，但不必留全量 PII
     */
    static String maskPartial(String value) {
        if (value == null || value.length() < 7) {
            return MASK;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    static String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(已截断)";
    }

    // ========== 查询 ==========

    public PageResponse<OperationLogResponse> page(String keyword, String module, String status,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   long page, long pageSize) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
            .like(keyword != null && !keyword.isBlank(), SysOperationLog::getUsername, keyword)
            .eq(module != null && !module.isBlank(), SysOperationLog::getModule, module)
            .eq(status != null && !status.isBlank(), SysOperationLog::getStatus, status)
            .ge(startTime != null, SysOperationLog::getCreatedAt, startTime)
            .le(endTime != null, SysOperationLog::getCreatedAt, endTime)
            .orderByDesc(SysOperationLog::getId);

        Page<SysOperationLog> result = logMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<OperationLogResponse> records = result.getRecords().stream()
            .map(OperationLogResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 已出现过的模块名，供查询页下拉使用
     */
    public List<String> modules() {
        // 用列名而不是方法引用：LambdaQueryWrapper 的 select 需要实体列缓存，
        // 只有起了容器才建得起来，这里换成普通 QueryWrapper 让它在离线单测里也可验证
        return logMapper.selectList(new QueryWrapper<SysOperationLog>()
                .select("DISTINCT module")
                .isNotNull("module")
                .orderByAsc("module"))
            .stream()
            .map(SysOperationLog::getModule)
            .filter(item -> item != null && !item.isBlank())
            .toList();
    }

    // ========== 清理 ==========

    public int retentionDays() {
        try {
            return configService.getInt("ops-operation-log-retention-days", 90);
        } catch (Exception e) {
            return 90;
        }
    }

    /**
     * 物理删除超过保留期的日志
     *
     * @return 删除条数
     */
    public int purgeExpired() {
        int days = retentionDays();
        if (days <= 0) {
            // 保留天数配成 0 或负数视为"永久保留"，不做删除，避免误配把审计全清空
            return 0;
        }
        LocalDateTime deadline = LocalDateTime.now().minusDays(days);
        int removed = logMapper.delete(new LambdaQueryWrapper<SysOperationLog>()
            .lt(SysOperationLog::getCreatedAt, deadline));
        if (removed > 0) {
            LOGGER.info("操作日志清理完成，删除 {} 条 {} 天前的记录", removed, days);
        }
        return removed;
    }
}

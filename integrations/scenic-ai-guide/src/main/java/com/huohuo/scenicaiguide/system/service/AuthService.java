package com.huohuo.scenicaiguide.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.common.redis.RedisUtils;
import com.huohuo.scenicaiguide.system.dto.request.AdminLoginRequest;
import com.huohuo.scenicaiguide.system.dto.request.TouristLoginRequest;
import com.huohuo.scenicaiguide.system.dto.response.AdminLoginResponse;
import com.huohuo.scenicaiguide.system.dto.response.TokenResponse;
import com.huohuo.scenicaiguide.system.entity.SysUser;
import com.huohuo.scenicaiguide.system.enums.SystemRedisFeature;
import com.huohuo.scenicaiguide.system.mapper.SysUserMapper;
import com.huohuo.scenicaiguide.system.security.JwtUtil;
import com.huohuo.scenicaiguide.system.security.RedisSessionUtil;
import com.huohuo.scenicaiguide.tourist.dto.response.SessionInitResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristAnonymous;
import com.huohuo.scenicaiguide.tourist.entity.TouristUser;
import com.huohuo.scenicaiguide.tourist.mapper.TouristAnonymousMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristUserMapper;
import io.jsonwebtoken.Claims;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 */
@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    /** 验证码必须用密码学安全随机源，Math.random 的输出可被推断 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysUserMapper sysUserMapper;
    private final TouristUserMapper touristUserMapper;
    private final TouristAnonymousMapper touristAnonymousMapper;
    private final JwtUtil jwtUtil;
    private final RedisSessionUtil sessionUtil;
    private final RedisUtils redisUtils;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Autowired
    public AuthService(SysUserMapper sysUserMapper,
                       TouristUserMapper touristUserMapper,
                       TouristAnonymousMapper touristAnonymousMapper,
                       JwtUtil jwtUtil,
                       RedisSessionUtil sessionUtil,
                       RedisUtils redisUtils,
                       SmsService smsService,
                       PasswordEncoder passwordEncoder,
                       Environment environment) {
        this.sysUserMapper = sysUserMapper;
        this.touristUserMapper = touristUserMapper;
        this.touristAnonymousMapper = touristAnonymousMapper;
        this.jwtUtil = jwtUtil;
        this.sessionUtil = sessionUtil;
        this.redisUtils = redisUtils;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    // ========== 管理员登录 ==========

    /**
     * 管理员账号密码登录
     *
     * @param request     登录请求
     * @param ip          客户端IP
     * @param userAgent   客户端UA
     * @return 登录响应（直接发token或需短信验证）
     */
    public AdminLoginResponse adminLogin(AdminLoginRequest request, String ip, String userAgent) {
        SysUser user = sysUserMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getStatus, 1));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // Root 强制短信验证
        if (user.getIsRoot() != null && user.getIsRoot() == 1) {
            return createSmsVerifyResponse(user);
        }

        // 普通管理员检查设备信任
        String fingerprint = generateFingerprint(ip, userAgent);
        if (checkDeviceTrust(user.getId(), fingerprint)) {
            return toAdminLoginResponse(generateAdminToken(user));
        }

        return createSmsVerifyResponse(user);
    }

    /**
     * 管理员短信验证
     *
     * @param tempToken   临时令牌
     * @param smsCode     短信验证码
     * @param ip          客户端IP
     * @param userAgent   客户端UA
     * @return Token 响应
     */
    public TokenResponse adminSmsVerify(String tempToken, String smsCode, String ip, String userAgent) {
        Claims claims = jwtUtil.parseToken(tempToken);
        String sub = claims.getSubject();
        if (!sub.startsWith("temp_admin_")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "临时令牌无效");
        }

        Long userId = Long.valueOf(sub.substring("temp_admin_".length()));
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        verifySmsCode(user.getPhone(), smsCode);

        // 普通管理员记录设备信任
        if (user.getIsRoot() == null || user.getIsRoot() == 0) {
            String fingerprint = generateFingerprint(ip, userAgent);
            saveDeviceTrust(user.getId(), fingerprint);
        }

        return generateAdminToken(user);
    }

    // ========== 短信验证码 ==========

    /**
     * 发送短信验证码
     *
     * <p>定档（P1-17）：不接真实短信通道，验证码一律随响应回显给操作者
     *
     * @param phone 手机号
     */
    public String sendSmsCode(String phone) {
        return createSmsCode(phone);
    }

    // ========== 游客匿名 ==========

    /**
     * 游客匿名身份初始化
     *
     * @param deviceId 设备ID
     * @return 初始化响应
     */
    public SessionInitResponse touristAnonymousInit(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString().replace("-", "");
        }

        TouristAnonymous anonymous = touristAnonymousMapper.selectOne(
            Wrappers.<TouristAnonymous>lambdaQuery()
                .eq(TouristAnonymous::getDeviceId, deviceId));

        if (anonymous == null) {
            anonymous = new TouristAnonymous();
            anonymous.setDeviceId(deviceId);
            anonymous.setStatus("NORMAL");
            touristAnonymousMapper.insert(anonymous);
        }

        int anonExpireDays = 7;
        String accessToken = jwtUtil.generateToken(
            "anon_" + deviceId, "TOURIST", "ANONYMOUS", anonExpireDays * 24 * 60L);

        sessionUtil.create("anon_" + deviceId, "1", anonExpireDays * 24 * 60L);

        return new SessionInitResponse(deviceId, accessToken, "ANONYMOUS");
    }

    // ========== 游客手机号登录 ==========

    /**
     * 游客手机号验证码登录
     *
     * @param request 登录请求
     * @return Token 响应
     */
    public TokenResponse touristLogin(TouristLoginRequest request) {
        verifySmsCode(request.getPhone(), request.getSmsCode());

        TouristUser user = touristUserMapper.selectOne(
            Wrappers.<TouristUser>lambdaQuery()
                .eq(TouristUser::getPhone, request.getPhone()));

        if (user == null) {
            user = new TouristUser();
            user.setPhone(request.getPhone());
            user.setStatus("NORMAL");
            touristUserMapper.insert(user);
        }

        int accessExpireMinutes = 60;
        int refreshExpireDays = 7;
        String accessToken = jwtUtil.generateToken(
            "user_" + user.getId(), "TOURIST", "AUTHENTICATED", accessExpireMinutes);
        String refreshToken = jwtUtil.generateToken(
            "user_" + user.getId(), "TOURIST", "AUTHENTICATED", refreshExpireDays * 24 * 60L);

        sessionUtil.create("user_" + user.getId(), "1", refreshExpireDays * 24 * 60L);

        return new TokenResponse(accessToken, refreshToken, (long) accessExpireMinutes * 60, "AUTHENTICATED");
    }

    // ========== Token 刷新 ==========

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新令牌
     * @return 新 Token 响应
     */
    public TokenResponse refreshToken(String refreshToken) {
        Claims claims = jwtUtil.parseToken(refreshToken);
        String sub = claims.getSubject();

        if (!sessionUtil.exists(sub)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期");
        }

        String role = claims.get("role", String.class);
        String authLevel = claims.get("authLevel", String.class);
        Boolean isRoot = claims.get("isRoot", Boolean.class);

        int accessExpireMinutes = 60;
        int refreshExpireDays = 7;

        String newAccessToken = jwtUtil.generateToken(sub, role, authLevel, isRoot, accessExpireMinutes);
        String newRefreshToken = jwtUtil.generateToken(sub, role, authLevel, isRoot, refreshExpireDays * 24 * 60L);

        sessionUtil.create(sub, "1", refreshExpireDays * 24 * 60L);

        return new TokenResponse(newAccessToken, newRefreshToken, (long) accessExpireMinutes * 60, authLevel);
    }

    // ========== 登出 ==========

    /**
     * 登出
     *
     * @param subject 用户标识（从 SecurityContext 获取）
     */
    public void logout(String subject) {
        if (subject != null) {
            sessionUtil.delete(subject);
        }
    }

    // ========== 私有方法 ==========

    private String generateFingerprint(String ip, String userAgent) {
        if (ip == null) ip = "";
        if (userAgent == null) userAgent = "";
        String ipC_part = ip.replaceAll("\\.\\d+$", ".x");
        return DigestUtils.sha256Hex(ipC_part + userAgent);
    }

    private boolean checkDeviceTrust(Long userId, String fingerprint) {
        return Boolean.TRUE.equals(redisUtils.hasKeyWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.ADMIN_TRUST,
            userId + ":" + fingerprint));
    }

    private void saveDeviceTrust(Long userId, String fingerprint) {
        redisUtils.setWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.ADMIN_TRUST,
            userId + ":" + fingerprint, "1", 30, TimeUnit.DAYS);
    }

    private AdminLoginResponse createSmsVerifyResponse(SysUser user) {
        String smsCode = createSmsCode(user.getPhone());

        AdminLoginResponse response = new AdminLoginResponse();
        response.setNeedSmsVerify(true);
        response.setTempToken(jwtUtil.generateToken(
            "temp_admin_" + user.getId(), "ADMIN", null, 5));
        response.setMaskedPhone(maskPhone(user.getPhone()));
        // 定档（P1-17）：不接真实短信，验证码随响应回显，与 profile 无关
        response.setDisplaySmsCode(smsCode);
        return response;
    }

    private String createSmsCode(String phone) {
        if (!isDevProfile()) {
            checkSmsRateLimit(phone);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

        redisUtils.setWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.SMS_CODE,
            phone, code, 5, TimeUnit.MINUTES);

        if (!isDevProfile()) {
            recordSmsRateLimit(phone);
        }

        if (isDevProfile()) {
            LOGGER.info("开发环境短信验证码 phone={}, code={}", maskPhone(phone), code);
        }
        smsService.sendVerificationCode(phone, code);
        return code;
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private TokenResponse generateAdminToken(SysUser user) {
        boolean isRoot = user.getIsRoot() != null && user.getIsRoot() == 1;
        int accessExpireMinutes = isRoot ? 30 : 60;
        int refreshExpireDays = 7;

        String sub = "admin_" + user.getId();
        String accessToken = jwtUtil.generateToken(sub, "ADMIN", null, isRoot, accessExpireMinutes);
        String refreshToken = jwtUtil.generateToken(sub, "ADMIN", null, isRoot, refreshExpireDays * 24 * 60L);

        sessionUtil.create(sub, "1", refreshExpireDays * 24 * 60L);

        return new TokenResponse(accessToken, refreshToken, (long) accessExpireMinutes * 60, null);
    }

    private AdminLoginResponse toAdminLoginResponse(TokenResponse token) {
        AdminLoginResponse response = new AdminLoginResponse();
        response.setNeedSmsVerify(false);
        response.setAccessToken(token.getAccessToken());
        response.setRefreshToken(token.getRefreshToken());
        response.setExpiresIn(token.getExpiresIn());
        return response;
    }

    private void verifySmsCode(String phone, String smsCode) {
        String cachedCode = redisUtils.getWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_CODE, phone);
        if (cachedCode == null || !cachedCode.equals(smsCode)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "验证码错误或已过期");
        }
        redisUtils.deleteWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_CODE, phone);
    }

    private void checkSmsRateLimit(String phone) {
        if (Boolean.TRUE.equals(redisUtils.hasKeyWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT, phone + ":60s"))) {
            throw new BusinessException(ErrorCode.RATE_LIMIT, "发送过于频繁，请60秒后再试");
        }
        String count10min = redisUtils.getWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT, phone + ":10min");
        if (count10min != null && Integer.parseInt(count10min) >= 5) {
            throw new BusinessException(ErrorCode.RATE_LIMIT, "发送过于频繁，请10分钟后再试");
        }
        String countDay = redisUtils.getWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT, phone + ":day");
        if (countDay != null && Integer.parseInt(countDay) >= 20) {
            throw new BusinessException(ErrorCode.RATE_LIMIT, "今日发送次数已达上限");
        }
    }

    private void recordSmsRateLimit(String phone) {
        redisUtils.setWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT,
            phone + ":60s", "1", 60, TimeUnit.SECONDS);

        String count10min = redisUtils.getWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT, phone + ":10min");
        int cnt10 = count10min == null ? 0 : Integer.parseInt(count10min);
        redisUtils.setWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT,
            phone + ":10min", String.valueOf(cnt10 + 1), 10, TimeUnit.MINUTES);

        String countDay = redisUtils.getWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT, phone + ":day");
        int cntDay = countDay == null ? 0 : Integer.parseInt(countDay);
        redisUtils.setWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.SMS_LIMIT,
            phone + ":day", String.valueOf(cntDay + 1), 24, TimeUnit.HOURS);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}

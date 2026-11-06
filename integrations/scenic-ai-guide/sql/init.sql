-- ============================================
-- 景区AI数字人导览系统 — 数据库统一初始化脚本
-- ============================================

-- 管理员表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    phone VARCHAR(20) COMMENT '手机号',
    nickname VARCHAR(50) COMMENT '昵称',
    is_root TINYINT DEFAULT 0 COMMENT '1=Root 0=普通管理员',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    last_login_at DATETIME COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0=未删除 1=已删除'
) COMMENT='管理员表';

-- 匿名访客表
CREATE TABLE IF NOT EXISTS tourist_anonymous (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    device_id VARCHAR(64) NOT NULL UNIQUE COMMENT '设备ID',
    status VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL/TEMP_BANNED/BANNED',
    ban_reason VARCHAR(255) COMMENT '封禁原因',
    ban_until DATETIME COMMENT '临时封禁到期时间',
    last_location_lat DECIMAL(10,7) COMMENT '最后位置纬度',
    last_location_lng DECIMAL(10,7) COMMENT '最后位置经度',
    last_location_at DATETIME COMMENT '最后位置时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='匿名访客表';

-- 认证游客表
CREATE TABLE IF NOT EXISTS tourist_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    phone VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像',
    status VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL/TEMP_BANNED/BANNED',
    ban_reason VARCHAR(255) COMMENT '封禁原因',
    ban_until DATETIME COMMENT '临时封禁到期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='认证游客表';

-- 游客导览会话表
CREATE TABLE IF NOT EXISTS tourist_guide_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    session_no VARCHAR(64) NOT NULL UNIQUE COMMENT '业务会话号',
    visitor_subject VARCHAR(100) NOT NULL COMMENT '游客主体 anon_xxx/user_xxx',
    scenic_area_id BIGINT NOT NULL COMMENT '景区ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ENDED',
    preference_json JSON COMMENT '游客偏好快照',
    current_spot_id VARCHAR(64) COMMENT '当前景点ID',
    current_route_id BIGINT COMMENT '当前AI路线ID',
    route_node_seq INT COMMENT '当前路线已推进到的节点序号(从1开始)，NULL表示未开始跟随',
    last_location_lat DECIMAL(10,7) COMMENT '最后位置纬度',
    last_location_lng DECIMAL(10,7) COMMENT '最后位置经度',
    last_location_at DATETIME COMMENT '最后位置时间',
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    ended_at DATETIME COMMENT '结束时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_tourist_guide_session_visitor (visitor_subject, status),
    INDEX idx_tourist_guide_session_area (scenic_area_id)
) COMMENT='游客导览会话表';

-- 游客消息表
CREATE TABLE IF NOT EXISTS tourist_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '导览会话ID',
    sender VARCHAR(20) NOT NULL COMMENT 'TOURIST/AGENT/SYSTEM',
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/ROUTE/GUIDE/TTS/ERROR',
    content TEXT NOT NULL COMMENT '消息内容',
    evidence_text VARCHAR(500) COMMENT '依据和工具调用摘要',
    audio_url VARCHAR(500) COMMENT 'TTS音频地址',
    action_state VARCHAR(32) COMMENT '数字人动作状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_tourist_message_session (session_id, created_at)
) COMMENT='游客消息表';

-- 游客位置日志表
CREATE TABLE IF NOT EXISTS tourist_location_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '导览会话ID',
    longitude DECIMAL(10,7) NOT NULL COMMENT '经度',
    latitude DECIMAL(10,7) NOT NULL COMMENT '纬度',
    accuracy_meter INT COMMENT '定位精度米',
    speed_meter_second DECIMAL(8,2) COMMENT '速度米每秒',
    reported_at DATETIME NOT NULL COMMENT '上报时间',
    accepted TINYINT NOT NULL DEFAULT 1 COMMENT '是否参与判定',
    reject_reason VARCHAR(255) COMMENT '丢弃原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_tourist_location_session_time (session_id, reported_at)
) COMMENT='游客位置日志表';

-- 游客围栏触发日志表
CREATE TABLE IF NOT EXISTS tourist_fence_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '导览会话ID',
    spot_id VARCHAR(64) NOT NULL COMMENT '景点ID',
    spot_name VARCHAR(100) COMMENT '景点名称',
    trigger_state VARCHAR(32) NOT NULL COMMENT 'OUTSIDE/ENTERING/INSIDE/COOLDOWN/EXITED',
    trigger_type VARCHAR(32) COMMENT 'ENTER/DWELL/EXIT/SKIP',
    longitude DECIMAL(10,7) COMMENT '经度',
    latitude DECIMAL(10,7) COMMENT '纬度',
    accuracy_meter INT COMMENT '定位精度米',
    detail VARCHAR(500) COMMENT '判定说明',
    broadcast_status VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/TRIGGERED/SKIPPED',
    reported_at DATETIME NOT NULL COMMENT '位置上报时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_tourist_fence_session_spot (session_id, spot_id, reported_at)
) COMMENT='游客围栏触发日志表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500) COMMENT '配置值',
    description VARCHAR(255) COMMENT '配置说明',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT COMMENT '更新人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='系统配置表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识',
    name VARCHAR(50) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '角色说明',
    builtin TINYINT DEFAULT 0 COMMENT '1=内置角色不可删除 0=自定义',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='角色表';

-- 角色权限关联表（权限码由代码侧 PermissionCode 枚举定义，此处只存勾选关系）
-- 关联表用物理删除：逻辑删除会让"取消勾选后重新勾选"撞上唯一键
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_code VARCHAR(64) NOT NULL COMMENT '权限码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_code)
) COMMENT='角色权限关联表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '管理员ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role (role_id)
) COMMENT='用户角色关联表';

-- 操作日志表（后台写操作的审计留痕）
-- 不带 deleted 字段：日志是只追加数据，过期清理必须真删才能释放磁盘，逻辑删除等于永不释放
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT COMMENT '操作人ID',
    username VARCHAR(50) COMMENT '操作人账号',
    module VARCHAR(50) COMMENT '业务模块',
    action VARCHAR(100) COMMENT '操作动作',
    http_method VARCHAR(10) COMMENT 'HTTP方法',
    uri VARCHAR(255) COMMENT '请求路径',
    params TEXT COMMENT '请求参数（已脱敏并截断）',
    ip VARCHAR(64) COMMENT '客户端IP',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS=成功 FAILURE=失败',
    error_msg VARCHAR(500) COMMENT '失败原因',
    duration_ms BIGINT COMMENT '耗时毫秒',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_created_at (created_at),
    KEY idx_user (user_id),
    KEY idx_module (module)
) COMMENT='操作日志表';

-- 景区表
CREATE TABLE IF NOT EXISTS scenic_area (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '景区名称',
    description VARCHAR(1000) COMMENT '景区描述',
    center_longitude DECIMAL(10,7) COMMENT '地图中心经度',
    center_latitude DECIMAL(10,7) COMMENT '地图中心纬度',
    default_zoom INT DEFAULT 16 COMMENT '默认缩放级别',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_scenic_area_status (status)
) COMMENT='景区表';

-- 景点表
CREATE TABLE IF NOT EXISTS scenic_spot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    name VARCHAR(100) NOT NULL COMMENT '景点名称',
    description VARCHAR(1000) COMMENT '景点描述',
    label_longitude DECIMAL(10,7) COMMENT '标注点经度',
    label_latitude DECIMAL(10,7) COMMENT '标注点纬度',
    fence_polygon JSON COMMENT '景点围栏多边形',
    fence_type VARCHAR(32) NOT NULL DEFAULT 'POLYGON' COMMENT '围栏类型 POLYGON/CIRCLE',
    fence_radius_meter INT COMMENT '半径生成围栏半径米',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_scenic_spot_area (scenic_area_id),
    INDEX idx_scenic_spot_status (status)
) COMMENT='景点表';

-- 服务点表
CREATE TABLE IF NOT EXISTS scenic_facility (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    name VARCHAR(100) NOT NULL COMMENT '服务点名称',
    facility_type VARCHAR(32) NOT NULL COMMENT '服务点类型',
    description VARCHAR(1000) COMMENT '服务点描述',
    longitude DECIMAL(10,7) NOT NULL COMMENT '经度',
    latitude DECIMAL(10,7) NOT NULL COMMENT '纬度',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_scenic_facility_area (scenic_area_id),
    INDEX idx_scenic_facility_type (facility_type),
    INDEX idx_scenic_facility_status (status)
) COMMENT='服务点表';

-- 标签表（景点分类词表，全局共享，不挂景区）
-- 地图草稿里的景点只存 code，名称与颜色在展示时按 code 回填——改词表名不破坏已发布地图
-- code 的唯一性由服务层在未删除行内保证：若用数据库唯一键，逻辑删除的行会永久占用 code，
-- 删除后重建同名标签会被旧行挡住；加上 deleted 复合键又会在"建-删-建-删"第二次删除时撞键
CREATE TABLE IF NOT EXISTS scenic_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(50) NOT NULL COMMENT '标签标识（英文小写，稳定引用）',
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    color VARCHAR(20) COMMENT '展示颜色',
    description VARCHAR(255) COMMENT '说明',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_scenic_tag_code (code)
) COMMENT='标签表';

-- 地图工作台草稿表
CREATE TABLE IF NOT EXISTS scenic_map_draft (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    name VARCHAR(100) NOT NULL COMMENT '草稿名称',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ONLINE',
    payload JSON NOT NULL COMMENT '地图草稿完整快照',
    last_saved_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后保存时间',
    created_by BIGINT COMMENT '创建人管理员ID',
    updated_by BIGINT COMMENT '更新人管理员ID',
    online_scenic_area_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ONLINE' AND deleted = 0 THEN scenic_area_id ELSE NULL END) STORED COMMENT '线上草稿唯一约束辅助列',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_scenic_map_draft_online_area (online_scenic_area_id),
    INDEX idx_scenic_map_draft_area_status (scenic_area_id, status, deleted),
    INDEX idx_scenic_map_draft_saved_at (last_saved_at)
) COMMENT='地图工作台草稿表';

-- 知识文档表
CREATE TABLE IF NOT EXISTS qa_knowledge_doc (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    title VARCHAR(150) NOT NULL COMMENT '文档标题',
    file_name VARCHAR(255) COMMENT '文件名',
    file_url VARCHAR(500) COMMENT '文件地址',
    doc_type VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/PDF/DOCX/MD/URL',
    summary VARCHAR(1000) COMMENT '内容摘要',
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSED/FAILED',
    vector_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
    chunk_count INT NOT NULL DEFAULT 0 COMMENT '切片数量',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_qa_knowledge_doc_area (scenic_area_id),
    INDEX idx_qa_knowledge_doc_vector_status (vector_status),
    INDEX idx_qa_knowledge_doc_status (status)
) COMMENT='知识文档表';

-- 知识文档切片表
CREATE TABLE IF NOT EXISTS qa_knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    doc_id BIGINT NOT NULL COMMENT '文档ID',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    chunk_index INT NOT NULL COMMENT '切片序号',
    content TEXT NOT NULL COMMENT '切片内容',
    vector_id VARCHAR(100) COMMENT '向量ID',
    token_count INT DEFAULT 0 COMMENT 'Token 数',
    hit_count INT DEFAULT 0 COMMENT '召回次数',
    vector_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_qa_chunk_doc (doc_id),
    INDEX idx_qa_chunk_area (scenic_area_id),
    INDEX idx_qa_chunk_vector_status (vector_status)
) COMMENT='知识文档切片表';

-- FAQ 表
CREATE TABLE IF NOT EXISTS qa_faq (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    question VARCHAR(500) NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '答案',
    category VARCHAR(64) COMMENT '分类',
    tags VARCHAR(255) COMMENT '标签',
    hit_count INT DEFAULT 0 COMMENT '命中次数',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_qa_faq_area (scenic_area_id),
    INDEX idx_qa_faq_status (status)
) COMMENT='FAQ表';

-- 讲解词表
CREATE TABLE IF NOT EXISTS qa_guide_script (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    title VARCHAR(150) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '讲解内容',
    language VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
    audio_asset_id BIGINT COMMENT '音频素材ID',
    video_asset_id BIGINT COMMENT '视频素材ID',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_qa_guide_script_area (scenic_area_id),
    INDEX idx_qa_guide_script_status (status)
) COMMENT='讲解词表';

-- AI路线实例表
CREATE TABLE IF NOT EXISTS route_ai_route (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '导览会话ID',
    scenic_area_id BIGINT NOT NULL COMMENT '景区ID',
    source_draft_id BIGINT NOT NULL COMMENT '来源线上地图草稿ID',
    name VARCHAR(100) NOT NULL COMMENT '路线名称',
    route_type VARCHAR(32) NOT NULL DEFAULT 'AI_CUSTOM' COMMENT '路线类型',
    preference_json JSON COMMENT '游客偏好',
    node_json JSON NOT NULL COMMENT '路线节点快照',
    segment_json JSON COMMENT '路线段快照',
    recommendation_reason VARCHAR(1000) COMMENT '推荐理由',
    estimated_duration_min INT COMMENT '预计耗时分钟',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/COMPLETED/CANCELLED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_route_ai_session (session_id),
    INDEX idx_route_ai_area (scenic_area_id)
) COMMENT='AI路线实例表';

-- 数字人配置表
CREATE TABLE IF NOT EXISTS dh_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '景区ID',
    name VARCHAR(80) NOT NULL COMMENT '数字人名称',
    avatar_url VARCHAR(500) COMMENT '头像地址',
    persona_prompt VARCHAR(2000) COMMENT '人设提示词',
    welcome_text VARCHAR(1000) COMMENT '欢迎语',
    provider VARCHAR(32) NOT NULL DEFAULT 'ALIYUN_NLS' COMMENT 'TTS供应商',
    voice_code VARCHAR(64) NOT NULL DEFAULT 'siyue' COMMENT '阿里云音色编码',
    output_format VARCHAR(16) NOT NULL DEFAULT 'wav' COMMENT '输出格式',
    sample_rate INT NOT NULL DEFAULT 16000 COMMENT '采样率',
    speech_rate INT NOT NULL DEFAULT 0 COMMENT '语速',
    pitch_rate INT NOT NULL DEFAULT 0 COMMENT '语调',
    volume INT NOT NULL DEFAULT 50 COMMENT '音量',
    action_idle_url VARCHAR(500) COMMENT '待机动作资源',
    action_listening_url VARCHAR(500) COMMENT '聆听动作资源',
    action_thinking_url VARCHAR(500) COMMENT '思考动作资源',
    action_speaking_url VARCHAR(500) COMMENT '播报动作资源',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_dh_profile_area (scenic_area_id, deleted)
) COMMENT='数字人配置表';

-- TTS缓存表
CREATE TABLE IF NOT EXISTS dh_tts_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '景区ID',
    text_hash VARCHAR(64) NOT NULL COMMENT '文本和参数哈希',
    text_preview VARCHAR(255) COMMENT '文本摘要',
    voice_code VARCHAR(64) NOT NULL COMMENT '音色编码',
    output_format VARCHAR(16) NOT NULL COMMENT '输出格式',
    sample_rate INT NOT NULL COMMENT '采样率',
    audio_url VARCHAR(500) COMMENT '音频地址',
    audio_base64 MEDIUMTEXT COMMENT '开发期音频Base64',
    hit_count INT NOT NULL DEFAULT 0 COMMENT '命中次数',
    expire_at DATETIME COMMENT '过期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_dh_tts_hash (text_hash, deleted),
    INDEX idx_dh_tts_area (scenic_area_id)
) COMMENT='TTS缓存表';

-- 运营概览日统计表
CREATE TABLE IF NOT EXISTS analytics_overview_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    visitor_count INT NOT NULL DEFAULT 0 COMMENT '服务人次',
    session_count INT NOT NULL DEFAULT 0 COMMENT '会话数',
    active_visitor_count INT NOT NULL DEFAULT 0 COMMENT '活跃游客数',
    message_count INT NOT NULL DEFAULT 0 COMMENT '消息数',
    fence_trigger_count INT NOT NULL DEFAULT 0 COMMENT '围栏触发次数',
    route_request_count INT NOT NULL DEFAULT 0 COMMENT '路线请求次数',
    route_complete_count INT NOT NULL DEFAULT 0 COMMENT '路线完成次数',
    avg_response_ms INT NOT NULL DEFAULT 0 COMMENT '平均AI响应耗时毫秒',
    positive_message_count INT NOT NULL DEFAULT 0 COMMENT '正面消息数',
    neutral_message_count INT NOT NULL DEFAULT 0 COMMENT '中性消息数',
    negative_message_count INT NOT NULL DEFAULT 0 COMMENT '疑惑问路消息数',
    complaint_message_count INT NOT NULL DEFAULT 0 COMMENT '抱怨投诉消息数',
    estimated_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '估算成本',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_analytics_overview_area_date (scenic_area_id, stat_date, deleted),
    INDEX idx_analytics_overview_date (stat_date)
) COMMENT='运营概览日统计表';

-- AI 调用消耗日统计表
CREATE TABLE IF NOT EXISTS analytics_ai_usage_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    chat_call_count INT NOT NULL DEFAULT 0 COMMENT 'Chat调用次数',
    rag_search_count INT NOT NULL DEFAULT 0 COMMENT 'RAG检索次数',
    prompt_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输入Token数',
    completion_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输出Token数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总Token数',
    tts_char_count INT NOT NULL DEFAULT 0 COMMENT 'TTS字符数',
    asr_duration_seconds DECIMAL(10,1) NOT NULL DEFAULT 0.0 COMMENT 'ASR识别秒数',
    tts_cache_hit_count INT NOT NULL DEFAULT 0 COMMENT 'TTS缓存命中次数',
    tts_cache_request_count INT NOT NULL DEFAULT 0 COMMENT 'TTS缓存请求次数',
    p95_first_token_ms INT NOT NULL DEFAULT 0 COMMENT 'P95首字延迟毫秒',
    estimated_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '估算成本',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_analytics_ai_area_date (scenic_area_id, stat_date, deleted),
    INDEX idx_analytics_ai_date (stat_date)
) COMMENT='AI调用消耗日统计表';

-- 景点日统计表
CREATE TABLE IF NOT EXISTS analytics_spot_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    spot_id VARCHAR(64) NOT NULL COMMENT '景点业务ID',
    spot_name VARCHAR(100) NOT NULL COMMENT '景点名称',
    visitor_count INT NOT NULL DEFAULT 0 COMMENT '服务人次',
    fence_trigger_count INT NOT NULL DEFAULT 0 COMMENT '围栏触发次数',
    guide_play_count INT NOT NULL DEFAULT 0 COMMENT '讲解播放次数',
    avg_stay_seconds INT NOT NULL DEFAULT 0 COMMENT '平均停留秒数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_analytics_spot_area_date_spot (scenic_area_id, stat_date, spot_id, deleted),
    INDEX idx_analytics_spot_area_date (scenic_area_id, stat_date),
    INDEX idx_analytics_spot_trigger (fence_trigger_count)
) COMMENT='景点日统计表';

-- 路线日统计表
CREATE TABLE IF NOT EXISTS analytics_route_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    scenic_area_id BIGINT NOT NULL COMMENT '所属景区ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    route_id VARCHAR(64) NOT NULL COMMENT '路线业务ID',
    route_name VARCHAR(100) NOT NULL COMMENT '路线名称',
    from_name VARCHAR(100) COMMENT '起点名称',
    to_name VARCHAR(100) COMMENT '终点名称',
    use_count INT NOT NULL DEFAULT 0 COMMENT '使用次数',
    complete_count INT NOT NULL DEFAULT 0 COMMENT '完成次数',
    exit_count INT NOT NULL DEFAULT 0 COMMENT '中途退出次数',
    avg_duration_min INT NOT NULL DEFAULT 0 COMMENT '平均耗时分钟',
    heat_level INT NOT NULL DEFAULT 1 COMMENT '热度等级1-3',
    display_path VARCHAR(500) COMMENT '首页拓扑展示路径',
    display_width INT NOT NULL DEFAULT 8 COMMENT '首页拓扑展示宽度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_analytics_route_area_date_route (scenic_area_id, stat_date, route_id, deleted),
    INDEX idx_analytics_route_area_date (scenic_area_id, stat_date),
    INDEX idx_analytics_route_heat (heat_level)
) COMMENT='路线日统计表';

-- 运营分析演示数据
INSERT IGNORE INTO analytics_overview_daily (
    scenic_area_id, stat_date, visitor_count, session_count, active_visitor_count, message_count,
    fence_trigger_count, route_request_count, route_complete_count, avg_response_ms,
    positive_message_count, neutral_message_count, negative_message_count, complaint_message_count, estimated_cost
) VALUES
(1, '2026-06-01', 520, 214, 38, 860, 138, 62, 48, 1320, 620, 164, 52, 24, 8.80),
(1, '2026-06-02', 630, 246, 44, 980, 166, 71, 55, 1260, 715, 190, 55, 20, 10.20),
(1, '2026-06-03', 590, 238, 42, 910, 148, 68, 52, 1240, 668, 174, 48, 20, 9.70),
(1, '2026-06-04', 760, 302, 58, 1180, 212, 86, 66, 1190, 875, 210, 68, 27, 13.40),
(1, '2026-06-05', 880, 344, 63, 1320, 244, 104, 79, 1210, 982, 245, 67, 26, 15.60),
(1, '2026-06-06', 1020, 392, 71, 1506, 286, 121, 92, 1170, 1128, 276, 72, 30, 18.90),
(1, '2026-06-07', 1180, 438, 82, 1720, 326, 142, 108, 1150, 1302, 306, 82, 30, 21.40),
(1, '2026-06-08', 650, 260, 46, 1008, 172, 74, 58, 1220, 730, 202, 53, 23, 11.20),
(1, '2026-06-09', 720, 292, 53, 1110, 196, 82, 61, 1180, 820, 215, 50, 25, 12.80),
(1, '2026-06-10', 800, 318, 56, 1264, 218, 93, 72, 1160, 940, 235, 60, 29, 14.90),
(1, '2026-06-11', 550, 226, 41, 876, 154, 65, 49, 1250, 640, 170, 44, 22, 9.80),
(1, '2026-06-12', 900, 356, 66, 1398, 252, 110, 84, 1110, 1035, 264, 69, 30, 17.20),
(1, '2026-06-13', 980, 376, 69, 1460, 278, 118, 91, 1080, 1104, 268, 60, 28, 18.70),
(1, '2026-06-14', 1482, 528, 126, 2240, 386, 188, 142, 1180, 1702, 336, 134, 68, 23.60),
(1, '2026-06-15', 1120, 420, 92, 1688, 304, 134, 102, 1140, 1242, 310, 94, 42, 19.40),
(1, '2026-06-16', 1268, 466, 108, 1902, 348, 156, 119, 1090, 1430, 346, 82, 44, 21.90);

INSERT IGNORE INTO analytics_ai_usage_daily (
    scenic_area_id, stat_date, chat_call_count, rag_search_count, prompt_tokens, completion_tokens,
    total_tokens, tts_char_count, asr_duration_seconds, tts_cache_hit_count, tts_cache_request_count,
    p95_first_token_ms, estimated_cost
) VALUES
(1, '2026-06-01', 1640, 1230, 32500, 16800, 49300, 70200, 1305.0, 690, 1120, 920, 8.80),
(1, '2026-06-02', 1820, 1368, 37400, 19100, 56500, 80400, 1510.0, 780, 1250, 890, 10.20),
(1, '2026-06-03', 1750, 1284, 35200, 18050, 53250, 76000, 1428.0, 750, 1198, 870, 9.70),
(1, '2026-06-04', 2288, 1730, 46800, 24100, 70900, 96000, 1810.0, 980, 1510, 860, 13.40),
(1, '2026-06-05', 2550, 1904, 53200, 27600, 80800, 108500, 2030.0, 1120, 1688, 850, 15.60),
(1, '2026-06-06', 2986, 2200, 61200, 31850, 93050, 126800, 2460.0, 1340, 2030, 830, 18.90),
(1, '2026-06-07', 3340, 2490, 70600, 36600, 107200, 142600, 2712.0, 1518, 2280, 820, 21.40),
(1, '2026-06-08', 1940, 1420, 39200, 20100, 59300, 82600, 1530.0, 804, 1260, 900, 11.20),
(1, '2026-06-09', 2105, 1584, 42800, 22300, 65100, 90800, 1668.0, 895, 1398, 870, 12.80),
(1, '2026-06-10', 2402, 1780, 48600, 25400, 74000, 101200, 1895.0, 1015, 1580, 850, 14.90),
(1, '2026-06-11', 1724, 1260, 34100, 17700, 51800, 72400, 1380.0, 730, 1160, 910, 9.80),
(1, '2026-06-12', 2660, 2016, 55600, 28600, 84200, 112000, 2180.0, 1206, 1840, 820, 17.20),
(1, '2026-06-13', 2796, 2100, 58000, 30100, 88100, 118300, 2294.0, 1280, 1960, 810, 18.70),
(1, '2026-06-14', 4120, 3286, 84600, 43800, 128400, 186000, 4350.0, 2120, 3310, 820, 23.60),
(1, '2026-06-15', 3158, 2360, 64800, 33600, 98400, 134800, 2608.0, 1425, 2200, 810, 19.40),
(1, '2026-06-16', 3560, 2688, 72400, 37600, 110000, 151200, 3070.0, 1660, 2580, 790, 21.90);

INSERT IGNORE INTO analytics_spot_daily (
    scenic_area_id, stat_date, spot_id, spot_name, visitor_count, fence_trigger_count, guide_play_count, avg_stay_seconds
) VALUES
(1, '2026-06-14', 'bridge', '望月桥', 960, 920, 680, 520),
(1, '2026-06-14', 'folk', '民俗馆', 820, 780, 556, 610),
(1, '2026-06-14', 'east', '东入口', 704, 680, 320, 180),
(1, '2026-06-14', 'academy', '云山书院', 590, 560, 410, 650),
(1, '2026-06-14', 'view', '观景台', 450, 420, 298, 480),
(1, '2026-06-15', 'bridge', '望月桥', 728, 704, 514, 500),
(1, '2026-06-15', 'folk', '民俗馆', 612, 590, 430, 590),
(1, '2026-06-15', 'east', '东入口', 522, 504, 246, 170),
(1, '2026-06-15', 'academy', '云山书院', 480, 452, 332, 620),
(1, '2026-06-15', 'view', '观景台', 390, 362, 252, 460),
(1, '2026-06-16', 'bridge', '望月桥', 812, 792, 590, 510),
(1, '2026-06-16', 'folk', '民俗馆', 694, 660, 482, 600),
(1, '2026-06-16', 'east', '东入口', 610, 584, 288, 175),
(1, '2026-06-16', 'academy', '云山书院', 524, 498, 364, 640),
(1, '2026-06-16', 'view', '观景台', 416, 390, 270, 470);

INSERT IGNORE INTO analytics_route_daily (
    scenic_area_id, stat_date, route_id, route_name, from_name, to_name, use_count, complete_count,
    exit_count, avg_duration_min, heat_level, display_path, display_width
) VALUES
(1, '2026-06-14', 'e1', '东入口至民俗馆', '东入口', '民俗馆', 420, 330, 38, 9, 2, 'M90 250 C150 240 190 205 260 180', 12),
(1, '2026-06-14', 'e2', '民俗馆至望月桥', '民俗馆', '望月桥', 610, 520, 44, 8, 3, 'M260 180 C320 155 380 145 450 140', 18),
(1, '2026-06-14', 'e3', '望月桥至餐饮休息区', '望月桥', '餐饮休息区', 372, 286, 40, 7, 2, 'M450 140 C490 170 520 215 570 245', 11),
(1, '2026-06-14', 'e4', '望月桥至云山书院', '望月桥', '云山书院', 558, 466, 42, 11, 3, 'M450 140 C525 108 610 95 700 128', 16),
(1, '2026-06-14', 'e5', '餐饮休息区至观景台', '餐饮休息区', '观景台', 238, 182, 31, 13, 1, 'M570 245 C645 260 760 250 880 205', 8),
(1, '2026-06-14', 'e6', '云山书院至观景台', '云山书院', '观景台', 344, 278, 30, 10, 2, 'M700 128 C765 135 815 170 880 205', 12),
(1, '2026-06-14', 'e7', '观景台至西出口', '观景台', '西出口', 210, 188, 12, 6, 1, 'M880 205 C925 170 945 125 980 100', 7),
(1, '2026-06-15', 'e1', '东入口至民俗馆', '东入口', '民俗馆', 320, 258, 30, 9, 2, 'M90 250 C150 240 190 205 260 180', 12),
(1, '2026-06-15', 'e2', '民俗馆至望月桥', '民俗馆', '望月桥', 470, 398, 36, 8, 3, 'M260 180 C320 155 380 145 450 140', 18),
(1, '2026-06-15', 'e3', '望月桥至餐饮休息区', '望月桥', '餐饮休息区', 286, 220, 32, 7, 2, 'M450 140 C490 170 520 215 570 245', 11),
(1, '2026-06-15', 'e4', '望月桥至云山书院', '望月桥', '云山书院', 426, 356, 34, 11, 3, 'M450 140 C525 108 610 95 700 128', 16),
(1, '2026-06-15', 'e5', '餐饮休息区至观景台', '餐饮休息区', '观景台', 188, 146, 22, 13, 1, 'M570 245 C645 260 760 250 880 205', 8),
(1, '2026-06-15', 'e6', '云山书院至观景台', '云山书院', '观景台', 268, 218, 24, 10, 2, 'M700 128 C765 135 815 170 880 205', 12),
(1, '2026-06-15', 'e7', '观景台至西出口', '观景台', '西出口', 166, 148, 10, 6, 1, 'M880 205 C925 170 945 125 980 100', 7),
(1, '2026-06-16', 'e1', '东入口至民俗馆', '东入口', '民俗馆', 356, 286, 34, 9, 2, 'M90 250 C150 240 190 205 260 180', 12),
(1, '2026-06-16', 'e2', '民俗馆至望月桥', '民俗馆', '望月桥', 528, 442, 38, 8, 3, 'M260 180 C320 155 380 145 450 140', 18),
(1, '2026-06-16', 'e3', '望月桥至餐饮休息区', '望月桥', '餐饮休息区', 324, 250, 34, 7, 2, 'M450 140 C490 170 520 215 570 245', 11),
(1, '2026-06-16', 'e4', '望月桥至云山书院', '望月桥', '云山书院', 488, 406, 36, 11, 3, 'M450 140 C525 108 610 95 700 128', 16),
(1, '2026-06-16', 'e5', '餐饮休息区至观景台', '餐饮休息区', '观景台', 204, 158, 26, 13, 1, 'M570 245 C645 260 760 250 880 205', 8),
(1, '2026-06-16', 'e6', '云山书院至观景台', '云山书院', '观景台', 296, 238, 26, 10, 2, 'M700 128 C765 135 815 170 880 205', 12),
(1, '2026-06-16', 'e7', '观景台至西出口', '观景台', '西出口', 184, 164, 11, 6, 1, 'M880 205 C925 170 945 125 980 100', 7);

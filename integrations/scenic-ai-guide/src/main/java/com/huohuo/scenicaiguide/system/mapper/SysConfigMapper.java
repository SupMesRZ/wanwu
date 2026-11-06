package com.huohuo.scenicaiguide.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huohuo.scenicaiguide.system.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统配置 Mapper
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    /**
     * 根据 key 查询配置值
     *
     * @param configKey 配置键
     * @return 配置实体
     */
    @Select("SELECT * FROM sys_config WHERE config_key = #{configKey} AND deleted = 0 LIMIT 1")
    SysConfig selectByKey(@Param("configKey") String configKey);
}

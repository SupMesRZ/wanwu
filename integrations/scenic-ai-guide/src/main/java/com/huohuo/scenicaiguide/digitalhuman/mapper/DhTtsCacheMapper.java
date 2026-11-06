package com.huohuo.scenicaiguide.digitalhuman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhTtsCache;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DhTtsCacheMapper extends BaseMapper<DhTtsCache> {

    /**
     * 按 text_hash 唯一键 upsert：命中过期或逻辑删除的历史行时原地复活刷新，
     * 避免"查不到（被过期/删除过滤）但唯一键冲突"导致的插入失败，
     * 同时把并发重复插入收敛为原子操作
     */
    @Insert("""
        INSERT INTO dh_tts_cache
          (scenic_area_id, text_hash, text_preview, voice_code, output_format, sample_rate,
           audio_base64, hit_count, expire_at, created_at, updated_at, deleted)
        VALUES
          (#{scenicAreaId}, #{textHash}, #{textPreview}, #{voiceCode}, #{outputFormat}, #{sampleRate},
           #{audioBase64}, 0, #{expireAt}, #{createdAt}, #{updatedAt}, 0)
        ON DUPLICATE KEY UPDATE
          scenic_area_id = VALUES(scenic_area_id),
          text_preview = VALUES(text_preview),
          voice_code = VALUES(voice_code),
          output_format = VALUES(output_format),
          sample_rate = VALUES(sample_rate),
          audio_base64 = VALUES(audio_base64),
          hit_count = 0,
          expire_at = VALUES(expire_at),
          updated_at = VALUES(updated_at),
          deleted = 0
        """)
    int upsertByHash(DhTtsCache entity);
}

package com.huohuo.scenicaiguide.digitalhuman;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.common.AbstractIntegrationTest;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhTtsCache;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhTtsCacheMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dh_tts_cache 唯一键 upsert 行为：过期与逻辑删除的历史同 hash 行必须能原地复活
 */
class DhTtsCacheMapperUpsertTest extends AbstractIntegrationTest {

    @Autowired
    private DhTtsCacheMapper mapper;

    @Test
    void upsertShouldReviveExpiredRowInsteadOfDuplicateKeyViolation() {
        String hash = "upsert-test-expired-" + System.nanoTime();
        mapper.insert(row(hash, "old-audio", LocalDateTime.now().minusDays(1)));

        mapper.upsertByHash(row(hash, "new-audio", LocalDateTime.now().plusDays(1)));

        DhTtsCache revived = selectByHash(hash);
        assertThat(revived.getAudioBase64()).isEqualTo("new-audio");
        assertThat(revived.getExpireAt()).isAfter(LocalDateTime.now());
        assertThat(countByHash(hash)).isEqualTo(1);
    }

    @Test
    void upsertShouldReviveLogicallyDeletedRow() {
        String hash = "upsert-test-deleted-" + System.nanoTime();
        mapper.insert(row(hash, "old-audio", LocalDateTime.now().plusDays(1)));
        mapper.deleteById(selectByHash(hash).getId());
        // 逻辑删除后普通查询查不到，但唯一键仍占用，直接 insert 必然冲突
        assertThat(countByHash(hash)).isZero();

        mapper.upsertByHash(row(hash, "revived-audio", LocalDateTime.now().plusDays(1)));

        DhTtsCache revived = selectByHash(hash);
        assertThat(revived.getAudioBase64()).isEqualTo("revived-audio");
        assertThat(revived.getDeleted()).isEqualTo(0);
        assertThat(countByHash(hash)).isEqualTo(1);
    }

    private DhTtsCache selectByHash(String hash) {
        return mapper.selectList(new QueryWrapper<DhTtsCache>().eq("text_hash", hash))
            .stream().findFirst().orElseThrow();
    }

    private long countByHash(String hash) {
        return mapper.selectCount(new QueryWrapper<DhTtsCache>().eq("text_hash", hash));
    }

    private DhTtsCache row(String hash, String audio, LocalDateTime expireAt) {
        DhTtsCache entity = new DhTtsCache();
        entity.setScenicAreaId(1L);
        entity.setTextHash(hash);
        entity.setTextPreview("预览");
        entity.setVoiceCode("siyue");
        entity.setOutputFormat("wav");
        entity.setSampleRate(16000);
        entity.setAudioBase64(audio);
        entity.setHitCount(0);
        entity.setExpireAt(expireAt);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        return entity;
    }
}

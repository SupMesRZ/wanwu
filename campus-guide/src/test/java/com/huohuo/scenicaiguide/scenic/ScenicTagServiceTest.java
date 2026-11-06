package com.huohuo.scenicaiguide.scenic;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicTagRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicTagResponse;
import com.huohuo.scenicaiguide.scenic.entity.ScenicTag;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicTagMapper;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签服务测试
 * <p>
 * 纯逻辑离线测试。钉住三条词表级约束：标识在未删除行内唯一、
 * 标识建后不可改、解析时查不到的 code 静默丢弃
 */
class ScenicTagServiceTest {

    private final ScenicTagMapper tagMapper = mock(ScenicTagMapper.class);
    private final ScenicTagService service = new ScenicTagService(tagMapper);

    private ScenicTagRequest request(String code, String name) {
        ScenicTagRequest request = new ScenicTagRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }

    private ScenicTag tag(Long id, String code, String name, int status) {
        ScenicTag tag = new ScenicTag();
        tag.setId(id);
        tag.setCode(code);
        tag.setName(name);
        tag.setStatus(status);
        return tag;
    }

    @Test
    void createRejectsDuplicateCode() {
        when(tagMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request("family", "亲子休闲")))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("RESOURCE_ALREADY_EXISTS"));
        verify(tagMapper, never()).insert(any(ScenicTag.class));
    }

    @Test
    void createDefaultsStatusToEnabled() {
        when(tagMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.create(request("family", "亲子休闲"));

        ArgumentCaptor<ScenicTag> captor = ArgumentCaptor.forClass(ScenicTag.class);
        verify(tagMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getCode()).isEqualTo("family");
    }

    @Test
    void updateKeepsOriginalCode() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "family", "亲子", 1));
        ScenicTagRequest request = request("hacked-code", "亲子休闲");

        ScenicTagResponse response = service.update(1L, request);

        // 请求里带一个不同的 code 也不能改——草稿里存的是旧 code，改了引用就全断
        assertThat(response.getCode()).isEqualTo("family");
        assertThat(response.getName()).isEqualTo("亲子休闲");
    }

    @Test
    void updateMissingTagIsNotFound() {
        when(tagMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(9L, request("a", "b")))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void deleteMissingTagIsNotFound() {
        when(tagMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(9L))
            .isInstanceOf(BusinessException.class);
        verify(tagMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void pageMapsRecordsAndTotal() {
        Page<ScenicTag> page = new Page<>(1, 20);
        page.setRecords(List.of(tag(1L, "family", "亲子休闲", 1)));
        page.setTotal(1);
        when(tagMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResponse<ScenicTagResponse> result = service.page("亲子", 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(ScenicTagResponse::getCode).containsExactly("family");
    }

    @Test
    void resolveNamesDropsUnknownCodes() {
        when(tagMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(tag(1L, "family", "亲子休闲", 1), tag(2L, "history", "历史探秘", 1)));

        // 删除、停用或拼错的 code 静默丢弃，不能让脏数据混进给 LLM 的文本
        assertThat(service.resolveNames(List.of("family", "deleted-code", "history")))
            .containsExactly("亲子休闲", "历史探秘");
        assertThat(service.resolveNames(null)).isEmpty();
        assertThat(service.resolveNames(List.of())).isEmpty();
    }

    @Test
    void enabledCodeToNameOnlyContainsReturnedRows() {
        when(tagMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(tag(1L, "family", "亲子休闲", 1)));

        Map<String, String> names = service.enabledCodeToName();

        assertThat(names).containsOnly(Map.entry("family", "亲子休闲"));
    }
}

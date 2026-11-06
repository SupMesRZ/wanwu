package com.huohuo.scenicaiguide.scenic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicTagRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicTagResponse;
import com.huohuo.scenicaiguide.scenic.entity.ScenicTag;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicTagMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签服务
 * <p>
 * 词表本身很小（几十条量级），agent 解析时直接全量读启用标签，
 * 不上缓存——词表由管理员低频维护，复杂度换不到收益
 */
@Service
public class ScenicTagService {

    private final ScenicTagMapper tagMapper;

    public ScenicTagService(ScenicTagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public PageResponse<ScenicTagResponse> page(String keyword, long page, long pageSize) {
        LambdaQueryWrapper<ScenicTag> wrapper = new LambdaQueryWrapper<ScenicTag>()
            .and(keyword != null && !keyword.isBlank(), w -> w
                .like(ScenicTag::getCode, keyword)
                .or()
                .like(ScenicTag::getName, keyword))
            .orderByAsc(ScenicTag::getId);
        Page<ScenicTag> result = tagMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<ScenicTagResponse> records = result.getRecords().stream()
            .map(ScenicTagResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 全部启用标签，供地图工作台多选下拉使用
     */
    public List<ScenicTagResponse> listEnabled() {
        return tagMapper.selectList(new LambdaQueryWrapper<ScenicTag>()
                .eq(ScenicTag::getStatus, 1)
                .orderByAsc(ScenicTag::getId))
            .stream()
            .map(ScenicTagResponse::from)
            .toList();
    }

    public ScenicTagResponse create(ScenicTagRequest request) {
        requireCodeAvailable(request.getCode().trim(), null);
        ScenicTag tag = new ScenicTag();
        apply(tag, request);
        tag.setCode(request.getCode().trim());
        tag.setCreatedAt(LocalDateTime.now());
        tag.setUpdatedAt(LocalDateTime.now());
        tag.setDeleted(0);
        tagMapper.insert(tag);
        return ScenicTagResponse.from(tag);
    }

    public ScenicTagResponse update(Long id, ScenicTagRequest request) {
        ScenicTag tag = requireTag(id);
        // code 是草稿里的稳定引用，与角色标识同理建后不允许改，请求里的 code 直接忽略
        apply(tag, request);
        tag.setUpdatedAt(LocalDateTime.now());
        tagMapper.updateById(tag);
        return ScenicTagResponse.from(tag);
    }

    public void delete(Long id) {
        ScenicTag tag = requireTag(id);
        // 不做"是否被草稿引用"检查：引用散落在各地图草稿的 JSON payload 里，查不全也查不准；
        // 删除后旧 code 在解析时自然落空，前端与 agent 都按"查不到即不展示"处理
        tagMapper.deleteById(tag.getId());
    }

    /**
     * 启用标签的 code → 名称映射，供 agent 把草稿里的 code 解析成可读文本
     */
    public Map<String, String> enabledCodeToName() {
        List<ScenicTag> tags = tagMapper.selectList(new LambdaQueryWrapper<ScenicTag>()
            .eq(ScenicTag::getStatus, 1));
        Map<String, String> result = new LinkedHashMap<>();
        for (ScenicTag tag : tags) {
            result.put(tag.getCode(), tag.getName());
        }
        return result;
    }

    /**
     * 把一组 code 解析成启用标签的名称，查不到（删除/停用/拼错）的静默丢弃
     */
    public List<String> resolveNames(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = enabledCodeToName();
        return codes.stream()
            .map(names::get)
            .filter(name -> name != null && !name.isBlank())
            .toList();
    }

    private ScenicTag requireTag(Long id) {
        ScenicTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    private void requireCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<ScenicTag> wrapper = new LambdaQueryWrapper<ScenicTag>()
            .eq(ScenicTag::getCode, code)
            .ne(excludeId != null, ScenicTag::getId, excludeId);
        Long count = tagMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "标签标识已存在：" + code);
        }
    }

    private void apply(ScenicTag tag, ScenicTagRequest request) {
        tag.setName(request.getName().trim());
        tag.setColor(request.getColor());
        tag.setDescription(request.getDescription());
        tag.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}

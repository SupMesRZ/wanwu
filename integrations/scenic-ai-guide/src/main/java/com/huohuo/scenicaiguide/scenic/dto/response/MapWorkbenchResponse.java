package com.huohuo.scenicaiguide.scenic.dto.response;

import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import lombok.Data;

import java.util.List;

@Data
public class MapWorkbenchResponse {

    private List<ScenicAreaResponse> scenicAreas;

    private ScenicAreaResponse currentArea;

    private ScenicMapDraftResponse currentDraft;

    private List<ScenicMapDraftResponse> drafts;

    private MapWorkbenchDraftRequest payload;
}

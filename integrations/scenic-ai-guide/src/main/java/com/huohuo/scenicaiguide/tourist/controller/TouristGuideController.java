package com.huohuo.scenicaiguide.tourist.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.AsrResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhProfileResponse;
import com.huohuo.scenicaiguide.digitalhuman.service.AsrService;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsStreamService;
import com.huohuo.scenicaiguide.system.security.AuthPrincipal;
import com.huohuo.scenicaiguide.system.security.RequireAuth;
import com.huohuo.scenicaiguide.system.security.RequireAuthenticated;
import com.huohuo.scenicaiguide.tourist.dto.request.GuideMessageRequest;
import com.huohuo.scenicaiguide.tourist.dto.request.GuideSessionStartRequest;
import com.huohuo.scenicaiguide.tourist.dto.request.LocationReportRequest;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideHistoryMessageResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideMessageResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideSessionResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideStreamEvent;
import com.huohuo.scenicaiguide.tourist.dto.response.LocationReportResponse;
import com.huohuo.scenicaiguide.tourist.service.TouristGuideService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/tourist/guide")
public class TouristGuideController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TouristGuideController.class);

    private final TouristGuideService touristGuideService;
    private final DigitalHumanService digitalHumanService;
    private final TtsStreamService ttsStreamService;
    private final AsrService asrService;

    public TouristGuideController(TouristGuideService touristGuideService,
                                  DigitalHumanService digitalHumanService,
                                  TtsStreamService ttsStreamService,
                                  AsrService asrService) {
        this.touristGuideService = touristGuideService;
        this.digitalHumanService = digitalHumanService;
        this.ttsStreamService = ttsStreamService;
        this.asrService = asrService;
    }

    @GetMapping("/profile")
    public ApiResponse<DhProfileResponse> profile() {
        return ApiResponse.ok(digitalHumanService.getDefaultProfileResponse());
    }

    /**
     * 一句话语音识别：接收前端按住说话产出的 16k 单声道 WAV
     */
    @PostMapping("/asr")
    @RequireAuthenticated
    public ApiResponse<AsrResponse> asr(@RequestParam("audio") MultipartFile audio) throws Exception {
        return ApiResponse.ok(new AsrResponse(asrService.transcribe(audio.getBytes())));
    }

    @PostMapping("/sessions")
    @RequireAuth
    public ApiResponse<GuideSessionResponse> start(@Valid @RequestBody GuideSessionStartRequest request) {
        AuthPrincipal principal = resolvePrincipal();
        return ApiResponse.ok(touristGuideService.start(principal.subject(), principal.authLevel(), request));
    }

    @GetMapping("/sessions/{sessionId}")
    @RequireAuth
    public ApiResponse<GuideSessionResponse> resume(@PathVariable Long sessionId) {
        AuthPrincipal principal = resolvePrincipal();
        return ApiResponse.ok(touristGuideService.resume(principal.subject(), principal.authLevel(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @RequireAuth
    public ApiResponse<List<GuideHistoryMessageResponse>> history(@PathVariable Long sessionId) {
        AuthPrincipal principal = resolvePrincipal();
        return ApiResponse.ok(touristGuideService.history(principal.subject(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @RequireAuth
    public ApiResponse<GuideMessageResponse> message(@PathVariable Long sessionId,
                                                      @Valid @RequestBody GuideMessageRequest request) {
        AuthPrincipal principal = resolvePrincipal();
        return ApiResponse.ok(touristGuideService.message(
            principal.subject(), principal.authLevel(), sessionId, request));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireAuth
    public Flux<ServerSentEvent<GuideStreamEvent>> messageStream(
        @PathVariable Long sessionId,
        @Valid @RequestBody GuideMessageRequest request) {
        AuthPrincipal principal = resolvePrincipal();
        return Flux.defer(() -> {
            Sinks.Many<GuideStreamEvent> progressEvents =
                Sinks.many().unicast().onBackpressureBuffer();
            Flux<GuideStreamEvent> work = Flux.defer(() -> prepareStreamEvents(
                principal, sessionId, request, progressEvents))
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(ignored -> progressEvents.tryEmitComplete());
            return Flux.concat(
                    Flux.just(GuideStreamEvent.status("正在理解你的需求")),
                    Flux.merge(progressEvents.asFlux(), work))
                .map(this::event);
        });
    }

    private Flux<GuideStreamEvent> prepareStreamEvents(AuthPrincipal principal,
                                                       Long sessionId,
                                                       GuideMessageRequest request,
                                                       Sinks.Many<GuideStreamEvent> progressEvents) {
        TouristGuideService.GuideStreamPlan plan;
        try {
            plan = touristGuideService.prepareStream(
                principal.subject(), principal.authLevel(), sessionId, request,
                status -> progressEvents.tryEmitNext(GuideStreamEvent.status(status)));
        } catch (BusinessException exception) {
            return Flux.just(GuideStreamEvent.failed(exception.getMessage()));
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to prepare tourist guide stream sessionId={}", sessionId, exception);
            return Flux.just(GuideStreamEvent.failed("路线与导览服务暂时不可用，请稍后重试"));
        }
        return streamPlan(plan, request);
    }

    private Flux<GuideStreamEvent> streamPlan(TouristGuideService.GuideStreamPlan plan,
                                              GuideMessageRequest request) {
        if (plan.isCompleted()) {
            GuideMessageResponse message = plan.completedMessage();
            Flux<GuideStreamEvent> responseEvents = Flux.just(
                GuideStreamEvent.delta(message.getAnswer()), GuideStreamEvent.completed(message));
            Flux<GuideStreamEvent> audioEvents = speechEnabled(request)
                ? speechEvents(plan.scenicAreaId(), Flux.just(message.getAnswer()))
                : Flux.empty();
            return Flux.merge(responseEvents, audioEvents)
                .onErrorResume(error -> Flux.just(GuideStreamEvent.failed(
                    "语音服务暂时不可用，文字与路线结果仍可继续使用")));
        }

        StringBuilder answer = new StringBuilder();
        Flux<String> content = plan.content()
            .filter(delta -> delta != null && !delta.isEmpty())
            .doOnNext(answer::append);
        Flux<GuideStreamEvent> guideEvents = content.publish(shared -> {
            Flux<GuideStreamEvent> deltas = shared.map(GuideStreamEvent::delta);
            Mono<GuideStreamEvent> completed = Mono.fromCallable(() -> GuideStreamEvent.completed(
                touristGuideService.completeStream(
                    plan.sessionId(), plan.scenicAreaId(), answer.toString(), plan.evidence(),
                    plan.pathVisualization(), plan.recommendedRoute(), plan.routePlan(), speechEnabled(request))));
            Flux<GuideStreamEvent> speech = speechEnabled(request)
                ? speechEvents(plan.scenicAreaId(), shared)
                : Flux.empty();
            return Flux.merge(deltas.concatWith(completed), speech);
        });
        return guideEvents
            .onErrorResume(error -> Flux.just(GuideStreamEvent.failed("AI 导游暂时无法回答，请稍后再试")));
    }

    @PostMapping("/sessions/{sessionId}/locations")
    @RequireAuthenticated
    public ApiResponse<LocationReportResponse> reportLocation(@PathVariable Long sessionId,
                                                              @Valid @RequestBody LocationReportRequest request) {
        return ApiResponse.ok(touristGuideService.reportLocation(resolvePrincipal().subject(), sessionId, request));
    }

    private AuthPrincipal resolvePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("游客身份未注入");
    }

    private ServerSentEvent<GuideStreamEvent> event(GuideStreamEvent data) {
        return ServerSentEvent.builder(data).event(data.getType()).build();
    }

    private Flux<GuideStreamEvent> speechEvents(Long scenicAreaId, Flux<String> content) {
        return ttsStreamService.synthesize(scenicAreaId, content)
            .map(chunk -> chunk.error() == null
                ? GuideStreamEvent.audio(chunk.sequence(), chunk.text(), chunk.audioBase64(),
                    chunk.format(), chunk.cached())
                : GuideStreamEvent.audioFailed(chunk.sequence(), chunk.text(), chunk.error()));
    }

    private boolean speechEnabled(GuideMessageRequest request) {
        return !Boolean.FALSE.equals(request.getSpeechEnabled());
    }
}

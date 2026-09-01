package com.consumoesperto.mobilecapture.controller;

import com.consumoesperto.mobilecapture.dto.ConfirmMobileCaptureEventRequest;
import com.consumoesperto.mobilecapture.dto.MobileCaptureEventReviewDto;
import com.consumoesperto.mobilecapture.dto.MobileIngestionResultDto;
import com.consumoesperto.mobilecapture.service.MobileCaptureReviewService;
import com.consumoesperto.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile-capture/review")
@RequiredArgsConstructor
public class MobileCaptureReviewController {

  private final MobileCaptureReviewService reviewService;

  @GetMapping("/events")
  public ResponseEntity<List<MobileCaptureEventReviewDto>> listNeedsReview(
      @AuthenticationPrincipal UserPrincipal user
  ) {
    return ResponseEntity.ok(reviewService.listNeedsReview(user.getId()));
  }

  @PostMapping("/events/{id}/confirm")
  public ResponseEntity<MobileIngestionResultDto> confirm(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable Long id,
      @RequestBody ConfirmMobileCaptureEventRequest request
  ) {
    return ResponseEntity.ok(reviewService.confirm(user.getId(), id, request));
  }

  @PostMapping("/events/{id}/discard")
  public ResponseEntity<Map<String, String>> discard(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable Long id
  ) {
    reviewService.discard(user.getId(), id);
    return ResponseEntity.ok(Map.of("status", "REJECTED"));
  }
}

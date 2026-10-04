package com.civicflow.appointment.client;

import com.civicflow.common.api.ApiResponse;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "civicflow-resource",
        url = "${civicflow.appointment.resource-client.url:}",
        configuration = ResourceClientConfig.class)
public interface ResourceSlotClient {
    @GetMapping("/internal/v1/resource/slots/{slotId}/snapshot")
    ApiResponse<ResourceSlotSnapshot> getSnapshot(@PathVariable("slotId") long slotId);

    @GetMapping("/internal/v1/resource/slots/preheat-candidates")
    ApiResponse<ResourceSlotCandidatePage> getPreheatCandidates(
            @RequestParam("releaseFrom") Instant releaseFrom,
            @RequestParam("releaseTo") Instant releaseTo,
            @RequestParam("afterId") long afterId,
            @RequestParam("size") int size);

    @GetMapping("/internal/v1/resource/slots/reconciliation-candidates")
    ApiResponse<ResourceSlotCandidatePage> getReconciliationCandidates(
            @RequestParam("at") Instant at,
            @RequestParam("afterId") long afterId,
            @RequestParam("size") int size);
}

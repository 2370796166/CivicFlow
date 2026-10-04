package com.civicflow.resource.service;

import com.civicflow.resource.dto.response.SlotCandidatePageResponse;
import com.civicflow.resource.dto.response.SlotSnapshotResponse;
import java.time.Instant;

public interface ResourceSlotSnapshotService {
    SlotSnapshotResponse get(long slotId);

    SlotCandidatePageResponse candidates(
            Instant releaseFrom, Instant releaseTo, long afterId, int size);

    SlotCandidatePageResponse reconciliationCandidates(Instant at, long afterId, int size);
}

package com.civicflow.appointment.service;

import com.civicflow.appointment.client.ResourceSlotSnapshot;

public interface StockPreheatService {
    PreheatResult preheat(ResourceSlotSnapshot snapshot);

    PreheatResult preheatOne(long slotId, long actorId, String requestId);

    PreheatBatchResult preheatWindow();

    record PreheatResult(String slotId, String outcome, long remaining, long configVersion) {}

    record PreheatBatchResult(int visited, int applied, int unchanged, int failed) {}
}

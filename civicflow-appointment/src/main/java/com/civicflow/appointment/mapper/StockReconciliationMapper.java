package com.civicflow.appointment.mapper;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;

public interface StockReconciliationMapper {
    @org.apache.ibatis.annotations.Select(
            "SELECT (SELECT COUNT(*) FROM appointment_reservation_request WHERE slot_id=#{slotId}) + (SELECT COUNT(*) FROM appointment_order WHERE slot_id=#{slotId}) + (SELECT COUNT(*) FROM stock_release_record WHERE slot_id=#{slotId})")
    long countSlotHistory(@Param("slotId") long slotId);

    Facts selectFacts(@Param("slotId") long slotId, @Param("stableBefore") Instant stableBefore);

    List<ActiveReservation> selectActiveReservations(
            @Param("slotId") long slotId, @Param("limit") int limit);

    @Getter
    @Setter
    class Facts {
        private long charged;
        private long successfulRelease;
        private long persistedConsumed;
        private long pendingReserved;
        private long pendingRelease;
        private long pendingCompensations;
        private long orphanOrders;
        private long invalidRelease;
        private long invalidGuard;
        private long recentActivity;
    }

    @Getter
    @Setter
    class ActiveReservation {
        private long userId;
        private String reservationId;
    }
}

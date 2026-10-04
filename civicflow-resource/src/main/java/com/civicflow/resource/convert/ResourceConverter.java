package com.civicflow.resource.convert;

import com.civicflow.resource.config.ContactPhoneProtector;
import com.civicflow.resource.dto.response.ItemResponse;
import com.civicflow.resource.dto.response.OutletResponse;
import com.civicflow.resource.dto.response.SlotResponse;
import com.civicflow.resource.dto.response.WindowResponse;
import com.civicflow.resource.entity.ResourceSlotEntity;
import com.civicflow.resource.entity.ServiceItemEntity;
import com.civicflow.resource.entity.ServiceOutletEntity;
import com.civicflow.resource.entity.ServiceWindowEntity;

public final class ResourceConverter {
    private ResourceConverter() {}

    public static OutletResponse toOutlet(
            ServiceOutletEntity entity, ContactPhoneProtector protector) {
        String maskedContact = null;
        if (entity.getContactPhoneCipher() != null) {
            maskedContact =
                    protector.mask(
                            protector.decrypt(
                                    entity.getContactPhoneCipher(),
                                    entity.getContactPhoneKeyVersion()));
        }
        return new OutletResponse(
                entity.getId().toString(),
                entity.getCode(),
                entity.getName(),
                entity.getAddress(),
                entity.getLongitude(),
                entity.getLatitude(),
                maskedContact,
                entity.getStatus(),
                entity.getVersion());
    }

    public static ItemResponse toItem(ServiceItemEntity entity) {
        return new ItemResponse(
                entity.getId().toString(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getDefaultDurationMinutes(),
                entity.getStatus(),
                entity.getVersion());
    }

    public static WindowResponse toWindow(ServiceWindowEntity entity) {
        return new WindowResponse(
                entity.getId().toString(),
                entity.getOutletId().toString(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getVersion());
    }

    public static SlotResponse toSlot(ResourceSlotEntity entity) {
        return new SlotResponse(
                entity.getId().toString(),
                entity.getOutletId().toString(),
                entity.getItemId().toString(),
                entity.getServiceDate(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getTotalQuota(),
                entity.getReleaseAt(),
                entity.getCheckInStart(),
                entity.getCheckInEnd(),
                entity.getStatus(),
                entity.getConfigVersion(),
                entity.getConsumedHint(),
                entity.getVersion());
    }
}

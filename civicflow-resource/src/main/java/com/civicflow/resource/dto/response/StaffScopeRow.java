package com.civicflow.resource.dto.response;

public record StaffScopeRow(
        Long outletId,
        String outletCode,
        String outletName,
        Long windowId,
        String windowCode,
        String windowName,
        Long itemId,
        String itemCode,
        String itemName) {}

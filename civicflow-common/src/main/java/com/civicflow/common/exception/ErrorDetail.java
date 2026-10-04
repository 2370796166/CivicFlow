package com.civicflow.common.exception;

/** Optional machine-readable detail for validation and other common failures. */
public record ErrorDetail(String field, String reason) {}

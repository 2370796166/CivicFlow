package com.civicflow.common.api;

/** Stable error-code contract shared by transport responses and business exceptions. */
public interface GlobalErrorCode {

    String code();

    String message();
}

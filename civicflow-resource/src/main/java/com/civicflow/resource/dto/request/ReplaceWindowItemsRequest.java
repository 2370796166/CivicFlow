package com.civicflow.resource.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ReplaceWindowItemsRequest(
        @NotNull @Size(max = 100) Set<@Pattern(regexp = "[1-9][0-9]{0,19}") String> itemIds,
        @Min(0) int version) {}

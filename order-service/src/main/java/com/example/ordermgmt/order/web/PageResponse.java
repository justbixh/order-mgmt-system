package com.example.ordermgmt.order.web;

import java.util.List;

/**
 * Minimal, stable JSON shape for paged results.
 *
 * @param content       the items of this page
 * @param page          zero-based page number
 * @param size          page size
 * @param totalElements total number of items across all pages
 * @param <T>           item type
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
}

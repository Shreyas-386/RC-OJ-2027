package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Wraps a text so it is written as a JSON string, for example "User Not Found" (with the quotes).
 * The Node admin login returns its errors in exactly this form.
 */
public record JsonString(@JsonValue String value) {
}

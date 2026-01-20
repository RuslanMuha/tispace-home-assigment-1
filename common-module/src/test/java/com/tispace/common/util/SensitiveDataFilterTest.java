package com.tispace.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataFilterTest {

    @Test
    void maskSensitiveData_whenContainsPassword_thenMasksValue() {
        String message = "user=alice password=secret123";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertTrue(masked.contains("password="), "Key should remain");
        assertTrue(masked.contains("***"), "Value should be masked");
        assertFalse(masked.contains("secret123"), "Original secret must not appear");
    }

    @Test
    void maskSensitiveData_whenContainsApiKey_thenMasksValue() {
        String message = "apiKey: ABC-123-XYZ";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertTrue(masked.toLowerCase().contains("apikey"), "Key should remain");
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("ABC-123-XYZ"));
    }

    @Test
    void maskSensitiveData_whenContainsToken_thenMasksValue() {
        String message = "token=\"very-secret-token\" other=value";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertTrue(masked.contains("token"));
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("very-secret-token"));
    }

    @Test
    void maskSensitiveData_whenJsonFormat_thenMasksCorrectly() {
        String message = "{\"password\":\"p1\",\"apiKey\":\"k2\"}";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertTrue(masked.contains("\"password\""));
        assertTrue(masked.contains("\"apiKey\""));
        assertFalse(masked.contains("p1"));
        assertFalse(masked.contains("k2"));
        assertTrue(masked.contains("***"));
    }

    @Test
    void maskSensitiveData_whenKeyValueFormat_thenMasksCorrectly() {
        String message = "auth: Bearer abcdef, other=1";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertTrue(masked.contains("auth:"));
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("abcdef"));
    }

    @Test
    void maskSensitiveData_whenNoSensitiveData_thenReturnsOriginal() {
        String message = "status=ok";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertEquals(message, masked);
    }

    @Test
    void maskSensitiveData_whenNullOrBlank_thenReturnsSame() {
        assertNull(SensitiveDataFilter.maskSensitiveData(null));
        assertEquals("", SensitiveDataFilter.maskSensitiveData(""));
        assertEquals("   ", SensitiveDataFilter.maskSensitiveData("   "));
    }

    @Test
    void containsSensitiveData_whenContainsSecret_thenReturnsTrue() {
        String message = "pwd=my-secret";

        assertTrue(SensitiveDataFilter.containsSensitiveData(message));
    }

    @Test
    void containsSensitiveData_whenNoSensitiveData_thenReturnsFalse() {
        String message = "info=public";

        assertFalse(SensitiveDataFilter.containsSensitiveData(message));
    }

    @Test
    void containsSensitiveData_whenNullOrBlank_thenReturnsFalse() {
        assertFalse(SensitiveDataFilter.containsSensitiveData(null));
        assertFalse(SensitiveDataFilter.containsSensitiveData(""));
        assertFalse(SensitiveDataFilter.containsSensitiveData("   "));
    }

    @Test
    void maskSensitiveData_whenKeysHaveDifferentCase_thenMasksRegardlessOfCase() {
        String message = "Password=abc123 SECRET=xyz";

        String masked = SensitiveDataFilter.maskSensitiveData(message);

        assertTrue(masked.contains("Password="));
        assertTrue(masked.contains("SECRET="));
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("xyz"));
    }

    @Test
    void maskSensitiveData_whenAuthorizationBearer_thenMasksAll() {
        String msg = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        String masked = SensitiveDataFilter.maskSensitiveData(msg);
        assertFalse(masked.contains("eyJhbGci"));
        assertTrue(masked.contains("***"));
    }

    @Test
    void maskSensitiveData_whenMultipleKeys_thenMasksAll() {
        String msg = "password=1 token=2 apiKey=3";
        String masked = SensitiveDataFilter.maskSensitiveData(msg);
        assertEquals("password=*** token=*** apiKey=***", masked);
    }

    @Test
    void maskSensitiveData_whenJsonWithSpaces_thenMasks() {
        String msg = "{ \"password\" : \"p1\" , \"token\" : \"t2\" }";
        String masked = SensitiveDataFilter.maskSensitiveData(msg);
        assertFalse(masked.contains("p1"));
        assertFalse(masked.contains("t2"));
        assertTrue(masked.contains("\"password\""));
    }

    @Test
    void maskSensitiveData_whenEndsWithDelimiter_thenMasks() {
        String msg = "token=abc, next=1";
        String masked = SensitiveDataFilter.maskSensitiveData(msg);
        assertFalse(masked.contains("abc"));
        assertTrue(masked.contains("token=***,"));
    }

    @Test
    void maskSensitiveData_whenUrlQuery_thenMasks() {
        String msg = "GET /v1?a=1&apiKey=SECRET123&b=2";
        String masked = SensitiveDataFilter.maskSensitiveData(msg);
        assertFalse(masked.contains("SECRET123"));
    }

}




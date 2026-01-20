package com.tispace.queryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleFlightEnvelope {

    private boolean success;
    private String payload;     // JSON result
    private String errorCode;   // safe short code
    private String message;
}

package com.ncba.countryinfo.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(Instant timestamp, int status, String error, String message,
                            String path, String correlationId, List<String> details) {
}

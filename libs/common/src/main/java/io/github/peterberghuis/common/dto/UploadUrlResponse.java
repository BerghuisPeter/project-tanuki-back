package io.github.peterberghuis.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadUrlResponse {
    private String uploadUrl;
    private String fileName;
    private Long maxSizeBytes;
    private List<String> allowedContentTypes;
}

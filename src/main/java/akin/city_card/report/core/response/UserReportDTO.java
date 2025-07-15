package akin.city_card.report.core.response;

import akin.city_card.report.model.ReportCategory;
import akin.city_card.report.model.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReportDTO {
    private Long id;
    private ReportCategory category;
    private String message;
    private List<String> photoUrls;
    private List<UserReportResponseDTO> responses;
    private List<UserReportResponseDTO> replies;
    private ReportStatus status;
    private LocalDateTime createdAt;
}

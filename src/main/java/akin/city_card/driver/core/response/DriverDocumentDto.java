package akin.city_card.driver.core.response;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDocumentDto {
    private Long id;
    private String documentName;
    private String documentType;
    private LocalDate expiryDate;
    private String filePath;
}

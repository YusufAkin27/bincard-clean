/*package akin.city_card.report.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "reports")
public class ReportSearchDocument {

    @Id
    private String id;

    private Long reportId;
    private Long userId;
    private String category;
    private String status;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String message;

    @Field(type = FieldType.Text, analyzer = "standard")
    private List<String> responses;

    private Long createdAt;
}


 */
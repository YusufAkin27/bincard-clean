/*package akin.city_card.report.repository;

import akin.city_card.report.model.ReportSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Optional;

public interface ReportSearchRepository extends ElasticsearchRepository<ReportSearchDocument, String> {
    Page<ReportSearchDocument> findByMessageContainingOrResponsesContaining(
            String keyword, String keyword2, Pageable pageable);

    Optional<ReportSearchDocument> findByReportId(Long reportId);
}

 */

/*package akin.city_card.report.service.abstracts;

import akin.city_card.report.model.ReportSearchDocument;
import akin.city_card.report.repository.ReportSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ReportSearchService {

    private final ReportSearchRepository searchRepository;

    public Page<ReportSearchDocument> search(String keyword, Pageable pageable) {
        Page<ReportSearchDocument> result;

        if (keyword == null || keyword.isBlank()) {
            result = searchRepository.findAll(pageable);
        } else {
            String cleanedKeyword = keyword.replaceAll("[\"*?]", "");
            cleanedKeyword = cleanedKeyword.trim().replaceAll("\\s+", " ");

            if (cleanedKeyword.isEmpty()) {
                result = searchRepository.findAll(pageable);
            } else {
                result = searchRepository.findByMessageContainingOrResponsesContaining(cleanedKeyword, cleanedKeyword, pageable);
            }
        }

        // 🔍 Sonuç özeti
        System.out.println("📄 Toplam Sayfa: " + result.getTotalPages());
        System.out.println("📦 Toplam Kayıt: " + result.getTotalElements());
        System.out.println("📍 Geçerli Sayfa: " + result.getNumber());
        System.out.println("📈 Sayfa Boyutu: " + result.getSize());
        System.out.println("---------------------------");

        // 🔎 Detaylı sonuç listesi
        result.forEach(doc -> {
            System.out.println("🔹 ID: " + doc.getId());
            System.out.println("🔹 Rapor ID: " + doc.getReportId());
            System.out.println("🔹 Kullanıcı ID: " + doc.getUserId());
            System.out.println("🔹 Mesaj: " + doc.getMessage());
            System.out.println("🔹 Yanıtlar: " + doc.getResponses());
            System.out.println("🔹 Oluşturulma: " + doc.getCreatedAt());
            System.out.println("---------------------------");
        });

        return result;
    }



}

 */

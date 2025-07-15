package akin.city_card.report.service.concretes;

import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.admin.model.Admin;
import akin.city_card.admin.repository.AdminRepository;
import akin.city_card.cloudinary.MediaUploadService;
import akin.city_card.report.core.converter.ReportConverter;
import akin.city_card.report.core.request.AddReportRequest;
import akin.city_card.report.core.response.AdminReportDTO;
import akin.city_card.report.core.response.ReportStatsDTO;
import akin.city_card.report.core.response.UserReportDTO;
import akin.city_card.report.exceptions.*;
import akin.city_card.report.model.*;
import akin.city_card.report.repository.*;
import akin.city_card.report.service.abstracts.ReportService;
import akin.city_card.report.service.abstracts.ReportSpecification;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.security.repository.SecurityUserRepository;
import akin.city_card.superadmin.model.SuperAdmin;
import akin.city_card.superadmin.repository.SuperAdminRepository;
import akin.city_card.user.core.response.Views;
import akin.city_card.user.exceptions.FileFormatCouldNotException;
import akin.city_card.user.exceptions.OnlyPhotosAndVideosException;
import akin.city_card.user.exceptions.PhotoSizeLargerException;
import akin.city_card.user.exceptions.VideoSizeLargerException;
import akin.city_card.user.model.User;
import akin.city_card.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class ReportManager implements ReportService {

    private final ReportRepository reportRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final ReportResponseRepository reportResponseRepository;
    private final ReportResponseRatingRepository reportResponseRatingRepository;
    private final MediaUploadService mediaUploadService;
    private final ReportConverter reportConverter;
    private final ReportPhotoRepository reportPhotoRepository;
    private final SecurityUserRepository securityUserRepository;
    private final SuperAdminRepository superAdminRepository;

    @Override
    @Transactional
    public ResponseMessage addReport(AddReportRequest addReportRequest, List<MultipartFile> photos, String username)
            throws AddReportRequestNullException, UserNotFoundException, PhotoSizeLargerException, IOException, OnlyPhotosAndVideosException, VideoSizeLargerException, FileFormatCouldNotException {

        if (addReportRequest == null) {
            throw new AddReportRequestNullException();
        }

        User user = findByUserName(username);
        Report report = Report.builder()
                .user(user)
                .category(addReportRequest.getCategory())
                .message(addReportRequest.getMessage())
                .status(ReportStatus.OPEN)
                .deleted(false)
                .isActive(true)
                .archived(false)
                .build();

        report = reportRepository.save(report);

        // Handle photos
        if (photos != null && !photos.isEmpty()) {
            List<ReportPhoto> reportPhotos = new ArrayList<>();
            for (MultipartFile photo : photos) {
                String photoUrl = mediaUploadService.uploadAndOptimizeMedia(photo);
                ReportPhoto reportPhoto = new ReportPhoto();
                reportPhoto.setImageUrl(photoUrl);
                reportPhoto.setUploadedAt(LocalDateTime.now());
                reportPhoto.setReport(report);
                reportPhotos.add(reportPhoto);
                reportPhotoRepository.save(reportPhoto);
            }
            report.setPhotos(reportPhotos);
            reportRepository.save(report);
        }
        /*
        ReportSearchDocument document = ReportSearchDocument.builder()
                .id(UUID.randomUUID().toString()) // ES için benzersiz ID
                .reportId(report.getId())
                .userId(user.getId())
                .category(report.getCategory().name())
                .status(report.getStatus().name())
                .message(report.getMessage())
                .responses(report.getResponses()==null ? null:report.getResponses().stream().map(ReportResponse::getResponseMessage).toList()) // Henüz yanıt yoksa boş
                .createdAt(report.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) // dönüşüm burada
                .build();

        reportSearchRepository.save(document); // Elasticsearch'e kaydet


         */

        return new ResponseMessage("Rapor başarıyla oluşturuldu", true);
    }

    @Override
    public ResponseMessage deleteReport(Long reportId, String username)
            throws ReportNotFoundException, ReportAlreadyDeletedException, ReportNotActiveException, UserNotFoundException {

        User user = findByUserName(username);
        Report report = findById(reportId);


        if (!report.getUser().equals(user)) {
            throw new ReportNotFoundException();
        }

        if (report.isDeleted()) {
            throw new ReportAlreadyDeletedException();
        }

        if (!report.isActive()) {
            throw new ReportNotActiveException();
        }

        report.setDeleted(true); // Fixed: was false
        report.setActive(false);
        report.setStatus(ReportStatus.CANCELLED);
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);
/*
        reportSearchRepository
                .findByReportId(report.getId())
                .ifPresent(doc -> reportSearchRepository.deleteById(doc.getId()));

 */

        return new ResponseMessage("Rapor başarıyla silindi.", true);
    }

    @Override
    public ResponseMessage updateReport(Long reportId, String username, String message)
            throws ReportNotFoundException, UserNotFoundException {

        User user = findByUserName(username);
        Report report = findById(reportId);

        if (!report.getUser().equals(user)) {
            throw new ReportNotFoundException();
        }

        if (report.isDeleted() || !report.isActive()) {
            throw new ReportNotFoundException();
        }

        boolean updated = false;

        if (message != null && !message.trim().isEmpty() && !report.getMessage().equals(message)) {
            report.setMessage(message);
            report.setUpdatedAt(LocalDateTime.now());
            updated = true;
        }

        if (updated) {
            reportRepository.save(report);
/*
            reportSearchRepository.findByReportId(report.getId()).ifPresent(doc -> {
                doc.setMessage(report.getMessage());
                doc.setCreatedAt(report.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                reportSearchRepository.save(doc);
            });

 */

        }

        return new ResponseMessage("Rapor başarıyla güncellendi", true);
    }


    @Override
    public List<AdminReportDTO> getAllReportsForAdmin(String adminUsername, Pageable pageable) {
        return reportRepository.findAll(pageable)
                .stream()
                .map(reportConverter::convertToAdminReportDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserReportDTO> getAllReportsForUser(String username, Pageable pageable) throws UserNotFoundException {
        User user = findByUserName(username);
        return reportRepository.findByUserAndDeletedFalse(user, pageable)
                .stream()
                .map(reportConverter::convertToUserReportDTO)
                .collect(Collectors.toList());
    }



    public Page<AdminReportDTO> search(Optional<String> keyword,
                                       Optional<ReportCategory> category,
                                       Optional<ReportStatus> status,
                                       Pageable pageable) {

        Specification<Report> spec = (root, query, cb) -> cb.conjunction();

        if (category.isPresent()) {
            spec = spec.and(ReportSpecification.hasCategory(category.get()));
        }

        if (status.isPresent()) {
            spec = spec.and(ReportSpecification.hasStatus(status.get()));
        }

        if (keyword.isPresent()) {
            spec = spec.and(ReportSpecification.containsKeyword(keyword.get()));
        }

        Page<Report> reportPage = reportRepository.findAll(spec, pageable);

        return reportPage.map(reportConverter::convertToAdminReportDTO);
    }

    @Override
    public ResponseMessage changeStatus(Long reportId, ReportStatus status, String username)
            throws AdminNotFoundException, ReportNotFoundException {

        Admin admin = adminRepository.findByUserNumber(username);
        if (admin == null) {
            throw new AdminNotFoundException();
        }

        Report report = findById(reportId);
        report.setStatus(status);
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);
/*
        reportSearchRepository.findByReportId(report.getId()).ifPresent(doc -> {
            doc.setStatus(status.name());
            doc.setCreatedAt(report.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            reportSearchRepository.save(doc);
        });

 */

        return new ResponseMessage("Rapor durumu güncellendi", true);
    }

    @Override
    public ResponseMessage toggleDeleteReport(Long reportId, String username)
            throws AdminNotFoundException, ReportNotFoundException {

        Admin admin = adminRepository.findByUserNumber(username);
        if (admin == null) {
            throw new AdminNotFoundException();
        }

        Report report = findById(reportId);
        report.setDeleted(!report.isDeleted());
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);

        String message = report.isDeleted() ? "Rapor silindi" : "Rapor geri yüklendi";
        return new ResponseMessage(message, true);
    }

    @Override
    @Transactional
    public ResponseMessage replyToReportAsAdmin(Long reportId, String username, String message)
            throws AdminNotFoundException, ReportNotFoundException, ReportIsDeletedException, ReportNotActiveException {

        SecurityUser securityUser = securityUserRepository.findByUserNumber(username)
                .orElseThrow(AdminNotFoundException::new);

        Report report = findById(reportId);

        if(report.isDeleted()){
            throw new ReportIsDeletedException();
        }
        if (!report.isActive()){
            throw new ReportNotActiveException();
        }
        ReportResponse response = ReportResponse.builder()
                .report(report)
                .admin(securityUser)
                .user(report.getUser())
                .responseMessage(message)
                .build();

        reportResponseRepository.save(response);

        // Report durumu güncelleniyor
        if (report.getStatus() == ReportStatus.OPEN) {
            report.setStatus(ReportStatus.IN_REVIEW);
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.save(report);
        }
/*
        // Elasticsearch dokümanı güncelleniyor
        reportSearchRepository.findByReportId(report.getId()).ifPresent(doc -> {
            List<String> updatedResponses = new ArrayList<>(doc.getResponses() != null ? doc.getResponses() : new ArrayList<>());
            updatedResponses.add(message);
            doc.setResponses(updatedResponses);
            doc.setStatus(report.getStatus().name());
            reportSearchRepository.save(doc);
        });


 */
        return new ResponseMessage("Yanıt başarıyla gönderildi", true);
    }


    @Override
    @Transactional
    public ResponseMessage replyToReportResponse(Long responseId, String username, String message)
            throws ReportNotFoundException, UserNotFoundException {

        User user = findByUserName(username);

        ReportResponse parentResponse = reportResponseRepository.findById(responseId)
                .orElseThrow(ReportNotFoundException::new);

        ReportResponse reply = ReportResponse.builder()
                .report(parentResponse.getReport())
                .user(user)
                .parent(parentResponse)
                .responseMessage(message)
                .build();

        reportResponseRepository.save(reply);
/*
        // Elasticsearch dokümanını güncelle
        reportSearchRepository.findByReportId(parentResponse.getReport().getId()).ifPresent(doc -> {
            List<String> updatedResponses = new ArrayList<>(doc.getResponses() != null ? doc.getResponses() : new ArrayList<>());
            updatedResponses.add(message);
            doc.setResponses(updatedResponses);
            reportSearchRepository.save(doc);
        });

 */

        return new ResponseMessage("Yanıt başarıyla gönderildi", true);
    }

    @Override
    public ResponseMessage deleteResponse(Long responseId, String username)
            throws ReportNotFoundException, UserNotFoundException, AdminNotFoundException {

        ReportResponse response = reportResponseRepository.findById(responseId)
                .orElseThrow(ReportNotFoundException::new);

        Admin admin = adminRepository.findByUserNumber(username);
        SuperAdmin superAdmin=superAdminRepository.findByUserNumber(username);

        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        boolean canDelete = false;
        if (superAdmin!=null){
            canDelete = true;
        }
        if (admin != null) {
            canDelete = true; // Admin can delete any response
        } else if (user != null && response.getUser() != null && response.getUser().equals(user)) {
            canDelete = true; // User can delete their own response
        }

        if (!canDelete) {
            throw new UserNotFoundException();
        }
/*
        // Elasticsearch'ten de silinen mesajı çıkar
        reportSearchRepository.findByReportId(response.getReport().getId()).ifPresent(doc -> {
            List<String> responses = doc.getResponses() != null ? new ArrayList<>(doc.getResponses()) : new ArrayList<>();
            responses.remove(response.getResponseMessage()); // içerik eşleşirse sil
            doc.setResponses(responses);
            reportSearchRepository.save(doc);
        });


 */
        reportResponseRepository.delete(response);

        return new ResponseMessage("Yanıt başarıyla silindi", true);
    }


    @Override
    public ResponseMessage rateResponse(Long responseId, String username, int rating)
            throws UserNotFoundException, ReportNotFoundException {

        User user = findByUserName(username);

        ReportResponse response = reportResponseRepository.findById(responseId)
                .orElseThrow(ReportNotFoundException::new);

        // Check if user already rated this response
        Optional<ReportResponseRating> existingRating = reportResponseRatingRepository
                .findByUserAndResponse(user, response);

        if (existingRating.isPresent()) {
            // Update existing rating
            ReportResponseRating ratingEntity = existingRating.get();
            ratingEntity.setRating(rating);
            reportResponseRatingRepository.save(ratingEntity);
            return new ResponseMessage("Değerlendirme güncellendi", true);
        } else {
            // Create new rating
            ReportResponseRating ratingEntity = ReportResponseRating.builder()
                    .user(user)
                    .response(response)
                    .rating(rating)
                    .build();
            reportResponseRatingRepository.save(ratingEntity);
            return new ResponseMessage("Değerlendirme başarıyla gönderildi", true);
        }
    }

    @Override
    public ResponseMessage updateRating(Long ratingId, String username, int rating)
            throws UserNotFoundException {

        User user = findByUserName(username);

        ReportResponseRating ratingEntity = reportResponseRatingRepository.findById(ratingId)
                .orElseThrow(() -> new UserNotFoundException());

        // Check if user owns the rating
        if (!ratingEntity.getUser().equals(user)) {
            throw new UserNotFoundException();
        }

        ratingEntity.setRating(rating);
        reportResponseRatingRepository.save(ratingEntity);

        return new ResponseMessage("Değerlendirme güncellendi", true);
    }

    @Override
    public ResponseMessage deleteRating(Long ratingId, String username) throws UserNotFoundException {
        User user = findByUserName(username);

        ReportResponseRating ratingEntity = reportResponseRatingRepository.findById(ratingId)
                .orElseThrow(() -> new UserNotFoundException());

        // Check if user owns the rating
        if (!ratingEntity.getUser().equals(user)) {
            throw new UserNotFoundException();
        }

        reportResponseRatingRepository.delete(ratingEntity);

        return new ResponseMessage("Değerlendirme silindi", true);
    }

    @Override
    public List<ReportResponse> getAllResponsesByUser(String username) throws UserNotFoundException {
        User user = findByUserName(username);
        return reportResponseRepository.findByUserOrderByRespondedAtDesc(user);
    }

    @Override
    public List<ReportResponse> getReportResponses(Long reportId) throws ReportNotFoundException {
        Report report = findById(reportId);
        return reportResponseRepository.findByReportOrderByRespondedAtAsc(report);
    }

    @Override
    public ResponseMessage batchToggleReports(List<Long> reportIds, boolean delete, String username)
            throws AdminNotFoundException {

        Admin admin = adminRepository.findByUserNumber(username);
        if (admin == null) {
            throw new AdminNotFoundException();
        }

        List<Report> reports = reportRepository.findAllById(reportIds);

        for (Report report : reports) {
            report.setDeleted(delete);
            report.setUpdatedAt(LocalDateTime.now());
        }

        reportRepository.saveAll(reports);

        String message = delete ? "Raporlar silindi" : "Raporlar geri yüklendi";
        return new ResponseMessage(message, true);
    }

    @Override
    public ResponseMessage archiveReport(Long reportId, String username)
            throws AdminNotFoundException, ReportNotFoundException {

        Admin admin = adminRepository.findByUserNumber(username);
        if (admin == null) {
            throw new AdminNotFoundException();
        }

        Report report = findById(reportId);
        report.setArchived(!report.isArchived());
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);

        String message = report.isArchived() ? "Rapor arşivlendi" : "Rapor arşivden çıkarıldı";
        return new ResponseMessage(message, true);
    }

    @Override
    public ReportStatsDTO getReportStats(String username) throws AdminNotFoundException {
        Admin admin = adminRepository.findByUserNumber(username);
        if (admin == null) {
            throw new AdminNotFoundException();
        }

        long totalReports = reportRepository.count();
        long openReports = reportRepository.countByStatus(ReportStatus.OPEN);
        long inReviewReports = reportRepository.countByStatus(ReportStatus.IN_REVIEW);
        long resolvedReports = reportRepository.countByStatus(ReportStatus.RESOLVED);
        long rejectedReports = reportRepository.countByStatus(ReportStatus.REJECTED);
        long deletedReports = reportRepository.countByDeletedTrue();
        long archivedReports = reportRepository.countByArchivedTrue();

        return ReportStatsDTO.builder()
                .totalReports(totalReports)
                .openReports(openReports)
                .inReviewReports(inReviewReports)
                .resolvedReports(resolvedReports)
                .rejectedReports(rejectedReports)
                .deletedReports(deletedReports)
                .archivedReports(archivedReports)
                .build();
    }


    @Override
    public List<AdminReportDTO> getReportsByCategoryAdmin(ReportCategory category, Pageable pageable) {
        return reportRepository.findByCategory(category, pageable)
                .stream()
                .map(reportConverter::convertToAdminReportDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<UserReportDTO> getUserReportsFiltered(String username, ReportCategory category, Pageable pageable)
            throws UserNotFoundException {

        User user = findByUserName(username);
        Page<Report> reportPage;

        if (category != null) {
            reportPage = reportRepository.findByUserAndCategoryAndDeletedFalseAndIsActiveTrue(user, category, pageable);
        } else {
            reportPage = reportRepository.findByUserAndDeletedFalseAndActiveTrue(user, pageable);
        }

        return reportPage.map(reportConverter::convertToUserReportDTO);
    }

    @Override
    public AdminReportDTO getReportByIdAsAdmin(Long reportId) {
        return reportRepository.findById(reportId).map(reportConverter::convertToAdminReportDTO).orElse(null);
    }

    @Override
    public UserReportDTO getReportByIdAsUser(Long reportId, String username) throws ReportIsDeletedException, ReportNotFoundException, UserNotFoundException {
        User user = findByUserName(username);
        Report report = reportRepository.findById(reportId).orElseThrow(ReportNotFoundException::new);
        if (!report.getUser().equals(user)) {
            throw new ReportNotFoundException();
        }
        if (!report.isActive() || report.isDeleted()) {
            throw new ReportIsDeletedException();
        }
        return reportRepository.findById(reportId).map(reportConverter::convertToUserReportDTO).orElse(null);
    }


    @Override
    public List<Report> getReportByCategory(ReportCategory category, String username)
            throws UserNotFoundException, CategoryNotFoundExecption {

        if (category == null) {
            throw new CategoryNotFoundExecption();
        }

        Admin admin = adminRepository.findByUserNumber(username);
        if (admin != null) {
            return reportRepository.findAllByCategory(category);
        }

        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);


        return reportRepository.findAllByCategoryAndUserAndDeletedFalse(category, user);
    }

    // Helper methods
    private User findByUserName(String userName) throws UserNotFoundException {
        return userRepository.findByUserNumber(userName).orElseThrow(UserNotFoundException::new);
    }

    private Report findById(Long reportId) throws ReportNotFoundException {
        return reportRepository.findById(reportId)
                .orElseThrow(ReportNotFoundException::new);
    }
}
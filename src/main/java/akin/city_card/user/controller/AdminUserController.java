package akin.city_card.user.controller;

import akin.city_card.bus.exceptions.UnauthorizedAccessException;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.entity.Role;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.security.exception.VerificationCodeStillValidException;
import akin.city_card.user.core.request.CreateUserRequestList;
import akin.city_card.user.core.request.PermanentDeleteRequest;
import akin.city_card.user.core.request.SuspendUserRequest;
import akin.city_card.user.core.request.UnsuspendUserRequest;
import akin.city_card.user.core.response.CacheUserDTO;
import akin.city_card.user.core.response.Views;
import akin.city_card.user.exceptions.InvalidPhoneNumberFormatException;
import akin.city_card.user.exceptions.PhoneNumberAlreadyExistsException;
import akin.city_card.user.exceptions.PhoneNumberRequiredException;
import akin.city_card.user.model.*;
import akin.city_card.user.service.abstracts.UserService;
import akin.city_card.wallet.exceptions.AdminOrSuperAdminNotFoundException;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hibernate.Cache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/v1/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    private void isAdminOrSuperAdmin(UserDetails userDetails) throws UnauthorizedAccessException {
        if (userDetails == null || userDetails.getAuthorities() == null) {
            throw new UnauthorizedAccessException();
        }

        boolean authorized = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ADMIN") || role.equals("SUPERADMIN"));

        if (!authorized) {
            throw new UnauthorizedAccessException();
        }
    }

    // =============== 1. KULLANICI LİSTESİ YÖNETİMİ ===============

    /**
     * Tüm kullanıcıları sayfalama ile listele
     */
    @GetMapping
    @JsonView(Views.Admin.class)
    public PageDTO<CacheUserDTO> getAllUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);
        return userService.getAllUsers(pageable);
    }

    /**
     * Kullanıcı arama ve filtreleme
     */
    @GetMapping("/search")
    @JsonView(Views.Admin.class)
    public PageDTO<CacheUserDTO> searchUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String query,
            Pageable pageable) throws UnauthorizedAccessException {

        isAdminOrSuperAdmin(userDetails);

        return userService.searchUsers(query, pageable);
    }


    /**
     * Kullanıcı hesaplarını toplu olarak pasifleştirme/aktifleştirme
     */
    @PutMapping("/bulk-status-update")
    public ResponseMessage bulkUpdateUserStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) throws UnauthorizedAccessException, AdminOrSuperAdminNotFoundException {
        isAdminOrSuperAdmin(userDetails);

        List<Long> userIds = (List<Long>) request.get("userIds");
        UserStatus newStatus = UserStatus.valueOf((String) request.get("status"));

        return userService.bulkUpdateUserStatus(userIds, newStatus, userDetails.getUsername());

    }

    /**
     * Toplu kullanıcı silme
     */
    @DeleteMapping("/bulk-delete")
    public ResponseMessage bulkDeleteUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody List<Long> userIds) throws UnauthorizedAccessException, AdminOrSuperAdminNotFoundException {
        isAdminOrSuperAdmin(userDetails);

        return userService.bulkDeleteUsers(userIds, userDetails.getUsername());
    }

    // =============== 2. KULLANICI DETAYLARI ===============

    /**
     * Belirli kullanıcının detaylı bilgilerini görüntüleme
     */
    @GetMapping("/{userId}")
    public CacheUserDTO getUserDetails(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId) throws UnauthorizedAccessException, UserNotFoundException, AdminOrSuperAdminNotFoundException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserById(userId,userDetails.getUsername());
    }

    /**
     * Kullanıcıya ait cihaz ve IP bilgilerini görüntüleme
     */
    @GetMapping("/{userId}/device-info")
    public Map<String, Object> getUserDeviceInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId) throws UnauthorizedAccessException, UserNotFoundException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserDeviceInfo(userId,userDetails.getUsername());

    }

    // =============== 3. ROL VE YETKİ YÖNETİMİ ===============

    /**
     * Kullanıcıya rol atama
     */
    @PostMapping("/{userId}/roles")
    public ResponseMessage assignRolesToUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Set<Role> roles) throws UnauthorizedAccessException, AdminOrSuperAdminNotFoundException {
        isAdminOrSuperAdmin(userDetails);

        return userService.assignRolesToUser(userId, roles, userDetails.getUsername());

    }

    /**
     * Kullanıcıdan rol kaldırma
     */
    @DeleteMapping("/{userId}/roles")
    public ResponseMessage removeRolesFromUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Set<Role> roles) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

       return userService.removeRolesFromUser(userId, roles, userDetails.getUsername());

    }

    /**
     * Toplu rol atama
     */
    @PostMapping("/bulk-role-assignment")
    public ResponseMessage bulkAssignRoles(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        List<Long> userIds = (List<Long>) request.get("userIds");
        Set<Role> roles = Set.of(Role.valueOf((String) request.get("role")));

        return  userService.bulkAssignRoles(userIds, roles, userDetails.getUsername());

    }

    // =============== 4. PAROLA VE GÜVENLİK ===============

    /**
     * Admin tarafından kullanıcı parolasını sıfırlama
     */
    @PostMapping("/{userId}/reset-password")
    public ResponseMessage resetUserPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        String newPassword = request.get("newPassword");
        boolean forceChange = Boolean.parseBoolean(request.getOrDefault("forceChange", "true"));

     return userService.resetUserPassword(userId, newPassword, forceChange, userDetails.getUsername());

    }


    /**
     * E-posta doğrulama durumu güncelleme
     */
    @PutMapping("/{userId}/email-verification")
    public ResponseMessage updateEmailVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        boolean verified = request.get("verified");
        return userService.updateEmailVerificationStatus(userId, verified, userDetails.getUsername());

    }

    /**
     * Telefon doğrulama durumu güncelleme
     */
    @PutMapping("/{userId}/phone-verification")
    public ResponseMessage updatePhoneVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        boolean verified = request.get("verified");
        return userService.updatePhoneVerificationStatus(userId, verified, userDetails.getUsername());

    }

    // =============== 6. OTURUM YÖNETİMİ ===============

    /**
     * Kullanıcının aktif oturumlarını listeleme
     */
    @GetMapping("/{userId}/active-sessions")
    public List<Map<String, Object>> getUserActiveSessions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserActiveSessions(userId);
    }

    /**
     * Kullanıcının belirli oturumunu sonlandırma
     */
    @DeleteMapping("/{userId}/sessions/{sessionId}")
    public ResponseMessage terminateUserSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @PathVariable String sessionId) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.terminateUserSession(userId, sessionId, userDetails.getUsername());
    }


    /**
     * IP adresini engelleme
     */
    @PostMapping("/ip-ban")
    public ResponseMessage banIpAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        String ipAddress = (String) request.get("ipAddress");
        String reason = (String) request.get("reason");
        LocalDateTime expiresAt = request.containsKey("expiresAt") ?
                LocalDateTime.parse((String) request.get("expiresAt")) : null;

        return userService.banIpAddress(ipAddress, reason, expiresAt, userDetails.getUsername());
    }

    // Kullanıcıyı askıya alma
    @PostMapping("/admin/{userId}/suspend")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseMessage suspendUser(
            @PathVariable Long userId,
            @RequestBody SuspendUserRequest request,
            @AuthenticationPrincipal UserDetails adminDetails
    ) throws UserNotFoundException, UnauthorizedAreaException {
        return userService.suspendUser(adminDetails.getUsername(), userId, request);
    }

    // Kullanıcı hesabını kalıcı olarak silme
    @DeleteMapping("/admin/{userId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseMessage permanentlyDeleteUser(
            @PathVariable Long userId,
            @RequestBody PermanentDeleteRequest request,
            @AuthenticationPrincipal UserDetails adminDetails
    ) throws UserNotFoundException, UnauthorizedAreaException {
        return userService.permanentlyDeleteUser(adminDetails.getUsername(), userId, request);
    }

    // Kullanıcı askıya alma işlemini kaldırma
    @PostMapping("/admin/{userId}/unsuspend")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseMessage unsuspendUser(
            @PathVariable Long userId,
            @RequestBody UnsuspendUserRequest request,
            @AuthenticationPrincipal UserDetails adminDetails
    ) throws UserNotFoundException, UnauthorizedAreaException {
        return userService.unsuspendUser(adminDetails.getUsername(), userId, request);
    }
    /**
     * Cihaz engelleme
     */
    @PostMapping("/{userId}/device-ban")
    public ResponseMessage banUserDevice(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        String deviceId = (String) request.get("deviceId");
        String reason = (String) request.get("reason");

       return userService.banUserDevice(userId, deviceId, reason, userDetails.getUsername());

    }

    // =============== 8. ŞÜPHELİ HAREKETLER VE GÜVENLİK ===============

    /**
     * Şüpheli giriş işlemlerini listeleme
     */
    @GetMapping("/suspicious-activities")
    public Page<Map<String, Object>> getSuspiciousActivities(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String activityType,
            Pageable pageable) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getSuspiciousActivities(
                startDate, endDate, activityType, pageable);

    }

    /**
     * Kullanıcı audit log'larını görüntüleme
     */
    @GetMapping("/{userId}/audit-logs")
    public Page<Map<String, Object>> getUserAuditLogs(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String action,
            Pageable pageable) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserAuditLogs(
                userId, startDate, endDate, action, pageable);
    }

    // =============== 9. ANALİTİK VE RAPORLAMA ===============

    /**
     * Kullanıcı istatistikleri
     */
    @GetMapping("/statistics")
    public Map<String, Object> getUserStatistics(
            @AuthenticationPrincipal UserDetails userDetails) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserStatistics();
    }

    /**
     * Kullanıcı giriş geçmişi raporlama
     */
    @GetMapping("/{userId}/login-history")
    public Page<LoginHistory> getUserLoginHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Pageable pageable) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserLoginHistory(userId, startDate, endDate, pageable);
    }

    /**
     * Kullanıcı arama geçmişi
     */
    @GetMapping("/{userId}/search-history")
    public Page<SearchHistory> getUserSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Pageable pageable) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserSearchHistory(userId, startDate, endDate, pageable);
    }

    // =============== 10. BİLDİRİM VE İLETİŞİM ===============

    /**
     * Kullanıcıya bildirim gönderme
     */
    @PostMapping("/{userId}/send-notification")
    public ResponseMessage sendNotificationToUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> notificationData) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        String title = (String) notificationData.get("title");
        String message = (String) notificationData.get("message");
        String type = (String) notificationData.get("type");

        return userService.sendNotificationToUser(userId, title, message, type, userDetails.getUsername());
    }

    /**
     * Toplu bildirim gönderme
     */
    @PostMapping("/bulk-notification")
    public ResponseMessage sendBulkNotification(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> notificationData) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        List<Long> userIds = (List<Long>) notificationData.get("userIds");
        String title = (String) notificationData.get("title");
        String message = (String) notificationData.get("message");
        String type = (String) notificationData.get("type");

        return userService.sendBulkNotification(userIds, title, message, type, userDetails.getUsername());
    }

    // =============== 11. EKSTRA ÖZELLİKLER ===============

    /**
     * Kullanıcı bilgilerini PDF olarak e-posta ile gönderme
     */
    @PostMapping("/{userId}/export-pdf")
    public ResponseMessage exportUserDataToPdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        String emailAddress = request.get("emailAddress");
        String language = request.getOrDefault("language", "tr");

        return userService.exportUserDataToPdf(userId, emailAddress, language, userDetails.getUsername());
    }

    /**
     * Kullanıcı verilerini Excel olarak dışa aktarma
     */
    @GetMapping("/export-excel")
    public void exportUsersToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Role role,
            HttpServletResponse response) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        userService.exportUsersToExcel(userIds, status, role, response, userDetails.getUsername());
    }



    /**
     * Kullanıcı davranış analizi
     */
    @GetMapping("/{userId}/behavior-analysis")
    public Map<String, Object> getUserBehaviorAnalysis(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "30") int days) throws UnauthorizedAccessException {
        isAdminOrSuperAdmin(userDetails);

        return userService.getUserBehaviorAnalysis(userId, days);
    }

    // =============== MEVCUT METOD (KORUNDU) ===============

    @PostMapping("/collective-sign-up")
    public List<ResponseMessage> collectiveSignUp(@AuthenticationPrincipal UserDetails userDetails,
                                                  @Valid @RequestBody CreateUserRequestList createUserRequestList,
                                                  HttpServletRequest httpServletRequest) throws PhoneNumberRequiredException, InvalidPhoneNumberFormatException, PhoneNumberAlreadyExistsException, VerificationCodeStillValidException, UnauthorizedAccessException, AdminOrSuperAdminNotFoundException {
        isAdminOrSuperAdmin(userDetails);
        return userService.createAll(userDetails.getUsername(), createUserRequestList.getUsers(), httpServletRequest);
    }
}
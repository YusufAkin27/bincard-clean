package akin.city_card.user.controller;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.admin.core.response.AuditLogDTO;
import akin.city_card.bus.exceptions.UnauthorizedAccessException;
import akin.city_card.buscard.core.request.FavoriteCardRequest;
import akin.city_card.buscard.core.response.FavoriteBusCardDTO;
import akin.city_card.buscard.exceptions.BusCardNotFoundException;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.notification.core.request.NotificationPreferencesDTO;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.exception.*;
import akin.city_card.user.core.request.*;
import akin.city_card.user.core.response.CacheUserDTO;
import akin.city_card.user.core.response.SearchHistoryDTO;
import akin.city_card.user.core.response.Views;
import akin.city_card.user.exceptions.*;
import akin.city_card.user.service.abstracts.UserService;
import akin.city_card.verification.exceptions.*;
import akin.city_card.wallet.exceptions.AdminOrSuperAdminNotFoundException;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.api.Http;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sign-up")
    public ResponseMessage signUp(@Valid @RequestBody CreateUserRequest createUserRequest, HttpServletRequest request) throws PhoneNumberRequiredException, PhoneNumberAlreadyExistsException, InvalidPhoneNumberFormatException, VerificationCodeStillValidException {
        return userService.create(createUserRequest, request);
    }

    @PostMapping("/verify/phone")
    public ResponseMessage verifyPhone(@Valid @RequestBody VerificationCodeRequest verificationCodeRequest, HttpServletRequest request) throws UserNotFoundException, CancelledVerificationCodeException, VerificationCodeNotFoundException, UsedVerificationCodeException, VerificationCodeExpiredException {
        return userService.verifyPhone(verificationCodeRequest, request);
    }

    // 📲 Adım 1: Şifremi unuttum -> Telefon numarasına kod gönder
    @PostMapping("/password/forgot")
    public ResponseMessage sendResetCode(@RequestParam("phone") String phone,HttpServletRequest httpServletRequest) throws UserNotFoundException {
        return userService.sendPasswordResetCode(phone,httpServletRequest);
    }

    @PostMapping("/password/verify-code")
    public ResponseMessage verifyResetCode(@Valid @RequestBody VerificationCodeRequest verificationCodeRequest)
            throws  VerificationCodeExpiredException, InvalidOrUsedVerificationCodeException {
        return userService.verifyPhoneForPasswordReset(verificationCodeRequest);
    }

    @PostMapping("/password/reset")
    public ResponseMessage resetPassword(@RequestBody PasswordResetRequest request, HttpServletRequest httpServletRequest) throws SamePasswordException, PasswordTooShortException, PasswordResetTokenNotFoundException, PasswordResetTokenExpiredException, PasswordResetTokenIsUsedException {
        return userService.resetPassword(request,httpServletRequest);
    }

    @PutMapping("/password/change")
    public ResponseMessage changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestBody ChangePasswordRequest request,
                                          HttpServletRequest httpServletRequest) throws UserNotFoundException, PasswordsDoNotMatchException, IncorrectCurrentPasswordException, UserNotActiveException, InvalidNewPasswordException, SamePasswordException, UserIsDeletedException {
        return userService.changePassword(userDetails.getUsername(), request,httpServletRequest);
    }

    // Telefon için yeniden doğrulama kodu gönderme
    @PostMapping("/verify/phone/resend")
    public ResponseMessage resendPhoneVerification(@RequestBody ResendPhoneVerificationRequest request, HttpServletRequest httpServletRequest) throws UserNotFoundException {
        return userService.resendPhoneVerificationCode(request);
    }

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

    @PostMapping("/collective-sign-up")
    public List<ResponseMessage> collectiveSignUp(@AuthenticationPrincipal UserDetails userDetails,
                                                  @Valid @RequestBody CreateUserRequestList createUserRequestList,
                                                  HttpServletRequest httpServletRequest) throws PhoneNumberRequiredException, InvalidPhoneNumberFormatException, PhoneNumberAlreadyExistsException, VerificationCodeStillValidException, UnauthorizedAccessException, AdminOrSuperAdminNotFoundException {
        isAdminOrSuperAdmin(userDetails);
        return userService.createAll(userDetails.getUsername(),createUserRequestList.getUsers(), httpServletRequest);
    }

    // 2. Profil görüntüleme
    @GetMapping("/profile")
    @JsonView(Views.User.class)
    public CacheUserDTO getProfile(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest httpServletRequest) throws UserNotFoundException {
        return userService.getProfile(userDetails.getUsername(), httpServletRequest);
    }

    // 3. Profil güncelleme
    @PutMapping("/profile")
    public ResponseMessage updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody UpdateProfileRequest updateProfileRequest,
                                         HttpServletRequest httpServletRequest) throws UserNotFoundException, EmailAlreadyExistsException {
        return userService.updateProfile(userDetails.getUsername(), updateProfileRequest, httpServletRequest);
    }

    @PostMapping("/email-verify/{token}")
    public ResponseMessage verifyEmail(
            @PathVariable("token") String token,
            @RequestParam("email") String email,
            HttpServletRequest request
    ) throws UserNotFoundException, VerificationCodeStillValidException, VerificationCodeNotFoundException, VerificationCodeExpiredException, VerificationCodeAlreadyUsedException, EmailMismatchException, VerificationCodeTypeMismatchException, VerificationCodeCancelledException, InvalidVerificationCodeException {
        return userService.verifyEmail(token, email,request);
    }


    //profil fotoğrafı yükleme
    @PutMapping("/profile/photo")
    public ResponseMessage uploadProfilePhoto(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestParam("photo") MultipartFile file,
                                              HttpServletRequest httpServletRequest) throws UserNotFoundException, PhotoSizeLargerException, IOException {
        return userService.updateProfilePhoto(userDetails.getUsername(), file,httpServletRequest);
    }

    @DeleteMapping("/profile/photo")
    public ResponseMessage deleteProfilePhoto(@AuthenticationPrincipal UserDetails userDetails) throws UserNotFoundException, PhotoSizeLargerException, IOException {
        return userService.deleteProfilePhoto(userDetails.getUsername());
    }

    // Hesap silme işlemi (kalıcı silme)
    @DeleteMapping("/delete-account")
    public ResponseMessage deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest
    ) throws UserNotFoundException, IncorrectPasswordException, UserNotActiveException, PasswordsDoNotMatchException, ApproveIsConfirmDeletionException, WalletBalanceNotZeroException {
        return userService.deleteAccount(userDetails.getUsername(), request, httpRequest);
    }


    @PostMapping("/freeze-account")
    public ResponseMessage freezeAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody FreezeAccountRequest request,
            HttpServletRequest httpRequest
    ) throws UserNotFoundException {
        return userService.freezeAccount(userDetails.getUsername(), request, httpRequest);
    }

    @PostMapping("/unfreeze-account")
    public ResponseMessage unfreezeAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UnfreezeAccountRequest request,
            HttpServletRequest httpRequest
    ) throws UserNotFoundException, AccountNotFrozenException {
        return userService.unfreezeAccount(userDetails.getUsername(), request, httpRequest);
    }


    /**
     * İstemci IP adresini alma metodu
     */

    /*
        // Giriş geçmişini görüntüleme
        @GetMapping("/login-history")
        public Page<LoginHistoryDTO> getLoginHistory(
                @AuthenticationPrincipal UserDetails userDetails,
                @PageableDefault(size = 20, sort = "loginAt", direction = Sort.Direction.DESC) Pageable pageable
        ) throws UserNotFoundException {
            return userService.getLoginHistory(userDetails.getUsername(), pageable);
        }

        // Dil ayarları
        @PutMapping("/preferences/language")
        public ResponseMessage updateLanguage(
                @AuthenticationPrincipal UserDetails userDetails,
                @RequestBody UpdateLanguageRequest request
        ) throws UserNotFoundException, UnsupportedLanguageException {
            return userService.updateLanguage(userDetails.getUsername(), request);
        }

        // Tema ayarları
        @PutMapping("/preferences/theme")
        public ResponseMessage updateTheme(
                @AuthenticationPrincipal UserDetails userDetails,
                @RequestBody UpdateThemeRequest request
        ) throws UserNotFoundException {
            return userService.updateTheme(userDetails.getUsername(), request);
        }

        // Gizlilik ayarları
        @PutMapping("/privacy-settings")
        public ResponseMessage updatePrivacySettings(
                @AuthenticationPrincipal UserDetails userDetails,
                @RequestBody PrivacySettingsRequest request
        ) throws UserNotFoundException {
            return userService.updatePrivacySettings(userDetails.getUsername(), request);
        }

        // Kullanıcı istatistikleri
        @GetMapping("/statistics")
        public UserStatisticsDTO getUserStatistics(
                @AuthenticationPrincipal UserDetails userDetails,
                @RequestParam(required = false) String period // DAILY, WEEKLY, MONTHLY, YEARLY
        ) throws UserNotFoundException {
            return userService.getUserStatistics(userDetails.getUsername(), period);
        }

        @GetMapping("/admin/{userId}")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public AdminUserDetailDTO getUserById(
                @PathVariable Long userId,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException {
            return userService.getUserByIdForAdmin(adminDetails.getUsername(), userId);
        }

        // Kullanıcı durumunu güncelleme
        @PutMapping("/admin/{userId}/status")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage updateUserStatus(
                @PathVariable Long userId,
                @RequestBody UpdateUserStatusRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException, InvalidStatusTransitionException {
            return userService.updateUserStatus(adminDetails.getUsername(), userId, request);
        }

        // Kullanıcıyı askıya alma
        @PostMapping("/admin/{userId}/suspend")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage suspendUser(
                @PathVariable Long userId,
                @RequestBody SuspendUserRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException, UserAlreadySuspendedException {
            return userService.suspendUser(adminDetails.getUsername(), userId, request);
        }

        // Kullanıcı askıya alma işlemini kaldırma
        @PostMapping("/admin/{userId}/unsuspend")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage unsuspendUser(
                @PathVariable Long userId,
                @RequestBody UnsuspendUserRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException, UserNotSuspendedException {
            return userService.unsuspendUser(adminDetails.getUsername(), userId, request);
        }

        // Kullanıcıyı yasaklama
        @PostMapping("/admin/{userId}/ban")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage banUser(
                @PathVariable Long userId,
                @RequestBody BanUserRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException, UserAlreadyBannedException {
            return userService.banUser(adminDetails.getUsername(), userId, request);
        }

        // Kullanıcı yasağını kaldırma
        @PostMapping("/admin/{userId}/unban")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage unbanUser(
                @PathVariable Long userId,
                @RequestBody UnbanUserRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException, UserNotBannedException {
            return userService.unbanUser(adminDetails.getUsername(), userId, request);
        }

        // Kullanıcı şifresini sıfırlama (Admin)
        @PostMapping("/admin/{userId}/reset-password")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage adminResetPassword(
                @PathVariable Long userId,
                @RequestBody AdminPasswordResetRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException {
            return userService.adminResetPassword(adminDetails.getUsername(), userId, request);
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
        // ===== KULLANICI AKTİVİTE TAKİBİ =====

        // Kullanıcı aktivite raporunu görüntüleme
        @GetMapping("/admin/{userId}/activity-report")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public UserActivityReportDTO getUserActivityReport(
                @PathVariable Long userId,
                @RequestParam(required = false) String startDate,
                @RequestParam(required = false) String endDate,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException {
            return userService.getUserActivityReport(adminDetails.getUsername(), userId, startDate, endDate);
        }

        // Şüpheli aktiviteleri listeleme
        @GetMapping("/admin/suspicious-activities")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public Page<SuspiciousActivityDTO> getSuspiciousActivities(
                @AuthenticationPrincipal UserDetails adminDetails,
                @RequestParam(required = false) String activityType,
                @RequestParam(required = false) String severity,
                @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC) Pageable pageable
        ) throws UnauthorizedAreaException {
            return userService.getSuspiciousActivities(adminDetails.getUsername(), activityType, severity, pageable);
        }

        // Şüpheli aktiviteyi işaretleme/çözme
        @PostMapping("/admin/suspicious-activities/{activityId}/resolve")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage resolveSuspiciousActivity(
                @PathVariable Long activityId,
                @RequestBody ResolveSuspiciousActivityRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws SuspiciousActivityNotFoundException, UnauthorizedAreaException {
            return userService.resolveSuspiciousActivity(adminDetails.getUsername(), activityId, request);
        }

    // ===== KULLANICI İSTATİSTİKLERİ =====

        // Genel kullanıcı istatistikleri
        @GetMapping("/admin/statistics/overview")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public AdminUserStatisticsDTO getUserStatisticsOverview(
                @AuthenticationPrincipal UserDetails adminDetails,
                @RequestParam(required = false) String period
        ) throws UnauthorizedAreaException {
            return userService.getUserStatisticsOverview(adminDetails.getUsername(), period);
        }

        // Kullanıcı büyüme analizi
        @GetMapping("/admin/statistics/growth")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public UserGrowthAnalysisDTO getUserGrowthAnalysis(
                @AuthenticationPrincipal UserDetails adminDetails,
                @RequestParam(required = false) String period,
                @RequestParam(required = false) String groupBy
        ) throws UnauthorizedAreaException {
            return userService.getUserGrowthAnalysis(adminDetails.getUsername(), period, groupBy);
        }

        // En aktif kullanıcılar
        @GetMapping("/admin/statistics/most-active")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public List<MostActiveUserDTO> getMostActiveUsers(
                @AuthenticationPrincipal UserDetails adminDetails,
                @RequestParam(defaultValue = "50") int limit,
                @RequestParam(required = false) String period
        ) throws UnauthorizedAreaException {
            return userService.getMostActiveUsers(adminDetails.getUsername(), limit, period);
        }

        @GetMapping("/admin/bulk-export")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseEntity<Resource> bulkExportUsers(
                @RequestParam(required = false) List<Long> userIds,
                @RequestParam(defaultValue = "CSV") String format, // CSV, EXCEL, JSON
                @RequestParam(required = false) List<String> fields,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UnauthorizedAreaException {
            return userService.bulkExportUsers(adminDetails.getUsername(), userIds, format, fields);
        }
    // ===== BİLDİRİM YÖNETİMİ =====



        // Toplu bildirim gönderme
        @PostMapping("/admin/notifications/broadcast")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage sendBroadcastNotification(
                @RequestBody BroadcastNotificationRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UnauthorizedAreaException {
            return userService.sendBroadcastNotification(adminDetails.getUsername(), request);
        }

        // Hedefli bildirim gönderme
        @PostMapping("/admin/notifications/targeted")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseMessage sendTargetedNotification(
                @RequestBody TargetedNotificationRequest request,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UnauthorizedAreaException {
            return userService.sendTargetedNotification(adminDetails.getUsername(), request);
        }

        // Bildirim istatistikleri
        @GetMapping("/admin/notifications/statistics")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public NotificationStatisticsDTO getNotificationStatistics(
                @AuthenticationPrincipal UserDetails adminDetails,
                @RequestParam(required = false) String period
        ) throws UnauthorizedAreaException {
            return userService.getNotificationStatistics(adminDetails.getUsername(), period);
        }
    // ===== GELİŞMİŞ ARAMA VE FİLTRELEME =====

        // Gelişmiş kullanıcı arama
        @PostMapping("/admin/search/advanced")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public Page<CacheUserDTO> advancedUserSearch(
                @RequestBody AdvancedUserSearchRequest request,
                @AuthenticationPrincipal UserDetails adminDetails,
                @PageableDefault(size = 20) Pageable pageable
        ) throws UnauthorizedAreaException {
            return userService.advancedUserSearch(adminDetails.getUsername(), request, pageable);
        }

        // Kullanıcı davranış analizi
        @GetMapping("/admin/{userId}/behavior-analysis")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public UserBehaviorAnalysisDTO getUserBehaviorAnalysis(
                @PathVariable Long userId,
                @RequestParam(required = false) String period,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException {
            return userService.getUserBehaviorAnalysis(adminDetails.getUsername(), userId, period);
        }

        // Risk skoru hesaplama
        @GetMapping("/admin/{userId}/risk-score")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public UserRiskScoreDTO getUserRiskScore(
                @PathVariable Long userId,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException {
            return userService.getUserRiskScore(adminDetails.getUsername(), userId);
        }

    // ===== SİSTEM SAĞLIĞI VE MONİTÖRİNG =====

        // Kullanıcı oturum durumları
        @GetMapping("/admin/sessions/overview")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public SessionOverviewDTO getSessionOverview(
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UnauthorizedAreaException {
            return userService.getSessionOverview(adminDetails.getUsername());
        }

        // Aktif kullanıcı istatistikleri
        @GetMapping("/admin/statistics/active-users")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ActiveUserStatisticsDTO getActiveUserStatistics(
                @AuthenticationPrincipal UserDetails adminDetails,
                @RequestParam(required = false) String timeframe // REALTIME, HOURLY, DAILY, WEEKLY
        ) throws UnauthorizedAreaException {
            return userService.getActiveUserStatistics(adminDetails.getUsername(), timeframe);
        }

    // ===== DATA PRIVACY VE GDPR =====

        // Kullanıcı verilerini export etme (GDPR)
        @GetMapping("/admin/{userId}/export-data")
        @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
        public ResponseEntity<Resource> exportUserDataForAdmin(
                @PathVariable Long userId,
                @RequestParam(defaultValue = "JSON") String format,
                @AuthenticationPrincipal UserDetails adminDetails
        ) throws UserNotFoundException, UnauthorizedAreaException {
            return userService.exportUserDataForAdmin(adminDetails.getUsername(), userId, format);
        }



    */
    @PatchMapping("/update-fcm-token")
    public boolean updateFCMToken(@RequestParam String fcmToken, @AuthenticationPrincipal UserDetails userDetails) throws UserNotFoundException {
        return userService.updateFCMToken(fcmToken, userDetails.getUsername());
    }

    // Tüm kullanıcıları sayfalı listeleme
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch)
            throws UserNotActiveException, UnauthorizedAreaException {

        Page<CacheUserDTO> userPage = userService.getAllUsers(userDetails.getUsername(), page, size);

        String etagValue = Integer.toHexString(userPage.getContent().hashCode());
        String etag = "\"" + etagValue + "\"";

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS)) // İsteğe göre ayarlanabilir
                .eTag(etag)
                .body(userPage);
    }

    // Tek sorgu ile kullanıcı arama (sayfalı)
    @GetMapping("/admin/search")
    public Page<CacheUserDTO> searchUser(@RequestParam String query,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size)
            throws UserNotFoundException, UnauthorizedAreaException, UserNotActiveException, UnauthorizedAreaException {
        return userService.searchUser(userDetails.getUsername(), query, page, size);
    }

    // FAVORİ KARTLAR
    @GetMapping("/favorites/cards")
    public List<FavoriteBusCardDTO> getFavoriteCards(@AuthenticationPrincipal UserDetails userDetails) throws UserNotFoundException {
        return userService.getFavoriteCards(userDetails.getUsername());
    }

    @PostMapping("/favorites/cards")
    public ResponseMessage addFavoriteCard(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestBody FavoriteCardRequest request) throws UserNotFoundException {
        return userService.addFavoriteCard(userDetails.getUsername(), request);
    }

    @DeleteMapping("/favorites/cards/{cardId}")
    public ResponseMessage removeFavoriteCard(@AuthenticationPrincipal UserDetails userDetails,
                                              @PathVariable Long cardId) throws UserNotFoundException {
        return userService.removeFavoriteCard(userDetails.getUsername(), cardId);
    }

    @PostMapping("/location")
    public void updateLocation(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestBody @Valid UpdateLocationRequest updateLocationRequest) throws UserNotFoundException {
        userService.updateLocation(userDetails.getUsername(), updateLocationRequest);
    }


    // BİLDİRİM TERCİHLERİ
    @PutMapping("/notification-preferences")
    public CacheUserDTO updateNotificationPreferences(@AuthenticationPrincipal UserDetails userDetails,
                                                      @RequestBody NotificationPreferencesDTO preferences) throws UserNotFoundException {
        return userService.updateNotificationPreferences(userDetails.getUsername(), preferences);
    }


    // DÜŞÜK BAKİYE UYARISI
    @PutMapping("/balance-alert")
    public ResponseMessage setLowBalanceAlert(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody LowBalanceAlertRequest request) throws UserNotFoundException, BusCardNotFoundException, AlreadyBusCardLowBalanceException {
        return userService.setLowBalanceThreshold(userDetails.getUsername(), request);
    }

    // ARAMA GEÇMİŞİ
    @GetMapping("/search-history")
    public List<SearchHistoryDTO> getSearchHistory(@AuthenticationPrincipal UserDetails userDetails) throws UserNotFoundException {
        return userService.getSearchHistory(userDetails.getUsername());
    }

    @DeleteMapping("/search-history")
    public ResponseMessage clearSearchHistory(@AuthenticationPrincipal UserDetails userDetails) throws UserNotFoundException {
        return userService.clearSearchHistory(userDetails.getUsername());
    }


    @GetMapping("/activity-log")
    public Page<AuditLogDTO> getUserActivityLog(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable
    ) throws UserNotFoundException {
        return userService.getUserActivityLog(userDetails.getUsername(), pageable);
    }


}



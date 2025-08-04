package akin.city_card.user.service.abstracts;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.admin.core.response.AuditLogDTO;
import akin.city_card.buscard.core.request.FavoriteCardRequest;
import akin.city_card.buscard.core.response.FavoriteBusCardDTO;
import akin.city_card.buscard.exceptions.BusCardNotFoundException;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.notification.core.request.NotificationPreferencesDTO;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.entity.Role;
import akin.city_card.security.exception.*;
import akin.city_card.user.core.request.*;
import akin.city_card.user.core.response.*;
import akin.city_card.user.exceptions.*;
import akin.city_card.user.model.LoginHistory;
import akin.city_card.user.model.SearchHistory;
import akin.city_card.user.model.UserStatus;
import akin.city_card.verification.exceptions.*;
import akin.city_card.wallet.exceptions.AdminOrSuperAdminNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserService {

    ResponseMessage create(CreateUserRequest createUserRequest, HttpServletRequest request) throws PhoneNumberRequiredException, PhoneNumberAlreadyExistsException, InvalidPhoneNumberFormatException, VerificationCodeStillValidException;

    CacheUserDTO getProfile(String username, HttpServletRequest httpServletRequest) throws UserNotFoundException;

    ResponseMessage updateProfile(String username, UpdateProfileRequest updateProfileRequest, HttpServletRequest httpServletRequest) throws UserNotFoundException, EmailAlreadyExistsException;



    List<ResponseMessage> createAll(String username, @Valid List<CreateUserRequest> createUserRequests, HttpServletRequest httpServletRequest) throws PhoneNumberRequiredException, InvalidPhoneNumberFormatException, PhoneNumberAlreadyExistsException, VerificationCodeStillValidException, AdminOrSuperAdminNotFoundException;

    ResponseMessage updateProfilePhoto(String username, MultipartFile file, HttpServletRequest httpServletRequest) throws PhotoSizeLargerException, IOException, UserNotFoundException;

    ResponseMessage verifyPhone(VerificationCodeRequest request, HttpServletRequest httpServletRequest) throws UserNotFoundException, VerificationCodeNotFoundException, UsedVerificationCodeException, CancelledVerificationCodeException, VerificationCodeExpiredException;


    ResponseMessage sendPasswordResetCode(String phone, HttpServletRequest httpServletRequest) throws UserNotFoundException;

    ResponseMessage resetPassword(PasswordResetRequest request, HttpServletRequest httpServletRequest) throws PasswordResetTokenNotFoundException, PasswordResetTokenExpiredException, PasswordResetTokenIsUsedException, PasswordTooShortException, SamePasswordException;

    ResponseMessage changePassword(String username, ChangePasswordRequest request, HttpServletRequest httpServletRequest) throws UserIsDeletedException, UserNotActiveException, UserNotFoundException, PasswordsDoNotMatchException, InvalidNewPasswordException, IncorrectCurrentPasswordException, SamePasswordException;


    ResponseMessage resendPhoneVerificationCode(ResendPhoneVerificationRequest request) throws UserNotFoundException;

    ResponseMessage verifyPhoneForPasswordReset(VerificationCodeRequest verificationCodeRequest) throws InvalidOrUsedVerificationCodeException, VerificationCodeExpiredException;

    boolean updateFCMToken(String fcmToken, String username) throws UserNotFoundException;



    List<FavoriteBusCardDTO> getFavoriteCards(String username) throws UserNotFoundException;

    ResponseMessage addFavoriteCard(String username, FavoriteCardRequest request) throws UserNotFoundException;

    ResponseMessage removeFavoriteCard(String username, Long cardId) throws UserNotFoundException;


    CacheUserDTO updateNotificationPreferences(String username, NotificationPreferencesDTO preferences) throws UserNotFoundException;


    ResponseMessage setLowBalanceThreshold(String username, LowBalanceAlertRequest request) throws UserNotFoundException, BusCardNotFoundException, AlreadyBusCardLowBalanceException;

    List<SearchHistoryDTO> getSearchHistory(String username) throws UserNotFoundException;

    ResponseMessage clearSearchHistory(String username) throws UserNotFoundException;


    Page<AuditLogDTO> getUserActivityLog(String username, Pageable pageable) throws UserNotFoundException;

    ResponseMessage deleteProfilePhoto(String username) throws UserNotFoundException;

    void updateLocation(String username, @Valid UpdateLocationRequest updateLocationRequest) throws UserNotFoundException;

    ResponseMessage verifyEmail(String token, String email, HttpServletRequest request) throws VerificationCodeNotFoundException, VerificationCodeExpiredException, VerificationCodeStillValidException, UserNotFoundException, VerificationCodeAlreadyUsedException, VerificationCodeCancelledException, VerificationCodeTypeMismatchException, EmailMismatchException, InvalidVerificationCodeException;

    ResponseMessage deleteAccount(String username, DeleteAccountRequest request, HttpServletRequest httpRequest) throws ApproveIsConfirmDeletionException, UserNotFoundException, PasswordsDoNotMatchException, WalletBalanceNotZeroException;

    ResponseMessage freezeAccount(String username, FreezeAccountRequest request, HttpServletRequest httpRequest) throws UserNotFoundException;



    PageDTO<CacheUserDTO> getAllUsers(Pageable pageable);

    PageDTO<CacheUserDTO> searchUsers(String name, Pageable pageable);

    ResponseMessage bulkUpdateUserStatus(List<Long> userIds, UserStatus newStatus, String username) throws AdminOrSuperAdminNotFoundException;

    ResponseMessage bulkDeleteUsers(List<Long> userIds, String username) throws AdminOrSuperAdminNotFoundException;

    CacheUserDTO getUserById(Long userId, String username) throws UserNotFoundException, AdminOrSuperAdminNotFoundException;

    Map<String, Object> getUserDeviceInfo(Long userId, String username) throws UserNotFoundException;

    ResponseMessage assignRolesToUser(Long userId, Set<Role> roles, String username) throws AdminOrSuperAdminNotFoundException;

    ResponseMessage removeRolesFromUser(Long userId, Set<Role> roles, String username);

    ResponseMessage bulkAssignRoles(List<Long> userIds, Set<Role> roles, String username);

    ResponseMessage resetUserPassword(Long userId, String newPassword, boolean forceChange, String username);


    ResponseMessage updateEmailVerificationStatus(Long userId, boolean verified, String username);

    ResponseMessage updatePhoneVerificationStatus(Long userId, boolean verified, String username);

    List<Map<String, Object>> getUserActiveSessions(Long userId);

    ResponseMessage terminateUserSession(Long userId, String sessionId, String username);


    ResponseMessage banIpAddress(String ipAddress, String reason, LocalDateTime expiresAt, String username);

    ResponseMessage suspendUser(String username, Long userId, SuspendUserRequest request);

    ResponseMessage permanentlyDeleteUser(String username, Long userId, PermanentDeleteRequest request);

    ResponseMessage unsuspendUser(String username, Long userId, UnsuspendUserRequest request);

    ResponseMessage banUserDevice(Long userId, String deviceId, String reason, String username);

    Page<Map<String, Object>> getSuspiciousActivities(String startDate, String endDate, String activityType, Pageable pageable);

    Page<Map<String, Object>> getUserAuditLogs(Long userId, String startDate, String endDate, String action, Pageable pageable);

    Map<String, Object> getUserStatistics();


    Page<LoginHistory> getUserLoginHistory(Long userId, String startDate, String endDate, Pageable pageable);

    Page<SearchHistory> getUserSearchHistory(Long userId, String startDate, String endDate, Pageable pageable);

    ResponseMessage sendNotificationToUser(Long userId, String title, String message, String type, String username);

    ResponseMessage sendBulkNotification(List<Long> userIds, String title, String message, String type, String username);

    ResponseMessage exportUserDataToPdf(Long userId, String emailAddress, String language, String username);

    void exportUsersToExcel(List<Long> userIds, UserStatus status, Role role, HttpServletResponse response, String username);


    Map<String, Object> getUserBehaviorAnalysis(Long userId, int days);
}

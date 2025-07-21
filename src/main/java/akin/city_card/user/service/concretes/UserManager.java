package akin.city_card.user.service.concretes;

import akin.city_card.admin.core.converter.AuditLogConverter;
import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.admin.core.response.AuditLogDTO;
import akin.city_card.admin.model.AuditLog;
import akin.city_card.admin.repository.AuditLogRepository;
import akin.city_card.bus.exceptions.RouteNotFoundException;
import akin.city_card.buscard.core.converter.BusCardConverter;
import akin.city_card.buscard.core.request.FavoriteCardRequest;
import akin.city_card.buscard.core.response.FavoriteBusCardDTO;
import akin.city_card.buscard.exceptions.BusCardNotFoundException;
import akin.city_card.buscard.model.BusCard;
import akin.city_card.buscard.model.UserFavoriteCard;
import akin.city_card.buscard.repository.BusCardRepository;
import akin.city_card.cloudinary.MediaUploadService;
import akin.city_card.location.model.Location;
import akin.city_card.mail.EmailMessage;
import akin.city_card.mail.MailService;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.notification.core.request.NotificationPreferencesDTO;
import akin.city_card.notification.model.NotificationPreferences;
import akin.city_card.notification.model.NotificationType;
import akin.city_card.notification.service.FCMService;
import akin.city_card.redis.CachedUserLookupService;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.exceptions.RouteNotFoundStationException;
import akin.city_card.route.model.Route;
import akin.city_card.route.repository.RouteRepository;
import akin.city_card.security.entity.ProfileInfo;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.security.exception.InvalidVerificationCodeException;
import akin.city_card.security.exception.UserNotActiveException;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.security.exception.VerificationCodeStillValidException;
import akin.city_card.security.repository.SecurityUserRepository;
import akin.city_card.security.repository.TokenRepository;
import akin.city_card.sms.SmsService;
import akin.city_card.station.exceptions.StationNotFoundException;
import akin.city_card.station.model.Station;
import akin.city_card.station.repository.StationRepository;
import akin.city_card.user.core.converter.AutoTopUpConverter;
import akin.city_card.user.core.converter.UserConverter;
import akin.city_card.user.core.request.*;
import akin.city_card.user.core.response.*;
import akin.city_card.user.exceptions.*;
import akin.city_card.user.model.*;
import akin.city_card.user.repository.AutoTopUpConfigRepository;
import akin.city_card.user.repository.PasswordResetTokenRepository;
import akin.city_card.user.repository.UserRepository;
import akin.city_card.user.service.abstracts.UserService;
import akin.city_card.verification.exceptions.*;
import akin.city_card.verification.model.VerificationChannel;
import akin.city_card.verification.model.VerificationCode;
import akin.city_card.verification.model.VerificationPurpose;
import akin.city_card.verification.repository.VerificationCodeRepository;
import akin.city_card.wallet.core.converter.WalletConverter;
import akin.city_card.wallet.core.response.WalletDTO;
import akin.city_card.wallet.exceptions.WalletIsEmptyException;
import akin.city_card.wallet.model.Wallet;
import akin.city_card.wallet.repository.WalletRepository;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserManager implements UserService {
    private final UserRepository userRepository;
    private final UserConverter userConverter;
    private final SmsService smsService;
    private final MailService mailService;
    private final MediaUploadService mediaUploadService;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecurityUserRepository securityUserRepository;
    private final BusCardConverter busCardConverter;
    private final BusCardRepository busCardRepository;
    private final WalletRepository walletRepository;
    private final WalletConverter walletConverter;
    private final AutoTopUpConfigRepository autoTopUpConfigRepository;
    private final AutoTopUpConverter autoTopUpConverter;
    private AuditLogRepository auditLogRepository;
    private final CachedUserLookupService cachedUserLookupService;
    private final RouteRepository routeRepository;
    private final StationRepository stationRepository;
    private final AuditLogConverter auditLogConverter;
    private final FCMService fcmService;
    private final TokenRepository tokenRepository;



    @Override
    @Transactional
    public ResponseMessage create(CreateUserRequest request) throws VerificationCodeStillValidException {
        String normalizedPhone = PhoneNumberFormatter.normalizeTurkishPhoneNumber(request.getTelephone());
        request.setTelephone(normalizedPhone);

        Optional<SecurityUser> existingUserOpt = securityUserRepository.findByUserNumber(normalizedPhone);

        if (existingUserOpt.isPresent() && !existingUserOpt.get().isEnabled()) {
            SecurityUser existingUser = existingUserOpt.get();

            VerificationCode lastCode = verificationCodeRepository.findAll().stream()
                    .filter(vc -> vc.getUser().getId().equals(existingUser.getId()) &&
                            vc.getPurpose() == VerificationPurpose.REGISTER)
                    .max(Comparator.comparing(VerificationCode::getCreatedAt))
                    .orElse(null);

            if (lastCode != null && !lastCode.isUsed() && !lastCode.isCancelled()
                    && lastCode.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new VerificationCodeStillValidException();
            }

            verificationCodeRepository.cancelAllActiveCodes(existingUser.getId(), VerificationPurpose.REGISTER);
            sendVerificationCode(existingUser, request.getIpAddress(), request.getUserAgent(), VerificationPurpose.REGISTER);

            return new ResponseMessage("Telefon numarası daha önce kayıt olmuş ancak aktif edilmemiş. Yeni doğrulama kodu gönderildi.", true);
        }

        User user = userConverter.convertUserToCreateUser(request);
        user.setStatus(UserStatus.UNVERIFIED);
        userRepository.save(user);

        sendVerificationCode(user, request.getIpAddress(), request.getUserAgent(), VerificationPurpose.REGISTER);

        return new ResponseMessage("Kullanıcı başarıyla oluşturuldu. Doğrulama kodu SMS olarak gönderildi.", true);
    }


    private void sendVerificationCode(SecurityUser user, String ipAddress, String userAgent, VerificationPurpose purpose) {
        String code = randomSixDigit();
        LocalDateTime now = LocalDateTime.now();

        VerificationCode verificationCode = VerificationCode.builder()
                .code(code)
                .user(user)
                .createdAt(now)
                .expiresAt(now.plusMinutes(3))
                .channel(VerificationChannel.SMS)
                .used(false)
                .cancelled(false)
                .purpose(purpose)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        verificationCodeRepository.save(verificationCode);
/*
         SmsRequest smsRequest = new SmsRequest();
         smsRequest.setTo(user.getUserNumber());
         smsRequest.setMessage("City Card - Doğrulama kodunuz: " + code + ". Kod 3 dakika geçerlidir.");
         smsService.sendSms(smsRequest);


 */
        System.out.println("📩 Yeni kayıt doğrulama kodu: " + code);
    }


    @Override
    @Transactional
    public ResponseMessage verifyPhone(VerificationCodeRequest request) throws UserNotFoundException {
        VerificationCode verificationCode = verificationCodeRepository
                .findTopByCodeOrderByCreatedAtDesc(request.getCode());

        if (verificationCode == null) {
            return new ResponseMessage("Böyle bir doğrulama kodu bulunamadı.", false);
        }

        if (verificationCode.isUsed()) {
            return new ResponseMessage("Bu doğrulama kodu zaten kullanılmış.", false);
        }

        if (verificationCode.isCancelled()) {
            return new ResponseMessage("Bu doğrulama kodu iptal edilmiş.", false);
        }

        if (verificationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            verificationCode.setCancelled(true);
            verificationCodeRepository.save(verificationCode);
            return new ResponseMessage("Doğrulama kodunun süresi dolmuş.", false);
        }

        SecurityUser securityUser = verificationCode.getUser();
        if (!(securityUser instanceof User user)) {
            throw new UserNotFoundException();
        }

        user.setPhoneVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        verificationCode.setUsed(true);
        verificationCode.setVerifiedAt(LocalDateTime.now());
        verificationCodeRepository.save(verificationCode);

        verificationCodeRepository.cancelAllActiveCodes(user.getId(), VerificationPurpose.REGISTER);

        fcmService.sendNotificationToToken(
                user,
                "Hoşgeldiniz!",
                "Telefon numaranız başarıyla doğrulandı ve hesabınız aktif edildi.",
                NotificationType.SUCCESS,
                null
        );

        return new ResponseMessage("Telefon numarası başarıyla doğrulandı. Hesabınız aktif hale getirildi.", true);
    }

    @Override
    @JsonView(Views.User.class)
    public CacheUserDTO getProfile(String username) throws UserNotFoundException {
        return userConverter.toCacheUserDTO(userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new));
    }


    @Override
    @Transactional
    public ResponseMessage updateProfile(String username, UpdateProfileRequest updateProfileRequest) throws UserNotFoundException, EmailAlreadyExistsException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        boolean isUpdated = false;

        if (updateProfileRequest.getName() != null && !updateProfileRequest.getName().isEmpty() &&
                !updateProfileRequest.getName().equals(user.getProfileInfo().getName())) {
            user.getProfileInfo().setName(updateProfileRequest.getName());
            isUpdated = true;
        }

        if (updateProfileRequest.getSurname() != null && !updateProfileRequest.getSurname().isEmpty() &&
                !updateProfileRequest.getSurname().equals(user.getProfileInfo().getSurname())) {
            user.getProfileInfo().setSurname(updateProfileRequest.getSurname());
            isUpdated = true;
        }
        if (updateProfileRequest.getEmail() != null && !updateProfileRequest.getEmail().isBlank()) {
            String newEmail = updateProfileRequest.getEmail().trim().toLowerCase();


            boolean emailAlreadyInUse = securityUserRepository.existsByProfileInfoEmail(newEmail);
            if (emailAlreadyInUse) {
                throw new EmailAlreadyExistsException();
            }

            if (user.getProfileInfo() == null) {
                user.setProfileInfo(new ProfileInfo());
            }

            String currentEmail = user.getProfileInfo().getEmail();

            if (!newEmail.equalsIgnoreCase(currentEmail)) {
                user.getProfileInfo().setEmail(newEmail);
                user.setEmailVerified(false);

                // Mevcut aktif email doğrulama kodlarını iptal et
                verificationCodeRepository.cancelAllActiveCodes(user.getId(), VerificationPurpose.EMAIL_VERIFICATION);

                // Yeni doğrulama kodu oluştur
                String token = UUID.randomUUID().toString();
                VerificationCode verificationCode = new VerificationCode();
                verificationCode.setCode(token);
                verificationCode.setCreatedAt(LocalDateTime.now());
                verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(15));
                verificationCode.setUsed(false);
                verificationCode.setCancelled(false);
                verificationCode.setUser(user);
                verificationCode.setPurpose(VerificationPurpose.EMAIL_VERIFICATION);
                verificationCode.setChannel(VerificationChannel.EMAIL);
                verificationCodeRepository.save(verificationCode);

                // Doğrulama linki
                String verificationLink = "http://localhost:8080/v1/api/user/email-verify/" + token
                        + "?email=" + URLEncoder.encode(newEmail, StandardCharsets.UTF_8);
                System.out.println(verificationLink);
                // HTML içerikli e-posta
                String fullName = (user.getProfileInfo().getName() != null ? user.getProfileInfo().getName() : "") + " "
                        + (user.getProfileInfo().getSurname() != null ? user.getProfileInfo().getSurname() : "");
                String htmlContent = """
                        <html>
                          <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 30px;">
                            <div style="max-width: 600px; margin: auto; background-color: #ffffff; padding: 20px; border-radius: 8px;">
                              <h2 style="color: #333;">E-posta Adresinizi Doğrulayın</h2>
                              <p>Merhaba <strong>%s</strong>,</p>
                              <p>Yeni e-posta adresinizi doğrulamak için aşağıdaki butona tıklayın:</p>
                              <div style="text-align: center; margin: 30px 0;">
                                <a href="%s" style="background-color: #4CAF50; color: white; padding: 14px 25px; text-decoration: none; border-radius: 5px;">E-Postamı Doğrula</a>
                              </div>
                              <p>Bu bağlantı <strong>15 dakika</strong> boyunca geçerlidir.</p>
                              <hr style="border: none; border-top: 1px solid #eee;">
                              <p style="font-size: 12px; color: #888;">Bu mesajı siz istemediyseniz lütfen dikkate almayın.</p>
                            </div>
                          </body>
                        </html>
                        """.formatted(fullName.trim(), verificationLink);

                // E-posta gönder
                EmailMessage emailMessage = new EmailMessage();
                emailMessage.setToEmail(newEmail);
                emailMessage.setSubject("E-Posta Doğrulama");
                emailMessage.setBody(htmlContent);
                emailMessage.setHtml(true);
                mailService.queueEmail(emailMessage);

                System.out.println("Email verification link: " + verificationLink);

                isUpdated = true;
            }


        }


        if (isUpdated) {
            userRepository.save(user);

            // Bildirimi kaydet ve anlık gönder
            fcmService.sendNotificationToToken(
                    user,
                    "Profil Güncelleme",
                    "Profil bilgileriniz başarıyla güncellendi.",
                    NotificationType.SUCCESS,
                    null
            );
            return new ResponseMessage("Profil başarıyla güncellendi.", true);
        }

        return new ResponseMessage("Herhangi bir değişiklik yapılmadı.", false);
    }


    @Override
    @Transactional
    public ResponseMessage deactivateUser(String username) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        user.setStatus(UserStatus.INACTIVE);
        user.setDeleted(true);
        tokenRepository.deleteBySecurityUserId(user.getId());
        fcmService.sendNotificationToToken(
                user,
                "Pasif oldunuz !",
                "Hesabınız devre dışı bırakıldı. Tekrar giriş yapamazsınız.",
                NotificationType.WARNING,
                null
        );

        return new ResponseMessage("Kullanıcı hesabı silindi.", true);
    }

    @Override
    public List<ResponseMessage> createAll(List<CreateUserRequest> createUserRequests) throws PhoneNumberRequiredException, InvalidPhoneNumberFormatException, PhoneNumberAlreadyExistsException, VerificationCodeStillValidException {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        for (CreateUserRequest createUserRequest : createUserRequests) {
            responseMessages.add(create(createUserRequest));
        }

        return responseMessages;

    }

    @Override
    @Transactional
    public ResponseMessage updateProfilePhoto(String username, MultipartFile file)
            throws PhotoSizeLargerException, IOException, UserNotFoundException {

        User user = userRepository.findByUserNumber(username)
                .orElseThrow(UserNotFoundException::new);

        try {
            String imageUrl = mediaUploadService.uploadAndOptimizeMedia(file);
            user.getProfileInfo().setProfilePicture(imageUrl);

            userRepository.save(user);

            return new ResponseMessage("Profil fotoğrafı başarıyla güncellendi.", true);

        } catch (OnlyPhotosAndVideosException | VideoSizeLargerException | FileFormatCouldNotException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public ResponseMessage sendPasswordResetCode(String phone) throws UserNotFoundException {
        Optional<SecurityUser> user = securityUserRepository.findByUserNumber(PhoneNumberFormatter.normalizeTurkishPhoneNumber(phone));
        if (user.isEmpty()) {
            throw new UserNotFoundException();
        }
        String code = randomSixDigit();
        System.out.println("Doğrulama kodu: " + code);

        VerificationCode verificationCode = VerificationCode.builder()
                .user(user.get())
                .code(code)
                .channel(VerificationChannel.SMS)
                .purpose(VerificationPurpose.RESET_PASSWORD)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();


        verificationCodeRepository.save(verificationCode);

/*
        SmsRequest smsRequest = new SmsRequest();
        smsRequest.setTo(phone);
        smsRequest.setMessage("City Card - Doğrulama kodunuz: " + code +
                ". Kod 3 dakika boyunca geçerlidir.");
        smsService.sendSms(smsRequest);


 */

        return new ResponseMessage("Doğrulama kodu gönderildi.", true);
    }

    @Override
    @Transactional
    public ResponseMessage resetPassword(PasswordResetRequest request)
            throws PasswordResetTokenNotFoundException,
            PasswordResetTokenExpiredException,
            PasswordResetTokenIsUsedException, SamePasswordException {

        PasswordResetToken passwordResetToken = passwordResetTokenRepository
                .findByToken(request.getResetToken())
                .orElseThrow(PasswordResetTokenNotFoundException::new);

        if (passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordResetTokenExpiredException();
        }

        if (passwordResetToken.isUsed()) {
            throw new PasswordResetTokenIsUsedException();
        }

        SecurityUser user = passwordResetToken.getUser();
        String newPassword = request.getNewPassword();

        if (newPassword.length() < 6) {
            throw new SamePasswordException();
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new SamePasswordException();
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        securityUserRepository.save(user);

        passwordResetToken.setUsed(true);
        passwordResetTokenRepository.save(passwordResetToken);

        if (user instanceof User appUser) {
            String title = "Şifre Sıfırlama";
            String message = "Şifreniz başarıyla sıfırlandı.";
            NotificationType type = NotificationType.SUCCESS;

            fcmService.sendNotificationToToken(appUser, title, message, type, null);
        }

        return new ResponseMessage("Şifreniz başarıyla sıfırlandı.", true);
    }


    @Override
    @Transactional
    public ResponseMessage changePassword(String username, ChangePasswordRequest request)
            throws UserIsDeletedException, UserNotActiveException, UserNotFoundException, PasswordsDoNotMatchException, InvalidNewPasswordException, IncorrectCurrentPasswordException, SamePasswordException {

        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        if (!user.isEnabled()) {
            throw new UserNotActiveException();
        }

        if (user.isDeleted()) {
            throw new UserIsDeletedException();
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IncorrectCurrentPasswordException();
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new InvalidNewPasswordException();
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new SamePasswordException();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);


        String title = "Şifre Güncelleme";
        String message = "Şifreniz başarıyla güncellendi.";
        NotificationType type = NotificationType.SUCCESS;

        fcmService.sendNotificationToToken(user, title, message, type, null);

        return new ResponseMessage("Şifre başarıyla güncellendi.", true);
    }


    @Override
    public ResponseMessage resendPhoneVerificationCode(ResendPhoneVerificationRequest resendPhoneVerification) throws UserNotFoundException {
        String normalizedPhone = PhoneNumberFormatter.normalizeTurkishPhoneNumber(resendPhoneVerification.getTelephone());
        resendPhoneVerification.setTelephone(normalizedPhone);

        User user = userRepository.findByUserNumber(resendPhoneVerification.getTelephone()).orElseThrow(UserNotFoundException::new);

        String code = randomSixDigit();

/*
        SmsRequest smsRequest = new SmsRequest();
        smsRequest.setTo(resendPhoneVerification.getTelephone());
        smsRequest.setMessage("City Card - Doğrulama kodunuz: " + code +
                ". Kod 3 dakika boyunca geçerlidir.");
        smsService.sendSms(smsRequest);


 */

        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setCode(code);
        verificationCode.setCreatedAt(LocalDateTime.now());
        verificationCode.setUser(user);
        verificationCode.setChannel(VerificationChannel.SMS);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(3));
        verificationCode.setCancelled(false);
        verificationCode.setPurpose(VerificationPurpose.REGISTER);
        verificationCode.setUsed(false);
        verificationCode.setIpAddress(resendPhoneVerification.getIpAddress());
        verificationCode.setUserAgent(resendPhoneVerification.getUserAgent());

        if (user.getVerificationCodes() == null) {
            user.setVerificationCodes(new ArrayList<>());
        }
        user.getVerificationCodes().add(verificationCode);

        verificationCodeRepository.save(verificationCode);
        userRepository.save(user);

        return new ResponseMessage("Yeniden doğrulama kodu gönderildi.", true);
    }

    @Override
    @Transactional
    public ResponseMessage verifyPhoneForPasswordReset(VerificationCodeRequest verificationCodeRequest) throws InvalidOrUsedVerificationCodeException, VerificationCodeExpiredException {
        String code = verificationCodeRequest.getCode();

        VerificationCode verificationCode = verificationCodeRepository
                .findFirstByCodeAndUsedFalseAndCancelledFalseOrderByCreatedAtDesc(code)
                .orElseThrow(InvalidOrUsedVerificationCodeException::new);

        if (verificationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationCodeExpiredException();
        }

        SecurityUser user = verificationCode.getUser();

        verificationCode.setUsed(true);
        verificationCodeRepository.save(verificationCode);

        UUID resetTokenUUID = UUID.randomUUID();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(resetTokenUUID.toString());
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(5)); // 5 dakika geçerli
        passwordResetToken.setUsed(false);
        passwordResetToken.setUser(user);

        passwordResetTokenRepository.save(passwordResetToken);

        return new ResponseMessage(resetTokenUUID + "", true);
    }

    @Override
    @Transactional
    public boolean updateFCMToken(String fcmToken, String username) throws UserNotFoundException {
        Optional<SecurityUser> user = securityUserRepository.findByUserNumber(username);
        user.get().getDeviceInfo().setFcmToken(fcmToken);
        return true;
    }

    @Override
    @JsonView(Views.SuperAdmin.class)
    public Page<CacheUserDTO> getAllUsers(String username, int page, int size)
            throws UserNotActiveException, UnauthorizedAreaException {

        SecurityUser securityUser = securityUserRepository.findByUserNumber(username)
                .orElseThrow(UserNotActiveException::new);

        if (securityUser.getRoles() == null ||
                securityUser.getRoles().stream().noneMatch(role ->
                        role.name().equals("ADMIN") || role.name().equals("SUPERADMIN"))) {
            throw new UnauthorizedAreaException();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> userPage = userRepository.findAll(pageable);

        return userPage.map(userConverter::toCacheUserDTO);
    }


    @Override
    @JsonView(Views.SuperAdmin.class)
    public Page<CacheUserDTO> searchUser(String username, String query, int page, int size)
            throws UserNotActiveException, UnauthorizedAreaException, UserNotFoundException {

        SecurityUser securityUser = securityUserRepository.findByUserNumber(username)
                .orElseThrow(UserNotActiveException::new);

        if (securityUser.getRoles() == null ||
                securityUser.getRoles().stream().noneMatch(role ->
                        role.name().equals("ADMIN") || role.name().equals("SUPERADMIN"))) {
            throw new UnauthorizedAreaException();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> results = userRepository.searchByQuery(query.toLowerCase(), pageable);

        if (results.isEmpty()) {
            throw new UserNotFoundException();
        }

        return results.map(userConverter::toCacheUserDTO);
    }

    @Override
    public List<FavoriteBusCardDTO> getFavoriteCards(String username) {
        List<UserFavoriteCard> favoriteCards = userRepository.findFavoriteCardsByUserNumber(username);
        return favoriteCards.stream().map(busCardConverter::favoriteBusCardToDTO).toList();
    }


    @Override
    @Transactional
    public ResponseMessage addFavoriteCard(String username, FavoriteCardRequest request) throws UserNotFoundException {
        CacheUserDTO cacheUserDTO = cachedUserLookupService.findByUsername(username);
        if (cacheUserDTO == null) {
            throw new UserNotFoundException();
        }

        User user = userRepository.findById(cacheUserDTO.getId())
                .orElseThrow(UserNotFoundException::new);

        BusCard busCard = busCardRepository.findById(request.getBusCardId())
                .orElseThrow(() -> new RuntimeException("BusCard bulunamadı - ID: " + request.getBusCardId()));

        boolean alreadyFavorited = user.getFavoriteCards().stream()
                .anyMatch(fav -> fav.getBusCard().getId().equals(busCard.getId()));
        if (alreadyFavorited) {
            return new ResponseMessage("Bu kart zaten favorilerinizde.", false);
        }

        UserFavoriteCard favorite = new UserFavoriteCard();
        favorite.setUser(user);
        favorite.setBusCard(busCard);
        favorite.setNickname(request.getNickname());
        favorite.setCreated(LocalDateTime.now());

        user.getFavoriteCards().add(favorite);
        userRepository.save(user);

        return new ResponseMessage("Kart favorilere başarıyla eklendi.", true);
    }

    @Override
    public ResponseMessage removeFavoriteCard(String username, Long cardId) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        List<UserFavoriteCard> favoriteCards = user.getFavoriteCards();
        boolean isSuccess = favoriteCards.removeIf(fav ->
                fav.getBusCard() != null && fav.getBusCard().getId().equals(cardId)
        );

        if (isSuccess) {
            userRepository.save(user);
        }

        return new ResponseMessage("Favoriden silme işlemi " + (isSuccess ? "başarılı" : "başarısız"), isSuccess);
    }

    @Override
    public WalletDTO getWallet(String username) throws WalletIsEmptyException, UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        Wallet wallet = user.getWallet();
        if (wallet == null) {
            throw new WalletIsEmptyException();
        }
        return walletConverter.convertToDTO(wallet);
    }

    @Override
    @Transactional
    @JsonView(Views.User.class)
    public CacheUserDTO updateNotificationPreferences(String username, NotificationPreferencesDTO preferencesDto)
            throws UserNotFoundException {

        if (preferencesDto.getNotifyBeforeMinutes() != null && preferencesDto.getNotifyBeforeMinutes() < 0) {
            throw new IllegalArgumentException("Bildirim süresi negatif olamaz");
        }

        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        NotificationPreferences preferences = Optional.ofNullable(user.getNotificationPreferences())
                .orElseGet(NotificationPreferences::new);

        preferences.setPushEnabled(preferencesDto.isPushEnabled());
        preferences.setSmsEnabled(preferencesDto.isSmsEnabled());
        preferences.setEmailEnabled(preferencesDto.isEmailEnabled());
        preferences.setNotifyBeforeMinutes(preferencesDto.getNotifyBeforeMinutes());
        preferences.setFcmActive(preferencesDto.isFcmActive());

        user.setNotificationPreferences(preferences);
        userRepository.save(user);


        return userConverter.toCacheUserDTO(user);
    }

    @Override
    public List<AutoTopUpConfigDTO> getAutoTopUpConfigs(String username) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        List<AutoTopUpConfig> configs = user.getAutoTopUpConfigs();

        return configs.stream()
                .map(autoTopUpConverter::convertToDTO)
                .toList();
    }


/*
    @Override
    public List<AuditLogDTO> getUserActivityLog(String username, Pageable pageable) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username);


        Page<AuditLog> auditLogs = auditLogRepository.findByUser_UserNumberOrderByTimestampDesc(username, pageable);

        return auditLogs.stream()
                .map(autoTopUpConverter::convertToDTO) // Eğer farklı bir converter ise ona göre değiştir
                .toList();
    }



 */

    @Override
    @JsonView(Views.User.class)
    public CacheUserDTO exportUserData(String username) throws UserNotFoundException {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }

        CacheUserDTO cacheUserDTO = cachedUserLookupService.findByUsername(username);

        if (cacheUserDTO.getEmail() != null) {
            String emailBody = buildEmailBodyFromCacheDTO(cacheUserDTO);

            EmailMessage emailMessage = new EmailMessage();
            emailMessage.setToEmail(cacheUserDTO.getEmail());
            emailMessage.setSubject("Hesap Bilgileriniz");
            emailMessage.setBody(emailBody);
            emailMessage.setHtml(false);

            mailService.queueEmail(emailMessage);
        }

        return cacheUserDTO;
    }

    @Override
    @Transactional
    public ResponseMessage addAutoTopUpConfig(String username, AutoTopUpConfigRequest configRequest) throws UserNotFoundException, BusCardNotFoundException, WalletIsEmptyException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);

        Optional<BusCard> busCard = busCardRepository.findById(configRequest.getBusCard());
        if (busCard.isEmpty()) {
            throw new BusCardNotFoundException();
        }
        Wallet wallet = user.getWallet();
        if (wallet == null) {
            throw new WalletIsEmptyException();
        }
        AutoTopUpConfig autoTopUpConfig = new AutoTopUpConfig();
        autoTopUpConfig.setAmount(configRequest.getAmount());
        autoTopUpConfig.setBusCard(busCard.get());
        autoTopUpConfig.setThreshold(configRequest.getThreshold());
        autoTopUpConfig.setUser(user);
        autoTopUpConfig.setWallet(wallet);
        autoTopUpConfig.setLastTopUpAt(null);
        autoTopUpConfig.setCreatedAt(LocalDateTime.now());
        autoTopUpConfig.setActive(true);
        autoTopUpConfig.setAutoTopUpLogs(new ArrayList<>());
        autoTopUpConfigRepository.save(autoTopUpConfig);
        return new ResponseMessage("otomatik ödeme alındı", true);
    }

    @Override
    @Transactional
    public ResponseMessage deleteAutoTopUpConfig(String username, Long configId) throws AutoTopUpConfigNotFoundException, UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        AutoTopUpConfig autoTopUpConfig = user.getAutoTopUpConfigs().stream().filter(a -> a.getId().equals(configId)).findFirst().orElseThrow(AutoTopUpConfigNotFoundException::new);
        autoTopUpConfig.setActive(false);
        autoTopUpConfigRepository.save(autoTopUpConfig);
        return new ResponseMessage("otomatik ödeme kapatıldı", true);
    }

    @Override
    public ResponseMessage setLowBalanceThreshold(String username, LowBalanceAlertRequest request) throws UserNotFoundException, BusCardNotFoundException, AlreadyBusCardLowBalanceException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        Optional<BusCard> busCard = busCardRepository.findById(request.getBusCardId());
        if (busCard.isEmpty()) {
            throw new BusCardNotFoundException();
        }
        boolean isPresent = user.getLowBalanceAlerts().containsKey(busCard.get());
        if (isPresent) {
            throw new AlreadyBusCardLowBalanceException();
        }
        user.getLowBalanceAlerts().put(busCard.get(), request.getLowBalance());

        return new ResponseMessage("düşük bakiye uyarısı ayarlandı", true);
    }

    @Override
    @JsonView(Views.User.class)
    public List<SearchHistoryDTO> getSearchHistory(String username) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        return user.getSearchHistory().stream().filter(SearchHistory::isActive).map(userConverter::toSearchHistoryDTO).toList();
    }

    @Override
    public ResponseMessage clearSearchHistory(String username) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        for (SearchHistory searchHistory : user.getSearchHistory()) {
            searchHistory.setActive(false);
            searchHistory.setDeleted(true);
            searchHistory.setDeletedAt(LocalDateTime.now());
        }
        return new ResponseMessage("arama geçmişi silindi", true);
    }

    @Override
    @JsonView(Views.User.class)
    public List<GeoAlertDTO> getGeoAlerts(String username) throws UserNotFoundException {
        return userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new).getGeoAlerts().stream().filter(GeoAlert::isActive).map(userConverter::toGeoAlertDTO).toList();
    }

    @Override
    public ResponseMessage addGeoAlert(String username, GeoAlertRequest alertRequest) throws UserNotFoundException, RouteNotFoundException, StationNotFoundException, RouteNotFoundStationException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        Optional<Route> route = routeRepository.findById(alertRequest.getRouteId());
        if (route.isEmpty()) {
            throw new RouteNotFoundException();
        }
        Optional<Station> station = stationRepository.findById(alertRequest.getStationId());
        if (station.isEmpty()) {
            throw new StationNotFoundException();
        }
        if (!route.get().getStationNodes().isEmpty() && !route.get().getStationNodes().contains(station.get())){
            throw new RouteNotFoundStationException();
        }
        GeoAlert geoAlert = new GeoAlert();
        geoAlert.setUser(user);
        geoAlert.setAlertName(alertRequest.getAlertName());
        geoAlert.setActive(true);
        geoAlert.setRoute(route.get());
        geoAlert.setStation(station.get());
        geoAlert.setNotifyBeforeMinutes(alertRequest.getNotifyBeforeMinutes());
        geoAlert.setRadiusMeters(alertRequest.getRadiusMeters());
        geoAlert.setCreatedAt(LocalDateTime.now());
        geoAlert.setUpdatedAt(LocalDateTime.now());
        user.getGeoAlerts().add(geoAlert);
        return new ResponseMessage("araç konum uyarısı eklendi", true);
    }

    @Override
    public ResponseMessage deleteGeoAlert(String username, Long alertId) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        boolean isDeleted = user.getGeoAlerts().removeIf(g -> g.getId().equals(alertId));
        if (isDeleted) {
            return new ResponseMessage(alertId + "araç konum uyarısı silindi", true);
        }
        return new ResponseMessage("araç konum uyarısı bulunamadı", true);
    }

    @Override
    @JsonView(Views.User.class)
    public Page<AuditLogDTO> getUserActivityLog(String username, Pageable pageable) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        Page<AuditLog> auditLogsPage = auditLogRepository.findByUser(user, pageable);
        return auditLogsPage.map(auditLogConverter::mapToDto);
    }

    @Override
    @Transactional
    public ResponseMessage deleteProfilePhoto(String username) throws UserNotFoundException {
        User user = userRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        if (user.getProfileInfo() != null) {
            user.getProfileInfo().setProfilePicture("https://thumbs.dreamstime.com/z/default-profile-picture-icon-high-resolution-high-resolution-default-profile-picture-icon-symbolizing-no-display-picture-360167031.jpg");
        }
        return new ResponseMessage("Profil fotoğrafı silindi", true);

    }

    @Override
    @Transactional
    public void updateLocation(String username, UpdateLocationRequest updateLocationRequest) throws UserNotFoundException {
        SecurityUser securityUser = securityUserRepository.findByUserNumber(username).orElseThrow(UserNotFoundException::new);
        Location location = new Location();
        location.setUser(securityUser);
        location.setLatitude(updateLocationRequest.getLatitude());
        location.setLongitude(updateLocationRequest.getLongitude());
        location.setRecordedAt(LocalDateTime.now());
        securityUser.getLocationHistory().add(location);
        securityUser.setLastLocationUpdatedAt(LocalDateTime.now());
        securityUserRepository.save(securityUser);
    }

    @Override
    @Transactional
    public ResponseMessage verifyEmail(String token, String email)
            throws VerificationCodeExpiredException,
            VerificationCodeAlreadyUsedException, VerificationCodeCancelledException,
            VerificationCodeTypeMismatchException, UserNotFoundException,
            EmailMismatchException, InvalidVerificationCodeException {

        VerificationCode code = verificationCodeRepository
                .findFirstByCodeOrderByCreatedAtDesc(token)
                .orElseThrow(InvalidVerificationCodeException::new);

        if (code.isUsed()) {
            throw new VerificationCodeAlreadyUsedException();
        }

        if (code.isCancelled()) {
            throw new VerificationCodeCancelledException();
        }

        if (code.getExpiresAt().isBefore(LocalDateTime.now())) {
            code.setCancelled(true);
            verificationCodeRepository.save(code);
            throw new VerificationCodeExpiredException();
        }

        if (code.getPurpose() != VerificationPurpose.EMAIL_VERIFICATION) {
            throw new VerificationCodeTypeMismatchException();
        }

        SecurityUser securityUser = code.getUser();
        if (!(securityUser instanceof User user) || user.getProfileInfo() == null) {
            throw new UserNotFoundException();
        }

        String currentEmail = user.getProfileInfo().getEmail();
        if (currentEmail == null || !currentEmail.equalsIgnoreCase(email)) {
            throw new EmailMismatchException();
        }

        // Doğrulama işlemi
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        code.setUsed(true);
        code.setVerifiedAt(LocalDateTime.now());
        verificationCodeRepository.save(code);

        verificationCodeRepository.cancelAllActiveCodes(user.getId(), VerificationPurpose.EMAIL_VERIFICATION);

        fcmService.sendNotificationToToken(
                user,
                "E-Posta Doğrulandı",
                "E-posta adresiniz başarıyla doğrulandı.",
                NotificationType.SUCCESS,
                null
        );

        return new ResponseMessage("E-posta adresiniz başarıyla doğrulandı.", true);
    }


    private String buildEmailBodyFromCacheDTO(CacheUserDTO dto) {
        StringBuilder sb = new StringBuilder();

        String fullName = (dto.getName() != null && dto.getSurname() != null) ?
                dto.getName() + " " + dto.getSurname() : "Kullanıcı";

        sb.append("Sayın ").append(fullName).append(",\n\n");
        sb.append("City Card hesabınıza ait bilgiler aşağıda yer almaktadır:\n\n");

        sb.append("──────────────────────────────\n");
        sb.append("Kullanıcı ID      : ").append(dto.getId()).append("\n");
        sb.append("Kullanıcı No      : ").append(dto.getTelephone() != null ? dto.getTelephone() : "—").append("\n");
        sb.append("Ad                : ").append(dto.getName() != null ? dto.getName() : "—").append("\n");
        sb.append("Soyad             : ").append(dto.getSurname() != null ? dto.getSurname() : "—").append("\n");
        sb.append("E-posta           : ").append(dto.getEmail() != null ? dto.getEmail() : "—").append("\n");
        sb.append("TC Kimlik No      : ").append(dto.getNationalId() != null ? dto.getNationalId() : "—").append("\n");
        sb.append("Doğum Tarihi      : ").append(dto.getBirthDate() != null ? dto.getBirthDate() : "—").append("\n");
        sb.append("Cüzdan Aktif      : ").append(dto.isWalletActivated() ? "Evet" : "Hayır").append("\n");
        sb.append("Negatif Bakiye İzin: ").append(dto.isAllowNegativeBalance() ? "Evet" : "Hayır").append("\n");
        sb.append("Negatif Bakiye Limit: ").append(dto.getNegativeBalanceLimit() != null ? dto.getNegativeBalanceLimit() : "0.0").append("\n");
        sb.append("Otomatik Yükleme   : ").append(dto.isAutoTopUpEnabled() ? "Aktif" : "Pasif").append("\n");
        sb.append("──────────────────────────────\n\n");

        sb.append("Herhangi bir sorunuz için bizimle iletişime geçebilirsiniz.\n");
        sb.append("City Card Ekibi olarak sizi aramızda görmekten mutluluk duyuyoruz.\n\n");
        sb.append("İyi günler dileriz.");

        return sb.toString();
    }

    public String randomSixDigit() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000)); // 000000 ile 999999 arasında 6 hane
    }


}

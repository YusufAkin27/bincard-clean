package akin.city_card.security.controller;


import akin.city_card.admin.exceptions.AdminNotApprovedException;
import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.dto.*;
import akin.city_card.security.exception.*;
import akin.city_card.security.manager.AuthService;
import akin.city_card.verification.exceptions.VerificationCodeExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/v1/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/login")
    public TokenResponseDTO login(HttpServletRequest httpServletRequest,@RequestBody LoginRequestDTO loginRequestDTO) throws UserNotActiveException, UserRoleNotAssignedException, UserDeletedException, NotFoundUserException, IncorrectPasswordException, UnrecognizedDeviceException, PhoneNotVerifiedException, AdminNotApprovedException, UserNotFoundException, VerificationCodeStillValidException, VerificationCooldownException {
        return authService.login(loginRequestDTO,httpServletRequest);
    }

    @PostMapping("/admin-login")
    public ResponseMessage adminLogin(HttpServletRequest request, @RequestBody LoginRequestDTO loginRequestDTO) throws IncorrectPasswordException, UserNotActiveException, UserRoleNotAssignedException, UserDeletedException, AdminNotFoundException, AdminNotApprovedException, NotFoundUserException, UserNotFoundException, VerificationCodeStillValidException, VerificationCooldownException {
        return authService.adminLogin(loginRequestDTO,request);
    }

    @PostMapping("/superadmin-login")
    public ResponseMessage superadminLogin(HttpServletRequest request ,@RequestBody LoginRequestDTO loginRequestDTO) throws IncorrectPasswordException, UserNotActiveException, UserRoleNotAssignedException, UserDeletedException, SuperAdminNotFoundException, UserNotFoundException, VerificationCodeStillValidException, VerificationCooldownException {
        return authService.superadminLogin(request,loginRequestDTO);
    }

    @PostMapping("/phone-verify")
    public TokenResponseDTO phoneVerify(HttpServletRequest httpServletRequest,@RequestBody LoginPhoneVerifyCodeRequest phoneVerifyCode) throws VerificationCodeExpiredException, CancelledVerificationCodeException, UsedVerificationCodeException, InvalidVerificationCodeException {
        return authService.phoneVerify(phoneVerifyCode,httpServletRequest);
    }

    @PostMapping("/refresh")
    public TokenDTO updateAccessToken(HttpServletRequest httpServletRequest,@RequestBody UpdateAccessTokenRequestDTO updateAccessTokenRequestDTO) throws TokenIsExpiredException, TokenNotFoundException, UserNotFoundException, InvalidRefreshTokenException {
        return authService.updateAccessToken(updateAccessTokenRequestDTO,httpServletRequest);
    }
    @PostMapping("/resend-verify-code")
    public ResponseMessage resendVerifyCode(@RequestParam String telephone) throws UserNotFoundException, VerificationCodeStillValidException, VerificationCooldownException {
        return authService.resendVerifyCode(telephone);
    }

    @PostMapping("/refresh-login")
    public TokenDTO refreshLogin(HttpServletRequest httpServletRequest,@RequestBody RefreshLoginRequest request) throws UserNotFoundException, InvalidRefreshTokenException, IncorrectPasswordException, TokenIsExpiredException, TokenNotFoundException {
        return authService.refreshLogin( httpServletRequest,  request);
    }

    @GetMapping("/logout")
    public ResponseEntity<ResponseMessage> logout(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseMessage("Kullanıcı doğrulanamadı", false));
        }
        try {
            System.out.printf(userDetails.getUsername());
            ResponseMessage response = authService.logout(userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Kullanıcı bulunamadı", false));
        } catch (TokenNotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage("Token bulunamadı", false));
        }
    }

}

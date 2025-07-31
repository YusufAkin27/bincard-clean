package akin.city_card.contract.controller;

import akin.city_card.contract.core.request.AcceptContractRequest;
import akin.city_card.contract.core.response.AcceptedContractDTO;
import akin.city_card.contract.core.response.UserContractDTO;
import akin.city_card.contract.exceptions.ContractAlreadyAcceptedException;
import akin.city_card.contract.exceptions.ContractNotFoundException;
import akin.city_card.contract.service.abstacts.ContractService;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.contract.core.request.RejectContractRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/contract")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;

    @GetMapping("/contracts")
    public ResponseEntity<List<UserContractDTO>> getUserContracts(
            @AuthenticationPrincipal UserDetails userDetails
    ) throws UserNotFoundException {
        List<UserContractDTO> contracts = contractService.getUserContracts(userDetails.getUsername());
        return ResponseEntity.ok(contracts);
    }

    /**
     * Sözleşme onaylama
     */
    @PostMapping("/contracts/{contractId}/accept")
    public ResponseEntity<ResponseMessage> acceptContract(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long contractId,
            @Valid @RequestBody AcceptContractRequest request,
            HttpServletRequest httpRequest
    ) {
        // IP ve User-Agent bilgilerini request'e ekle
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        request.setIpAddress(ipAddress);
        request.setUserAgent(userAgent);

        ResponseMessage result = contractService.acceptContract(
                userDetails.getUsername(),
                contractId,
                request
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Sözleşme reddetme
     */
    @PostMapping("/contracts/{contractId}/reject")
    public ResponseEntity<ResponseMessage> rejectContract(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long contractId,
            @Valid @RequestBody RejectContractRequest request
    ) {
        ResponseMessage result = contractService.rejectContract(
                userDetails.getUsername(),
                contractId,
                request
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Onaylanmış sözleşmeleri görüntüleme
     */
    @GetMapping("/contracts/accepted")
    public ResponseEntity<List<AcceptedContractDTO>> getAcceptedContracts(
            @AuthenticationPrincipal UserDetails userDetails
    ) throws UserNotFoundException {
        List<AcceptedContractDTO> acceptedContracts = contractService.getAcceptedContracts(
                userDetails.getUsername()
        );
        return ResponseEntity.ok(acceptedContracts);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}

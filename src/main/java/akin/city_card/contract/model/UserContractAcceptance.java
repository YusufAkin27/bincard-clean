package akin.city_card.contract.model;

import akin.city_card.security.entity.SecurityUser;
import akin.city_card.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_contract_acceptances")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserContractAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private SecurityUser user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private boolean accepted;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime acceptedAt;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 1000)
    private String rejectionReason; // Reddedilme sebebi

    // Sözleşme versiyonu (snapshot)
    @Column(length = 50)
    private String contractVersion;
}
package akin.city_card.user.repository;

import akin.city_card.buscard.model.UserFavoriteCard;
import akin.city_card.news.model.NewsType;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.user.model.User;
import akin.city_card.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserNumber(String username);

    boolean existsByIdentityInfo_NationalId(String nationalId);

    User findByIdentityInfo_NationalId(String nationalId);

    User findByProfileInfo_Email(String email);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.identityInfo.nationalId) LIKE %:query%
               OR LOWER(u.userNumber) LIKE %:query%
               OR LOWER(u.profileInfo.email) LIKE %:query%
               OR LOWER(u.profileInfo.name) LIKE %:query%
               OR LOWER(u.profileInfo.surname) LIKE %:query%
            """)
    Page<User> searchByQuery(@Param("query") String query, Pageable pageable);

    @Query("SELECT ufc FROM UserFavoriteCard ufc WHERE ufc.user.userNumber = :username")
    List<UserFavoriteCard> findFavoriteCardsByUserNumber(@Param("username") String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.wallet")
    List<User> findAllWithWallet();

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.viewedNews WHERE u.userNumber = :userNumber")
    Optional<User> findByUserNumberWithViewedNews(String userNumber);

    long countByStatus(UserStatus status);


    @Query("SELECT DISTINCT u FROM User u JOIN u.geoAlerts g WHERE u.status = 'ACTIVE' AND  g.active = true")
    List<User> findAllActiveWithGeoAlerts();

}

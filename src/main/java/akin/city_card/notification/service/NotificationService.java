package akin.city_card.notification.service;

import akin.city_card.notification.model.Notification;
import akin.city_card.notification.model.NotificationType;
import akin.city_card.notification.repository.NotificationRepository;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.user.model.User;
import akin.city_card.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // 🟢 Bildirim kaydetme
    @Transactional
    public Notification saveNotification(User user, String title, String message, NotificationType type, String targetUrl) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setTargetUrl(targetUrl);
        notification.setSentAt(LocalDateTime.now());
        notification.setRead(false);
        notification.setDeleted(false);
        user.getNotifications().add(notification);
        return notificationRepository.save(notification);
    }

    // 📄 Bildirimleri getir (filtreli + sayfalı)
    public Page<Notification> getNotifications(String username, Optional<NotificationType> type, Pageable pageable) throws UserNotFoundException {
        User user = getUserByUsernameOrThrow(username);
        if (type.isPresent()) {
            return notificationRepository.findByUserIdAndTypeAndDeletedFalseOrderBySentAtDesc(user.getId(), type.get(), pageable);
        }
        return notificationRepository.findByUserIdAndDeletedFalseOrderBySentAtDesc(user.getId(), pageable);
    }

    // 🔍 Bildirim detayı getir
    public Optional<Notification> getNotificationById(String username, Long notificationId) throws UserNotFoundException {
        User user = getUserByUsernameOrThrow(username);
        return notificationRepository.findByIdAndDeletedFalse(notificationId)
                .filter(notification -> notification.getUser().getId().equals(user.getId()));
    }

    // ❌ Soft delete
    @Transactional
    public void softDeleteNotification(String username, Long notificationId) throws UserNotFoundException {
        User user = getUserByUsernameOrThrow(username);
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (notification.getUser().getId().equals(user.getId())) {
                notification.setDeleted(true);
                notificationRepository.save(notification);
            } else {
                throw new IllegalArgumentException("Bu kullanıcıya ait olmayan bildirimi silemezsiniz.");
            }
        });
    }

    private User getUserByUsernameOrThrow(String username) throws UserNotFoundException {
        return userRepository.findByUserNumber(username)
                .orElseThrow(UserNotFoundException::new);
    }
}

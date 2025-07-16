package akin.city_card.notification;

import akin.city_card.notification.model.Notification;
import akin.city_card.notification.model.NotificationType;
import akin.city_card.notification.repository.NotificationRepository;
import akin.city_card.user.model.User;
import akin.city_card.user.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public String sendNotificationToUser(Long userId, String title, String body, NotificationType type, String targetUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + userId));

        // Bildirimi Firebase ile gönder
        try {
            com.google.firebase.messaging.Notification notification = com.google.firebase.messaging.Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            Message message = Message.builder()
                    .setToken(user.getDeviceInfo().getFcmToken())
                    .setNotification(notification)
                    .build();

            String response = firebaseMessaging.send(message);

            // Veritabanına bildirimi kaydet
            Notification dbNotification = new Notification();
            dbNotification.setUser(user);
            dbNotification.setTitle(title);
            dbNotification.setMessage(body);
            dbNotification.setType(type);
            dbNotification.setTargetUrl(targetUrl);
            notificationRepository.save(dbNotification);

            return "Bildirim gönderildi. Mesaj ID: " + response;

        } catch (FirebaseMessagingException e) {
            throw new RuntimeException("Bildirim gönderilemedi", e);
        }
    }
}

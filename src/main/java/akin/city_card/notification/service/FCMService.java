package akin.city_card.notification.service;

import akin.city_card.notification.model.NotificationType;
import akin.city_card.user.model.User;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FCMService {

    private final NotificationService notificationService;

    @Async
    public String sendNotificationToToken(User user, String title, String body, NotificationType type, String targetUrl) {
        // Bildirimi her durumda veritabanına kaydet
        notificationService.saveNotification(user, title, body, type, targetUrl);

        // Null kontrolü: deviceInfo ya da token null/boş ise sadece kayıt yapılır
        if (user.getDeviceInfo() == null ||
                user.getDeviceInfo().getFcmToken() == null ||
                user.getDeviceInfo().getFcmToken().isBlank()) {
            return "Cihaz bilgisi veya FCM token yok, yalnızca bildirim kaydedildi.";
        }

        Notification firebaseNotification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .setAps(Aps.builder().setContentAvailable(true).setSound("default").build())
                .putHeader("apns-priority", "10")
                .build();

        Message message = Message.builder()
                .setToken(user.getDeviceInfo().getFcmToken())
                .setNotification(firebaseNotification)
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            return "Bildirim gönderildi. Mesaj ID: " + response;
        } catch (FirebaseMessagingException e) {
            return "Bildirimin veri tabanına kaydı yapıldı fakat FCM gönderimi başarısız oldu: " + e.getMessage();
        }
    }
}

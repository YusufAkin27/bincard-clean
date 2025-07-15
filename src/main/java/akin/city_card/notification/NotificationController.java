package akin.city_card.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final FCMService fcmService;

    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(@RequestParam String token,
                                              @RequestParam String title,
                                              @RequestParam String message) {
        String result = fcmService.sendNotificationToToken(token, title, message);
        return ResponseEntity.ok(result);
    }
}

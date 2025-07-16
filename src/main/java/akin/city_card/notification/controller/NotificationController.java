package akin.city_card.notification.controller;

import akin.city_card.notification.model.Notification;
import akin.city_card.notification.model.NotificationType;
import akin.city_card.notification.service.NotificationService;
import akin.city_card.security.exception.UserNotFoundException;
import akin.city_card.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @AuthenticationPrincipal UserDetails  userDetails,
            @RequestParam Optional<NotificationType> type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) throws UserNotFoundException {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationService.getNotifications(userDetails.getUsername(), type, pageable);
        return ResponseEntity.ok(notifications);
    }

    // 📄 Bildirim detayı
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotificationDetail(@AuthenticationPrincipal UserDetails userDetails,@PathVariable Long id) throws UserNotFoundException {
        return ResponseEntity.of(notificationService.getNotificationById(userDetails.getUsername(),id));
    }

    // ❌ Bildirim silme (soft delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) throws UserNotFoundException {
        notificationService.softDeleteNotification(userDetails.getUsername(),id);
        return ResponseEntity.noContent().build();
    }
}

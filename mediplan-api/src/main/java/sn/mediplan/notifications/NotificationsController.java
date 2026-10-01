package sn.mediplan.notifications;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sn.mediplan.notifications.NotificationsService.Notification;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    private final NotificationsService notifications;

    public NotificationsController(NotificationsService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<Notification> lister(@RequestParam Long serviceId) {
        return notifications.notifications(serviceId);
    }
}

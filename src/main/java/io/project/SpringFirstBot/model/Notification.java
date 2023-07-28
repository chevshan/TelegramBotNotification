package io.project.SpringFirstBot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "message")
    private String message;
    @Column(name = "notificationtime")
    private LocalDateTime notificationTime;

    @ManyToOne
    @JoinColumn(name = "person_username", referencedColumnName = "username")
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getNotificationTime() {
        return notificationTime;
    }

    public void setNotificationTime(LocalDateTime notificationTime) {
        this.notificationTime = notificationTime;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        String formattedMonth = String.format("%02d", notificationTime.getMonthValue());
        String formattedDayOfMonth = String.format("%02d", notificationTime.getDayOfMonth());
        String formattedMinute = String.format("%02d", notificationTime.getMinute());
        String formattedSecond = String.format("%02d", notificationTime.getSecond());
        return notificationTime.getYear() + "-" + formattedMonth
                + "-" + formattedDayOfMonth + "  " + notificationTime.getHour() + ":"
                + formattedMinute + ":" + formattedSecond + "  " + message;
    }
}

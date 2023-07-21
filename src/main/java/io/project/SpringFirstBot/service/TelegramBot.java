package io.project.SpringFirstBot.service;

import io.project.SpringFirstBot.config.BotConfig;
import io.project.SpringFirstBot.model.Notification;
import io.project.SpringFirstBot.repository.NotificationRepository;
import io.project.SpringFirstBot.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import io.project.SpringFirstBot.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private static String createStatus = null;
    private final String NOT_REGISTER = "Изивините, Вы не зарегистрировались";
    private final String NAME_NOTIFICATION = "Введите описание упоминания";
    private final String TIME_NOTIFICATION = "Введите дату и время уведомления (формат: гггг-мм-чч hh:mm)";
    private final String SUCCESSFULLY_NOTIFICATION = "Уведомление успешно создано и сохранено";
    private final String NOTIFICATION_LIST_MESSAGE = "Ваш список уведомлений:";
    private final String NOT_CORRECT_DATE = "Пожалуйста, введите корректно значения даты и времени";
    private final String TEXT_TO_HELP = "Здравствуйте! Я бот для создания и вызова уведомлений! \n" +
            "Ниже предоставлен список команд, как можно со мной взаимодействовать: \n" +
            "/start: запуск бота \n" +
            "/help: список команд взаимодействия с ботом \n" +
            "/create: создание уведомления \n" +
            "/show: список всех Ваших уведомлений";

    @Autowired
    public TelegramBot(BotConfig config, UserRepository userRepository, NotificationRepository notificationRepository) {
        this.config = config;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/create", "create notification"));
        listOfCommands.add(new BotCommand("/show", "show your notification list"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));

        try {
            execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            User user = userRepository.findById(chatId).orElse(null);

            if ("IN_PROCESS".equals(createStatus)) {
                fillOutTheNotification(chatId, user, update.getMessage());
                return;
            }

            switch (messageText) {
                case "/start": {
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                }
                case "/help": {
                    sendMessage(chatId, TEXT_TO_HELP);
                    break;
                }
                case "/create": {
                    createNotification(chatId, user);
                    break;
                }
                case "/show": {
                    showNotificationList(chatId, user);
                    break;
                }
                default:
                    sendMessage(chatId, "Sorry, command wasn't recognized");
            }

        }
    }

    private void showNotificationList(Long chatId, User user) {
        if (isUserNull(chatId, user)) {
            return;
        }
        List<Notification> userList = user.getNotificationList();
        StringBuilder stringBuilder = new StringBuilder(NOTIFICATION_LIST_MESSAGE + "\n");
        for (Notification notification : userList) {
            stringBuilder.append(notification.toString()).append("\n");
        }
        sendMessage(chatId, stringBuilder.toString());
    }

    private void fillOutTheNotification(Long chatId, User user, Message message) {
        if (isUserNull(chatId, user)) {
            return;
        }
        Notification notification = Objects.requireNonNull(user).getNotificationList().get(user.getNotificationList().size() - 1);

        if (notification.getMessage() == null) {
            notification.setMessage(message.getText());
            notificationRepository.save(notification);
            sendMessage(chatId, TIME_NOTIFICATION);
            return;
        }

        if (notification.getNotificationTime() == null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            try {
                notification.setNotificationTime(LocalDateTime.parse(message.getText(), formatter));
            }
            catch (DateTimeParseException exception) {
                sendMessage(chatId, NOT_CORRECT_DATE);
                return;
            }
            notificationRepository.save(notification);
            sendMessage(chatId, SUCCESSFULLY_NOTIFICATION);
            createStatus = null;
        }
    }

    @Transactional
    public void createNotification(Long chatId, User user) {
        if (isUserNull(chatId, user)) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(user);
        notificationRepository.save(notification);
        user.initializeNotificationList();
        user.getNotificationList().add(notification);
        createStatus = "IN_PROCESS";
        sendMessage(chatId, NAME_NOTIFICATION);
    }

    private void registerUser(Message message) {
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            var chatId = message.getChatId();
            var chat = message.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(LocalDateTime.now());

            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    private void startCommandReceived(long chatId, String name) {
        String answer = "Hi " + name + ", nice to meet you!";
        log.info("Replied to user " + name);

        sendMessage(chatId, answer);
    }

    @Scheduled(cron = "${cron.scheduler}")
    protected void sendNotification() {
        System.out.println("check");
        LocalDateTime now = LocalDateTime.now();
        try {
            List<Notification> notificationList = notificationRepository.findAll();
            for (Notification notification : notificationList) {
                if (notification.getNotificationTime().getMinute() == now.getMinute()
                        && notification.getNotificationTime().getHour() == now.getHour()
                        && notification.getNotificationTime().getDayOfMonth() == now.getDayOfMonth()
                        && notification.getNotificationTime().getMonth() == now.getMonth()
                        && notification.getNotificationTime().getYear() == now.getYear()) {
                    sendMessage(notification.getUser().getChatId(), notification.toString());
                    notificationRepository.delete(notification);
                }
            }
        } catch (NullPointerException e) {
            System.out.println("Пустой лист");
        }
    }


    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private boolean isUserNull(Long chatId, User user) {
        if (user == null) {
            sendMessage(chatId, NOT_REGISTER);
            return true;
        } else return false;
    }
}
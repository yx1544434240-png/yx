package com.agedcare.modules.notification.service;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.notification.entity.MessageNotification;
import com.agedcare.modules.notification.mapper.MessageNotificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final MessageNotificationMapper notificationMapper;

    public void send(Long userId, String eventKey, String title, String content, Long refOrderId) {
        MessageNotification notif = MessageNotification.builder()
                .userId(userId)
                .eventKey(eventKey)
                .title(title)
                .content(content)
                .isRead(0)
                .refOrderId(refOrderId)
                .createdAt(LocalDateTime.now())
                .build();
        notificationMapper.insert(notif);
    }

    public List<MessageNotification> getMyNotifications() {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException("未登录");
        }
        return notificationMapper.selectList(new LambdaQueryWrapper<MessageNotification>()
                .eq(MessageNotification::getUserId, currentUser.getId())
                .orderByDesc(MessageNotification::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException("未登录");
        }
        MessageNotification notif = notificationMapper.selectById(id);
        if (notif != null) {
            if (!notif.getUserId().equals(currentUser.getId())) {
                throw new BusinessException(403, "无权操作他人的通知消息");
            }
            notif.setIsRead(1);
            notif.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notif);
        }
    }
}

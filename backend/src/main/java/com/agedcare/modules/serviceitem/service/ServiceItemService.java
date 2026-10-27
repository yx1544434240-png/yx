package com.agedcare.modules.serviceitem.service;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.modules.serviceitem.entity.ServiceItem;
import com.agedcare.modules.serviceitem.mapper.ServiceItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private final ServiceItemMapper serviceItemMapper;

    public List<ServiceItem> listOnShelfItems(String category) {
        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<ServiceItem>()
                .eq(ServiceItem::getStatus, "ON_SHELF");
        if (StringUtils.hasText(category)) {
            wrapper.eq(ServiceItem::getCategory, category);
        }
        wrapper.orderByAsc(ServiceItem::getSortOrder).orderByDesc(ServiceItem::getCreatedAt);
        return serviceItemMapper.selectList(wrapper);
    }

    public Page<ServiceItem> pageItems(int pageNum, int pageSize, String name, String category, String status) {
        Page<ServiceItem> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(ServiceItem::getName, name);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(ServiceItem::getCategory, category);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ServiceItem::getStatus, status);
        }
        wrapper.orderByAsc(ServiceItem::getSortOrder).orderByDesc(ServiceItem::getCreatedAt);
        return serviceItemMapper.selectPage(page, wrapper);
    }

    public ServiceItem getById(Long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("服务项目不存在");
        }
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceItem saveOrUpdate(ServiceItem item) {
        if (item.getPrice() == null || item.getPrice().doubleValue() < 0) {
            throw new BusinessException("服务单价不能小于0");
        }
        if (item.getDurationMinutes() == null || item.getDurationMinutes() <= 0) {
            throw new BusinessException("服务预计时长必须大于0分钟");
        }

        if (item.getId() == null) {
            if (!StringUtils.hasText(item.getStatus())) {
                item.setStatus("ON_SHELF");
            }
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            serviceItemMapper.insert(item);
        } else {
            item.setUpdatedAt(LocalDateTime.now());
            serviceItemMapper.updateById(item);
        }
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        ServiceItem item = getById(id);
        item.setStatus("ON_SHELF".equals(item.getStatus()) ? "OFF_SHELF" : "ON_SHELF");
        item.setUpdatedAt(LocalDateTime.now());
        serviceItemMapper.updateById(item);
    }
}

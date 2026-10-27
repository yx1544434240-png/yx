package com.agedcare.modules.order.mapper;

import com.agedcare.modules.order.entity.ServiceOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {

    @Select("SELECT * FROM service_order WHERE id = #{id} FOR UPDATE")
    ServiceOrder selectByIdForUpdate(@Param("id") Long id);

    /** 只在尚未支付时写入支付人；返回 0 表示已被支付(并发或重复请求)。 */
    @org.apache.ibatis.annotations.Update("UPDATE service_order SET paid_by_user_id = #{userId}, paid_at = #{paidAt}, updated_at = #{paidAt} WHERE id = #{id} AND paid_at IS NULL AND status <> 'CANCELLED'")
    int markPaid(@Param("id") Long id, @Param("userId") Long userId, @Param("paidAt") java.time.LocalDateTime paidAt);
}

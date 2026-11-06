package com.drone.rental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.drone.rental.entity.RentalOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租赁订单Mapper接口
 */
@Mapper
public interface RentalOrderMapper extends BaseMapper<RentalOrder> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM rental_order WHERE id=#{id} AND deleted=0 FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    RentalOrder selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}

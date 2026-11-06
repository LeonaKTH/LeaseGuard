package com.drone.rental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.drone.rental.entity.MaintenanceTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 维修工单Mapper接口
 */
@Mapper
public interface MaintenanceTicketMapper extends BaseMapper<MaintenanceTicket> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM maintenance_ticket WHERE id=#{id} AND deleted=0 FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    MaintenanceTicket selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}

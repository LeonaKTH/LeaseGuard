package com.drone.rental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.drone.rental.entity.FaultReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 故障上报Mapper接口
 */
@Mapper
public interface FaultReportMapper extends BaseMapper<FaultReport> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM fault_report WHERE id=#{id} AND deleted=0 FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    FaultReport selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}

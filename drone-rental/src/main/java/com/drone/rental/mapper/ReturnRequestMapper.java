package com.drone.rental.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.drone.rental.entity.ReturnRequest;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ReturnRequestMapper extends BaseMapper<ReturnRequest> {    @org.apache.ibatis.annotations.Select("SELECT * FROM return_request WHERE id=#{id} FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    ReturnRequest selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}

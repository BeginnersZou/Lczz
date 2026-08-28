package com.lczz.order.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrderEntity> {
    @Select("SELECT * FROM work_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkOrderEntity selectForUpdate(@Param("id") long id);
}

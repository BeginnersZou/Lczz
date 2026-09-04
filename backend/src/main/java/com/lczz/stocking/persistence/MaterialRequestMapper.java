package com.lczz.stocking.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialRequestMapper extends BaseMapper<MaterialRequestEntity> {
    @Select("SELECT * FROM material_request WHERE id=#{id} FOR UPDATE")
    MaterialRequestEntity selectForUpdate(long id);
}

package com.lczz.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    UserEntity selectForUpdate(@Param("id") long id);
}

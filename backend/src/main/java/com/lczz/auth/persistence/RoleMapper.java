package com.lczz.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
    @Select("""
            SELECT r.role_code
              FROM sys_role r
              JOIN sys_user_role ur ON ur.role_id = r.id
             WHERE ur.user_id = #{userId} AND r.enabled = 1
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT ur.user_id AS user_id, r.role_code AS role_code
              FROM sys_user_role ur
              JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
             WHERE ur.user_id IN
             <foreach collection='userIds' item='userId' open='(' separator=',' close=')'>
               #{userId}
             </foreach>
             ORDER BY ur.user_id, r.role_code
            </script>
            """)
    List<UserRoleCodeRow> selectRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);

    @Select("""
            SELECT u.id
              FROM sys_user u
              JOIN sys_user_role ur ON ur.user_id = u.id
              JOIN sys_role r ON r.id = ur.role_id
             WHERE r.role_code = 'ADMIN'
               AND r.enabled = TRUE
               AND u.deleted = FALSE
               AND u.account_status = 'ENABLED'
               AND u.audit_status = 'APPROVED'
               AND u.blacklist = FALSE
             ORDER BY u.id
             FOR UPDATE
            """)
    List<Long> selectActiveAdminIdsForUpdate();
}

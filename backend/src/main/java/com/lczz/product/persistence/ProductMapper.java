package com.lczz.product.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
    @Select("SELECT * FROM product WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ProductEntity selectForUpdate(@Param("id") long id);
}

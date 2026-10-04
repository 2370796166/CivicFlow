package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ServiceOutletEntity;
import com.civicflow.resource.enums.ResourceStatus;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ServiceOutletMapper extends BaseMapper<ServiceOutletEntity> {
    ServiceOutletEntity selectActiveById(@Param("id") long id);

    ServiceOutletEntity selectByIdForUpdate(@Param("id") long id);

    List<ServiceOutletEntity> selectAdminPage(
            @Param("keyword") String keyword,
            @Param("status") ResourceStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminPage(@Param("keyword") String keyword, @Param("status") ResourceStatus status);

    List<ServiceOutletEntity> selectUserPage(
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size);

    long countUserPage(@Param("keyword") String keyword);

    int updateDetails(
            @Param("id") long id,
            @Param("code") String code,
            @Param("name") String name,
            @Param("address") String address,
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("contactPhoneCipher") byte[] contactPhoneCipher,
            @Param("contactPhoneKeyVersion") Integer contactPhoneKeyVersion,
            @Param("expectedVersion") int expectedVersion);

    int updateStatus(
            @Param("id") long id,
            @Param("status") ResourceStatus status,
            @Param("expectedVersion") int expectedVersion);

    int logicallyDelete(@Param("id") long id, @Param("expectedVersion") int expectedVersion);
}

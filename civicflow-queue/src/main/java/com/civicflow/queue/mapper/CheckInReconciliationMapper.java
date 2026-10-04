package com.civicflow.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.queue.entity.CheckinReconciliationRecordEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CheckInReconciliationMapper extends BaseMapper<CheckinReconciliationRecordEntity> {
    List<CheckinReconciliationRecordEntity> selectDue(@Param("limit") int limit);

    int markLinked(@Param("id") long id);

    int defer(@Param("id") long id, @Param("error") String error);
}

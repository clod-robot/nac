package com.nac.auth.mapper;

import com.nac.auth.entity.SmsRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsRecordMapper {
    int insert(SmsRecord record);
}

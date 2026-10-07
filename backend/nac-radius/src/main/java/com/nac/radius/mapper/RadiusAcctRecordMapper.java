package com.nac.radius.mapper;

import com.nac.radius.entity.RadiusAcctRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RadiusAcctRecordMapper {
    int insert(RadiusAcctRecord record);
}

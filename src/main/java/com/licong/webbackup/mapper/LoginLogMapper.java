package com.licong.webbackup.mapper;

import com.licong.webbackup.entity.LoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface LoginLogMapper {

    @Insert("""
            INSERT INTO login_logs (user_id, ip_address, user_agent)
            VALUES (#{userId}, #{ipAddress}, #{userAgent})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "logId")
    int insert(LoginLog loginLog);
}

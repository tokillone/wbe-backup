package com.licong.webbackup.mapper;

import com.licong.webbackup.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE user_id = #{userId}")
    User findById(@Param("userId") Long userId);

    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE username = #{account} OR email = #{account} LIMIT 1")
    User findByUsernameOrEmail(@Param("account") String account);

    @Insert("""
            INSERT INTO users (username, email, password_hash, full_name, role, is_active)
            VALUES (#{username}, #{email}, #{passwordHash}, #{fullName}, #{role}, #{isActive})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insert(User user);

    @Update("UPDATE users SET password_hash = #{passwordHash} WHERE user_id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    @Update("UPDATE users SET last_login = NOW() WHERE user_id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId);
}

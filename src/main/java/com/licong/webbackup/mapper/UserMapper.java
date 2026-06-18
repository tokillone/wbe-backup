package com.licong.webbackup.mapper;

import com.licong.webbackup.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    @Select("""
            SELECT *
            FROM users
            ORDER BY created_at DESC, user_id DESC
            """)
    List<User> findAll();

    @Select("""
            <script>
            SELECT *
            FROM users
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (
                username LIKE CONCAT('%', #{keyword}, '%')
                OR email LIKE CONCAT('%', #{keyword}, '%')
                OR full_name LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            <if test="role != null and role != ''">
              AND role = #{role}
            </if>
            <if test="canUpload != null">
              AND can_upload = #{canUpload}
            </if>
            <if test="canReviewUploads != null">
              AND can_review_uploads = #{canReviewUploads}
            </if>
            <if test="canSyncData != null">
              AND can_sync_data = #{canSyncData}
            </if>
            <if test="canDownload != null">
              AND can_download = #{canDownload}
            </if>
            ORDER BY created_at DESC, user_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<User> findPage(@Param("keyword") String keyword,
                        @Param("role") String role,
                        @Param("canUpload") Boolean canUpload,
                        @Param("canReviewUploads") Boolean canReviewUploads,
                        @Param("canSyncData") Boolean canSyncData,
                        @Param("canDownload") Boolean canDownload,
                        @Param("limit") int limit,
                        @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM users
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (
                username LIKE CONCAT('%', #{keyword}, '%')
                OR email LIKE CONCAT('%', #{keyword}, '%')
                OR full_name LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            <if test="role != null and role != ''">
              AND role = #{role}
            </if>
            <if test="canUpload != null">
              AND can_upload = #{canUpload}
            </if>
            <if test="canReviewUploads != null">
              AND can_review_uploads = #{canReviewUploads}
            </if>
            <if test="canSyncData != null">
              AND can_sync_data = #{canSyncData}
            </if>
            <if test="canDownload != null">
              AND can_download = #{canDownload}
            </if>
            </script>
            """)
    long countPage(@Param("keyword") String keyword,
                   @Param("role") String role,
                   @Param("canUpload") Boolean canUpload,
                   @Param("canReviewUploads") Boolean canReviewUploads,
                   @Param("canSyncData") Boolean canSyncData,
                   @Param("canDownload") Boolean canDownload);

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

    @Update("""
            UPDATE users
            SET role = #{role},
                can_upload = #{canUpload},
                can_review_uploads = #{canReviewUploads},
                can_sync_data = #{canSyncData},
                can_download = #{canDownload}
            WHERE user_id = #{userId}
            """)
    int updatePermissions(@Param("userId") Long userId,
                          @Param("role") String role,
                          @Param("canUpload") Boolean canUpload,
                          @Param("canReviewUploads") Boolean canReviewUploads,
                          @Param("canSyncData") Boolean canSyncData,
                          @Param("canDownload") Boolean canDownload);
}

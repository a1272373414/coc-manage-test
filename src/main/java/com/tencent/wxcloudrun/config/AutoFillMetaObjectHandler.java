package com.tencent.wxcloudrun.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充审计字段：createdAt / updatedAt / createdBy / updatedBy。
 */
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

  private String currentUser() {
    AuthUser user = UserContext.get();
    return user == null ? null : user.getUsername();
  }

  @Override
  public void insertFill(MetaObject metaObject) {
    LocalDateTime now = LocalDateTime.now();
    strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
    strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    setFieldValByName("createdBy", currentUser(), metaObject);
    setFieldValByName("updatedBy", currentUser(), metaObject);
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    setFieldValByName("updatedBy", currentUser(), metaObject);
  }
}

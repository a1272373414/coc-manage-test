package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/clan/member")
public class ClanMemberController extends BaseCrudController<ClanMember> {

  @Resource
  private ClanMemberMapper clanMemberMapper;

  @Override
  protected BaseMapper<ClanMember> mapper() {
    return clanMemberMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("member_name", "member_no", "clan_no");
  }

  /**
   * 新增部落成员。同一部落群组（group_no）下做唯一性校验：
   * - 成员编号为空时，校验成员名称唯一
   * - 成员编号不为空时，校验成员名称+成员编号唯一
   *
   * 普通用户的 group_no 由多租户拦截器自动过滤；超级管理员需从请求体取 groupNo。
   */
  @Override
  @PostMapping
  public ApiResponse create(@RequestBody ClanMember body) {
    if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
      return ApiResponse.error("成员名称不能为空");
    }
    String groupNo = UserContext.getGroupNo();
    if (groupNo == null || groupNo.isEmpty()) {
      groupNo = body.getGroupNo();
    }
    QueryWrapper<ClanMember> qw = new QueryWrapper<>();
    if (groupNo != null && !groupNo.isEmpty()) {
      qw.eq("group_no", groupNo);
    }
    qw.eq("member_name", body.getMemberName());
    boolean hasNo = body.getMemberNo() != null && !body.getMemberNo().trim().isEmpty();
    if (hasNo) {
      qw.eq("member_no", body.getMemberNo());
    }
    Long count = clanMemberMapper.selectCount(qw);
    if (count != null && count > 0) {
      return ApiResponse.error(hasNo
          ? "同一群组下已存在相同成员名称和编号的成员"
          : "同一群组下已存在相同成员名称的成员");
    }
    body.setId(null);
    clanMemberMapper.insert(body);
    return ApiResponse.ok(body);
  }
}

package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
   * 新增部落成员。同一群组（group_no）内做条件唯一性校验：
   * - 成员编号不为空时，校验成员编号唯一
   * - 成员编号为空时，校验成员名称唯一
   *
   * 普通用户的 group_no 由多租户拦截器自动过滤；超级管理员需从请求体取 groupNo。
   */
  @Override
  @PostMapping
  public ApiResponse create(@RequestBody ClanMember body) {
    if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
      return ApiResponse.error("成员名称不能为空");
    }
    ApiResponse dup = checkDuplicate(body, null);
    if (dup != null) {
      return dup;
    }
    body.setId(null);
    clanMemberMapper.insert(body);
    return ApiResponse.ok(body);
  }

  /**
   * 编辑部落成员。复用与新增一致的“编号/名称”条件唯一校验，并排除记录自身。
   */
  @Override
  @PutMapping
  public ApiResponse update(@RequestBody ClanMember body) {
    if (body.getId() == null) {
      return ApiResponse.error("id 不能为空");
    }
    if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
      return ApiResponse.error("成员名称不能为空");
    }
    ApiResponse dup = checkDuplicate(body, body.getId());
    if (dup != null) {
      return dup;
    }
    clanMemberMapper.updateById(body);
    return ApiResponse.ok(body);
  }

  /**
   * 条件唯一校验（同一群组 group_no 内）：
   * - 成员编号不为空 → 按 (group_no, member_no) 查重
   * - 成员编号为空   → 按 (group_no, member_name) 查重
   * excludeId 不为空时排除该记录本身（编辑场景）。无群组上下文时返回 null（跳过校验）。
   */
  private ApiResponse checkDuplicate(ClanMember body, Long excludeId) {
    String groupNo = UserContext.getGroupNo();
    if (groupNo == null || groupNo.isEmpty()) {
      groupNo = body.getGroupNo();
    }
    if (groupNo == null || groupNo.isEmpty()) {
      return null;
    }
    boolean hasNo = body.getMemberNo() != null && !body.getMemberNo().trim().isEmpty();
    QueryWrapper<ClanMember> qw = new QueryWrapper<>();
    qw.eq("group_no", groupNo);
    if (body.getClanNo() != null && !body.getClanNo().trim().isEmpty()) {
      qw.eq("clan_no", body.getClanNo().trim());
    }
    if (hasNo) {
      qw.eq("member_no", body.getMemberNo().trim());
    } else {
      qw.eq("member_name", body.getMemberName().trim());
    }
    if (excludeId != null) {
      qw.ne("id", excludeId);
    }
    Long count = clanMemberMapper.selectCount(qw);
    if (count != null && count > 0) {
      return ApiResponse.error(hasNo
          ? "同一群组下已存在相同成员编号的成员"
          : "同一群组下已存在相同成员名称的成员");
    }
    return null;
  }
}

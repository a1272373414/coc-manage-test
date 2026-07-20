package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.util.StreamUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clan/group")
@SuppressWarnings("all")
public class ClanGroupController extends BaseCrudController<ClanGroup> {

  @Resource
  private ClanGroupMapper clanGroupMapper;
  @Resource
  private SysUserMapper sysUserMapper;

  @Override
  protected BaseMapper<ClanGroup> mapper() {
    return clanGroupMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("group_no", "group_name");
  }

  /** 重写分页接口：查询完成后批量关联 sys_user 填充 ownerName，避免 N+1 查询 */
  @Override
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<ClanGroup> page = PageResult.page(current, size);
    QueryWrapper<ClanGroup> qw = new QueryWrapper<>();
    List<String> fields = keywordFields();
    if (keyword != null && !keyword.trim().isEmpty() && !fields.isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> {
        boolean first = true;
        for (String field : fields) {
          if (!first) {
            w.or();
          }
          w.like(field, kw);
          first = false;
        }
      });
    }
    qw.orderByDesc("id");
    mapper().selectPage(page, qw);

    List<ClanGroup> records = page.getRecords();
    if (records != null && !records.isEmpty()) {
      // 收集所有非空 ownerId，批量查询用户名
      Set<Long> ownerIds = StreamUtils.mapNonNullToSet(records, ClanGroup::getOwnerId);
      if (!ownerIds.isEmpty()) {
        List<SysUser> users = sysUserMapper.selectBatchIds(ownerIds);
        Map<Long, String> idToName = new HashMap<>();
        for (SysUser u : users) {
          // 优先显示昵称，其次用户名
          idToName.put(u.getId(), u.getNickname() != null && !u.getNickname().isEmpty()
              ? u.getNickname() : u.getUsername());
        }
        for (ClanGroup g : records) {
          if (g.getOwnerId() != null) {
            g.setOwnerName(idToName.get(g.getOwnerId()));
          }
        }
      }
    }
    return ApiResponse.ok(PageResult.of(page));
  }

  /** 重写详情接口：填充 ownerName */
  @Override
  @GetMapping("/{id}")
  public ApiResponse getById(@PathVariable Long id) {
    ClanGroup group = clanGroupMapper.selectById(id);
    if (group != null && group.getOwnerId() != null) {
      SysUser user = sysUserMapper.selectById(group.getOwnerId());
      if (user != null) {
        group.setOwnerName(user.getNickname() != null && !user.getNickname().isEmpty()
            ? user.getNickname() : user.getUsername());
      }
    }
    return ApiResponse.ok(group);
  }
}

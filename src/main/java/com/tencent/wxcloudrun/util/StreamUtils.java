package com.tencent.wxcloudrun.util;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stream 操作工具类，集中处理 null safety。
 * 使用方式：
 * <pre>{@code
 *   List<Long> ids = StreamUtils.mapNonNull(roleMenus, SysRoleMenu::getMenuId);
 *   Set<Long>  idSet = StreamUtils.mapNonNullToSet(userRoles, SysUserRole::getRoleId);
 * }</pre>
 * 等价于：
 * <pre>{@code
 *   list.stream()
 *       .filter(java.util.Objects::nonNull)         // 过滤源列表中的 null 元素
 *       .map(SysRoleMenu::getMenuId)
 *       .filter(java.util.Objects::nonNull)          // 过滤 map 结果中的 null
 *       .collect(Collectors.toList());
 * }</pre>
 */
public final class StreamUtils {

  private StreamUtils() {}

  /**
   * 将集合中的元素映射为新类型并过滤掉 null 元素和 null 结果，返回 List。
   *
   * @param source 源集合，可为 null（返回空 List）
   * @param mapper 映射函数，不能为空
   * @return 不含 null 的 List
   */
  public static <T, R> List<R> mapNonNull(Iterable<T> source, Function<? super T, ? extends R> mapper) {
    if (source == null) {
      return java.util.Collections.emptyList();
    }
    return stream(source)
        .map(mapper)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * 将集合中的元素映射为新类型并过滤掉 null 元素和 null 结果，返回 Set。
   *
   * @param source 源集合，可为 null（返回空 Set）
   * @param mapper 映射函数，不能为空
   * @return 不含 null 的 Set
   */
  public static <T, R> Set<R> mapNonNullToSet(Iterable<T> source, Function<? super T, ? extends R> mapper) {
    if (source == null) {
      return java.util.Collections.emptySet();
    }
    return stream(source)
        .map(mapper)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * 将 Iterable 转为 Stream，自动过滤 null 元素。
   */
  private static <T> Stream<T> stream(Iterable<T> source) {
    return streamNullable(source).filter(Objects::nonNull);
  }

  /**
   * 将 Iterable 转为 Stream，保留 null 元素（用于在 filter 之前调用）。
   */
  private static <T> Stream<T> streamNullable(Iterable<T> source) {
    return source instanceof java.util.Collection
        ? ((java.util.Collection<T>) source).stream()
        : java.util.stream.StreamSupport.stream(source.spliterator(), false);
  }
}

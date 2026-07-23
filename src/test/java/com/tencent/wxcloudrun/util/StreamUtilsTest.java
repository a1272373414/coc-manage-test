package com.tencent.wxcloudrun.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamUtils 单元测试：验证 null-safe 集合映射工具。
 */
@DisplayName("Stream 工具类测试")
class StreamUtilsTest {

  @Test
  @DisplayName("mapNonNull - 正常映射")
  void mapNonNull_normal() {
    List<String> source = Arrays.asList("a", "b", "c");
    List<Integer> result = StreamUtils.mapNonNull(source, String::length);
    assertEquals(3, result.size());
    assertTrue(result.containsAll(Arrays.asList(1, 1, 1)));
  }

  @Test
  @DisplayName("mapNonNull - 过滤 null 元素和 null 结果")
  void mapNonNull_filtersNulls() {
    List<String> source = Arrays.asList("a", null, "b", null, "c");
    List<String> result = StreamUtils.mapNonNull(source, (String s) -> s);
    assertEquals(3, result.size());
    assertFalse(result.contains(null));
  }

  @Test
  @DisplayName("mapNonNull - null 源集合返回空 List")
  void mapNonNull_nullSource() {
    List<String> result = StreamUtils.<String, String>mapNonNull(null, s -> s);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("mapNonNullToSet - 正常映射为 Set")
  void mapNonNullToSet_normal() {
    List<Integer> source = Arrays.asList(1, 2, 3, 2, 1);
    Set<Integer> result = StreamUtils.mapNonNullToSet(source, i -> i * 10);
    assertEquals(3, result.size());
    assertTrue(result.containsAll(Arrays.asList(10, 20, 30)));
  }

  @Test
  @DisplayName("mapNonNullToSet - 过滤 null 结果")
  void mapNonNullToSet_filtersNullResults() {
    List<String> source = Arrays.asList("a", "", "b", "", "c");
    Set<String> result = StreamUtils.mapNonNullToSet(source, s -> s.isEmpty() ? null : s);
    assertEquals(3, result.size());
    assertFalse(result.contains(null));
  }

  @Test
  @DisplayName("mapNonNullToSet - 空集合返回空 Set")
  void mapNonNullToSet_emptySource() {
    Set<String> result = StreamUtils.mapNonNullToSet(Collections.<String>emptyList(), (String s) -> s);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}

package com.acme.courseplatform.shared.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SortSpecTest {

  private static final Map<String, String> ALLOWED =
      Map.of("createdAt", "created_at", "title", "title", "price", "price");

  @Test
  void resolvesOnlyAllowListedFieldAndStableTieBreaker() {
    SortSpec sort = SortSpec.parse("title,asc", ALLOWED, "createdAt", SortDirection.DESC, "id");

    assertThat(sort.sqlOrder()).isEqualTo("title asc,id asc");
  }

  @Test
  void acceptsCaseInsensitiveDirection() {
    SortSpec sort = SortSpec.parse("price,DESC", ALLOWED, "createdAt", SortDirection.DESC, "id");

    assertThat(sort.sqlOrder()).isEqualTo("price desc,id desc");
  }

  @Test
  void usesExplicitDefaultWhenSortIsMissing() {
    SortSpec sort = SortSpec.parse(null, ALLOWED, "createdAt", SortDirection.DESC, "id");

    assertThat(sort.sqlOrder()).isEqualTo("created_at desc,id desc");
  }

  @Test
  void rejectsUnsupportedFieldDirectionAndMalformedInput() {
    assertThatThrownBy(
            () -> SortSpec.parse("dropTable,asc", ALLOWED, "createdAt", SortDirection.DESC, "id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported sort field: dropTable");
    assertThatThrownBy(
            () -> SortSpec.parse("title,sideways", ALLOWED, "createdAt", SortDirection.DESC, "id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported sort direction: sideways");
    assertThatThrownBy(
            () -> SortSpec.parse("title,asc,extra", ALLOWED, "createdAt", SortDirection.DESC, "id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Sort must use field,direction");
  }
}

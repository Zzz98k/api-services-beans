/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class TerraformVersionTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMinVersion0151() {
    TerraformVersion version = TerraformVersion.create(0, 15, 1);

    assertThat(version.minVersion(0, 7)).isTrue();
    assertThat(version.minVersion(0, 15)).isTrue();
    assertThat(version.minVersion(0, 16)).isFalse();
    assertThat(version.minVersion(1, 1)).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMinVersion111() {
    TerraformVersion version = TerraformVersion.create(1, 1, 1);
    assertThat(version.minVersion(0, 7)).isTrue();
    assertThat(version.minVersion(0, 15)).isTrue();
    assertThat(version.minVersion(1, 1)).isTrue();
    assertThat(version.minVersion(1, 2)).isFalse();
    assertThat(version.minVersion(2, 1)).isFalse();
  }
}
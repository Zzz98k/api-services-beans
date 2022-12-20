/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Data;

@OwnedBy(CDC)
@Data
public class JiraFieldSchema {
  @NotNull String type;

  public JiraFieldSchema(JiraFieldSchemaNG jiraFieldSchemaNG) {
    this.type = jiraFieldSchemaNG.getTypeStr();
    if (jiraFieldSchemaNG.isArray()) {
      this.type = "array";
    }
  }
}

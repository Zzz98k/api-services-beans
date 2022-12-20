/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class JiraInternalConfig {
  String jiraUrl;
  String username;
  @ToString.Exclude String password;

  public String getJiraUrl() {
    return jiraUrl.endsWith("/") ? jiraUrl : jiraUrl.concat("/");
  }
}

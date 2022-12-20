/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import io.harness.exception.InvalidArgumentsException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.deserializer.JiraUserDataDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraUserDataDeserializer.class)
public class JiraUserData {
  private String accountId;
  private String name;
  private String displayName;
  private String emailAddress;
  private boolean active;

  public JiraUserData(JsonNode node) {
    try {
      this.accountId = JsonNodeUtils.mustGetString(node, "accountId");
    } catch (InvalidArgumentsException ex) {
      this.accountId = JsonNodeUtils.mustGetString(node, "key");
      this.name = JsonNodeUtils.mustGetString(node, "name");
    }
    this.displayName = JsonNodeUtils.mustGetString(node, "displayName");
    this.active = JsonNodeUtils.mustGetBoolean(node, "active");
    this.emailAddress = JsonNodeUtils.mustGetString(node, "emailAddress");
  }

  public JiraUserData(String accountId, String displayName, boolean active, String emailAddress) {
    this.accountId = accountId;
    this.displayName = displayName;
    this.emailAddress = emailAddress;
    this.active = active;
  }
}

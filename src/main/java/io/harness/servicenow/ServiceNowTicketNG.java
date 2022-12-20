/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;
import io.harness.servicenow.deserializer.ServiceNowTicketDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ServiceNowTicketDeserializer.class)
public class ServiceNowTicketNG implements TicketNG {
  @NotNull String url;
  @NotNull String number;
  @NotNull Map<String, ServiceNowFieldValueNG> fields = new HashMap<>();

  public ServiceNowTicketNG(JsonNode node) {
    this.url = JsonNodeUtils.mustGetString(node, "self");
    this.number = JsonNodeUtils.mustGetString(node, "number");
    this.fields.put("url", ServiceNowFieldValueNG.builder().value(this.url).build());
    this.fields.put("number", ServiceNowFieldValueNG.builder().value(this.number).build());

    Map<String, JsonNode> names = JsonNodeUtils.getMap(node, "names");
    Map<String, JsonNode> schema = JsonNodeUtils.getMap(node, "schema");
    Map<String, JsonNode> fieldValues = JsonNodeUtils.getMap(node, "fields");
  }
}

/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CommitAndPushRequest extends GitBaseRequest {
  private String lastProcessedGitCommit;
  private boolean pushOnlyIfHeadSeen;
  private List<GitFileChange> gitFileChanges;
  private boolean forcePush;
  private String commitMessage;
  private String authorName;
  private String authorEmail;
}

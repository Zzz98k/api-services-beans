/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AmazonClientSDKDefaultBackoffStrategy {
  @Builder.Default private int baseDelayInMs = 100;
  @Builder.Default private int throttledBaseDelayInMs = 500;
  @Builder.Default private int maxBackoffInMs = 20000;
  @Builder.Default private int maxErrorRetry = 5;
}

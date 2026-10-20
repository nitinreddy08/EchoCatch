// SPDX-License-Identifier: GPL-3.0-only
#include "echocatch/pipeline.h"

#include <gtest/gtest.h>

#include <cstring>

TEST(PipelineSkeleton, ReportsAVersion) {
  const char* version = echocatch::pipeline_version();
  ASSERT_NE(version, nullptr);
  EXPECT_GT(std::strlen(version), 0u);
}

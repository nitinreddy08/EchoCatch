# SPDX-License-Identifier: GPL-3.0-only
add_executable(pipeline_tests pipeline_test.cc)
target_link_libraries(pipeline_tests PRIVATE echocatch_pipeline GTest::gtest_main)

include(GoogleTest)
gtest_discover_tests(pipeline_tests)

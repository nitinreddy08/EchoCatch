# SPDX-License-Identifier: GPL-3.0-only
#
# Host benchmark CLI. In M2 it runs the pipeline over the corpus and prints accuracy, latency and CPU metrics
# (docs/plan/06-quality-and-testing.md). For now it only proves the host build works.
add_executable(echocatch_bench main.cc)
target_link_libraries(echocatch_bench PRIVATE echocatch_pipeline)

// SPDX-License-Identifier: GPL-3.0-only
#include "echocatch/pipeline.h"

#include <cstdio>
#include <cstring>

int main(int argc, char** argv) {
  if (argc == 2 && std::strcmp(argv[1], "--version") == 0) {
    std::printf("echocatch-bench %s\n", echocatch::pipeline_version());
    return 0;
  }
  std::fprintf(stderr, "usage: echocatch_bench --version\n(corpus benchmarking arrives in milestone M2)\n");
  return 2;
}

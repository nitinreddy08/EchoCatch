# SPDX-License-Identifier: GPL-3.0-only
#
# The native audio/detection core. It has NO Android dependencies (ADR-0006), so it builds on a Linux host
# for tests and the benchmark, and for Android ABIs through Gradle.
cmake_minimum_required(VERSION 3.22)

if(NOT TARGET echocatch_pipeline)
  add_library(echocatch_pipeline STATIC
    src/pipeline.cc
  )
  target_include_directories(echocatch_pipeline PUBLIC include)
  target_compile_features(echocatch_pipeline PUBLIC cxx_std_17)

  if(NOT MSVC)
    target_compile_options(echocatch_pipeline PRIVATE -Wall -Wextra -Wpedantic -Wshadow -Wconversion)
    if(ECHOCATCH_WERROR)
      target_compile_options(echocatch_pipeline PRIVATE -Werror)
    endif()
    if(ECHOCATCH_SANITIZE)
      target_compile_options(echocatch_pipeline PUBLIC -fsanitize=address,undefined -fno-omit-frame-pointer)
      target_link_options(echocatch_pipeline PUBLIC -fsanitize=address,undefined)
    endif()
  endif()
endif()

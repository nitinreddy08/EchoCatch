# SPDX-License-Identifier: GPL-3.0-only
#
# Host build of the native core, the benchmark tool and the tests.
# The Android build consumes native/pipeline/CMakeLists.txt directly through Gradle (see docs/plan/03-architecture.md).
cmake_minimum_required(VERSION 3.22)
project(EchoCatch LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

option(ECHOCATCH_WERROR "Treat warnings as errors" ON)
option(ECHOCATCH_SANITIZE "Build with AddressSanitizer and UBSan" OFF)

add_subdirectory(native/pipeline)

if(NOT ANDROID)
  enable_testing()
  add_subdirectory(tools/bench)

  include(FetchContent)
  set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
  set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
  FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG v1.15.2  # BSD-3-Clause, see docs/third-party.md
  )
  FetchContent_MakeAvailable(googletest)
  add_subdirectory(native/pipeline/tests)

  find_package(Python3 REQUIRED COMPONENTS Interpreter)
  add_test(
    NAME ci_scripts
    COMMAND ${Python3_EXECUTABLE} -I -m unittest discover -s ${CMAKE_SOURCE_DIR}/tools/ci/tests -v
  )
  add_test(
    NAME native_core_has_no_android_dependencies
    COMMAND ${Python3_EXECUTABLE} -I ${CMAKE_SOURCE_DIR}/tools/ci/check_native_purity.py ${CMAKE_SOURCE_DIR}/native/pipeline
  )
endif()

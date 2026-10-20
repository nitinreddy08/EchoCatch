// SPDX-License-Identifier: GPL-3.0-only
#pragma once

namespace echocatch {

// Version of the native core. Real pipeline components (ring buffer, resampler, VAD, keyword stages)
// arrive in milestone M2 (docs/plan/05-roadmap.md). This header exists so the build, tests, and CI
// can be proven before then.
const char* pipeline_version() noexcept;

}  // namespace echocatch

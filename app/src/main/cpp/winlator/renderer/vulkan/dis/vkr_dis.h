/* SPDX-License-Identifier: GPL-3.0-or-later
 *
 * DIS optical flow frame generation engine.
 *
 * Ported from WinNative (https://github.com/WinNative-Emu/WinNative),
 * file app/src/main/cpp/winlator/vk/dis/vkr_dis.h.
 *
 * Copyright (C) qwertypower / DEVAR Entertainment LLC (https://devar.ai/)
 * The DIS algorithm derives from OpenCV DISOpticalFlow, which adopted
 * Till Kroeger's OF_DIS (https://github.com/tikroeger/OF_DIS).
 *
 * This file is distributed under GPL-3.0-or-later, matching WinNative.
 */
#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "../vk_dispatch.h"

#ifdef __cplusplus
extern "C" {
#endif

#define VKR_DIS_MAX_GENERATIONS 3u

typedef struct VkrDis VkrDis;

typedef struct VkrDisContentRect {
    int32_t x;
    int32_t y;
    uint32_t width;
    uint32_t height;
} VkrDisContentRect;

VkrDis* vkr_dis_create(VkDevice device, VkPhysicalDevice physical_device);
void vkr_dis_destroy(VkrDis* dis);

void vkr_dis_configure(VkrDis* dis, uint32_t flow_min_side, uint32_t target_fps,
                       float refresh_rate);

void vkr_dis_set_debug_flow(VkrDis* dis, bool debug_flow);

/* Interpolation intensity in [0,1].  1.0 keeps the pure optical-flow interpolation;
 * lower values bleed the nearest real frame in, which is how ghosting on disoccluded
 * / fast-moving regions gets traded for a little temporal smoothing. */
void vkr_dis_set_strength(VkrDis* dis, float strength);

/* Ghosting suppression in [0,1].  Fraction of `strength` that survives where the adaptive
 * untrustworthy-interpolation signal maxes out: 1.0 disables adaptive suppression entirely,
 * 0.0 collapses the generated frame onto the real frame wherever motion is large or the two
 * warped samples disagree. */
void vkr_dis_set_motion_floor(VkrDis* dis, float motion_floor);

/* ---------------------------------------------------------------------------------------------
 * Full frame-generation configuration, mirroring the surface the Bionic (NeoMirror) build
 * exposes.  Each setter is independent and may be called at any time; the ones that change
 * resource sizes take effect on the next vkr_dis_prepare().
 * -------------------------------------------------------------------------------------------*/

/* 0 = performance, 1 = stable (fixed target rate), 2 = quality.  Selects the flow resolution and
 * the variational-refinement budget. */
void vkr_dis_set_quality_mode(VkrDis* dis, uint32_t mode);

/* Optical-flow working resolution, as the minimum side of the flow pyramid in pixels. */
void vkr_dis_set_flow_scale(VkrDis* dis, uint32_t flow_min_side);

/* Resolution the frame generation actually runs at, as a fraction of the output.  < 1 trades
 * generated-frame sharpness for a large speedup, which is how the feature stays affordable. */
void vkr_dis_set_render_scale(VkrDis* dis, float scale);

/* Desired output rate.  0 follows the panel refresh rate. */
void vkr_dis_set_target_fps(VkrDis* dis, uint32_t fps);

/* Do not generate at all while the measured source rate is below this.  0 disables the floor. */
void vkr_dis_set_floor_fps(VkrDis* dis, uint32_t fps);

/* Hard cap on the output multiplier (how many frames are shown per real frame). */
void vkr_dis_set_max_mult(VkrDis* dis, uint32_t mult);

/* How many frames to insert per real frame when pacing is not rate-driven. */
void vkr_dis_set_generated_frames(VkrDis* dis, uint32_t count);

/* Sharpen generated frames to offset the softening inherent to interpolation. */
void vkr_dis_set_post_process(VkrDis* dis, bool enabled);

/* Repair unreliable optical flow before interpolating.  This is what removes the trailing on
 * fast motion, where the flow estimator reports a wrong (too small) vector. */
void vkr_dis_set_artifact_clean(VkrDis* dis, bool enabled);

/* Skip the expensive refinement stages and run a reduced pipeline. */
void vkr_dis_set_perf_mode(VkrDis* dis, bool enabled);

/* Use 16-bit intermediates where the device supports them. */
void vkr_dis_set_fp16(VkrDis* dis, bool enabled);

/* Colourise the repaired flow for debugging. */
void vkr_dis_set_heatmap(VkrDis* dis, bool enabled);

/* Pacing: `carry_pct` is how much of an unused generation budget is carried to the next frame
 * (0..100), `cz_max` caps a single burst. */
void vkr_dis_set_pacing(VkrDis* dis, uint32_t carry_pct, uint32_t cz_max);

/* True once frame generation has actually produced output for the current configuration. */
bool vkr_dis_is_active(const VkrDis* dis);

/* Log per-stage timings accumulated since the last call. */
void vkr_dis_log_stage_times(VkrDis* dis);

bool vkr_dis_needs_rebuild(const VkrDis* dis, uint32_t width, uint32_t height,
                           VkFormat format, VkrDisContentRect content);

bool vkr_dis_prepare(VkrDis* dis, uint32_t width, uint32_t height, VkFormat format,
                     VkrDisContentRect content);

uint32_t vkr_dis_plan(VkrDis* dis, uint32_t capacity, uint64_t source_frames);

void vkr_dis_process(VkrDis* dis, VkCommandBuffer cmd, VkImage source,
                     uint32_t width, uint32_t height, uint32_t generations);

/* Batch API used by the NeoMirror presenter.  Sources are consumed in order; on the current
 * command-buffer based backend this records the equivalent sequence of process calls. */
void vkr_dis_process_batch(VkrDis* dis, VkCommandBuffer cmd, const VkImage* sources,
                           uint32_t source_count, uint32_t width, uint32_t height,
                           uint32_t generations);

/* The renderer records all work into the caller's command buffer, so there is no private worker
 * fence to wait on.  Keep the API for presenter parity; it is intentionally a no-op today. */
void vkr_dis_wait(VkrDis* dis);

void vkr_dis_generate_into(VkrDis* dis, VkCommandBuffer cmd, uint32_t generation,
                           uint32_t target_index, VkImage target_image,
                           VkImageView target_view, uint32_t width, uint32_t height,
                           VkImage base_image);

void vkr_dis_debug_into(VkrDis* dis, VkCommandBuffer cmd, VkImage target_image,
                        uint32_t width, uint32_t height);

void vkr_dis_forget_targets(VkrDis* dis);

void vkr_dis_reset(VkrDis* dis);

#ifdef __cplusplus
}
#endif

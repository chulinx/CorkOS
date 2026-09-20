cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(OpenXR-SDK)
add_subdirectory(patchelf)
add_subdirectory(adrenotools)

# Add Winlator shared library
add_library(winlator SHARED
        xr/engine.c
        xr/framebuffer.c
        xr/input.c
        xr/main.c
        xr/math.c
        xr/renderer.c
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/process_helper.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/vulkan.c
        winlator/renderer/egl.cpp
        winlator/renderer/renderer_jni.cpp
        winlator/renderer/shader.cpp
        winlator/renderer/window.cpp
        winlator/renderer/cursor.cpp
        winlator/renderer/xform.cpp
        winlator/renderer/view_transformation.cpp
        winlator/renderer/displayx.cpp
        winlator/renderer/effectcomposer/effect_composer.cpp
)

target_compile_options(winlator PRIVATE -Wall -Wextra)
target_include_directories(winlator PRIVATE winlator/renderer winlator/renderer/effectcomposer)

target_link_libraries(winlator
        log
        android
        jnigraphics
        openxr_loader
        aaudio
        EGL
        GLESv2
        GLESv3
        adrenotools
        vulkan
)

# Add Fake Input library for controller emulation (loaded via LD_PRELOAD)
add_library(fakeinput SHARED
        winlator/fakeinput.cpp
)

target_compile_options(fakeinput PRIVATE -Wall -Wextra -fvisibility=hidden)

target_link_libraries(fakeinput
        log
        dl
)

# Keep the Pypetto DisplayX/EGL target above untouched.  The optional Vulkan
# renderer is a separate JNI library so selecting it cannot change the native
# presentation path used by DisplayX or EGL.
set(VULKAN_RENDERER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/winlator/renderer/vulkan)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
    COMMAND glslangValidator -V --vn window_vert_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
            ${VULKAN_RENDERER_DIR}/window.vert
    DEPENDS ${VULKAN_RENDERER_DIR}/window.vert
    COMMENT "Compiling window.vert -> window_vert.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
    COMMAND glslangValidator -V --vn window_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
            ${VULKAN_RENDERER_DIR}/window.frag
    DEPENDS ${VULKAN_RENDERER_DIR}/window.frag
    COMMENT "Compiling window.frag -> window_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_sgsr_frag.h
    COMMAND glslangValidator -V --vn window_sgsr_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_sgsr_frag.h
            ${VULKAN_RENDERER_DIR}/window_sgsr.frag
    DEPENDS ${VULKAN_RENDERER_DIR}/window_sgsr.frag
    COMMENT "Compiling window_sgsr.frag -> window_sgsr_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_fsr1_frag.h
    COMMAND glslangValidator -V --vn window_fsr1_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_fsr1_frag.h
            ${VULKAN_RENDERER_DIR}/window_fsr1.frag
    DEPENDS ${VULKAN_RENDERER_DIR}/window_fsr1.frag
    COMMENT "Compiling single-pass FSR -> window_fsr1_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_lanczos_frag.h
    COMMAND glslangValidator -V --vn window_lanczos_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_lanczos_frag.h
            ${VULKAN_RENDERER_DIR}/window_lanczos.frag
    DEPENDS ${VULKAN_RENDERER_DIR}/window_lanczos.frag
    COMMENT "Compiling Lanczos 2 -> window_lanczos_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_stretch_frag.h
    COMMAND glslangValidator -V --vn window_stretch_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_stretch_frag.h
            ${VULKAN_RENDERER_DIR}/window_stretch.frag
    DEPENDS ${VULKAN_RENDERER_DIR}/window_stretch.frag
    COMMENT "Compiling window_stretch.frag -> window_stretch_frag.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_postfx_frag.h
    COMMAND glslangValidator -V --vn window_postfx_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_postfx_frag.h
            ${VULKAN_RENDERER_DIR}/window_postfx.frag
    DEPENDS ${VULKAN_RENDERER_DIR}/window_postfx.frag
    COMMENT "Compiling window_postfx.frag -> window_postfx_frag.h"
)

# DIS frame generation (optical flow interpolation), ported from WinNative
# (GPL-3.0-or-later).  The compute shaders are compiled to SPIR-V and then embedded as
# C arrays, because vkr_dis.c references each shader as <name> and <name>_size and
# glslangValidator's -x only emits the array.
set(DIS_DIR ${VULKAN_RENDERER_DIR}/dis)
set(DIS_SHADERS
        dis_gradient
        dis_inverse_search
        dis_propagate
        dis_densify
        dis_interpolate
        dis_vr_prep
        dis_vr_d1
        dis_vr_d2
        dis_vr_w
        dis_vr_coef
        dis_vr_sor
        dis_vr_add
        dis_luma_r16
        dis_luma_r32
)
set(DIS_SPV_HEADERS "")
foreach(DIS_SHADER ${DIS_SHADERS})
    set(DIS_SPV ${CMAKE_CURRENT_BINARY_DIR}/shaders/${DIS_SHADER}_comp.spv)
    set(DIS_SPV_H ${CMAKE_CURRENT_BINARY_DIR}/shaders/${DIS_SHADER}_comp.spv.h)
    add_custom_command(
            OUTPUT ${DIS_SPV}
            COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/shaders
            COMMAND glslangValidator -V -o ${DIS_SPV} ${DIS_DIR}/shaders/${DIS_SHADER}.comp
            DEPENDS ${DIS_DIR}/shaders/${DIS_SHADER}.comp
            COMMENT "Compiling ${DIS_SHADER}.comp -> SPIR-V"
    )
    add_custom_command(
            OUTPUT ${DIS_SPV_H}
            COMMAND python3 ${DIS_DIR}/bin2c.py ${DIS_SPV} ${DIS_SPV_H} ${DIS_SHADER}_comp
            DEPENDS ${DIS_SPV} ${DIS_DIR}/bin2c.py
            COMMENT "Embedding ${DIS_SHADER}_comp.spv -> header"
    )
    list(APPEND DIS_SPV_HEADERS ${DIS_SPV_H})
endforeach()

set(VULKAN_CONTEXT_SOURCE ${VULKAN_RENDERER_DIR}/VulkanRendererContext.cpp)
set(VULKAN_CONTEXT_LEGACY ${CMAKE_CURRENT_BINARY_DIR}/VulkanRendererContext_legacy.cpp)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${VULKAN_CONTEXT_SOURCE})
file(READ ${VULKAN_CONTEXT_SOURCE} VULKAN_CONTEXT_TEXT)

set(PIPELINE_OPT_INCLUDE_MARKER "#include <dlfcn.h>\n")
string(FIND "${VULKAN_CONTEXT_TEXT}" "${PIPELINE_OPT_INCLUDE_MARKER}" PIPELINE_OPT_INCLUDE_POS)
if(PIPELINE_OPT_INCLUDE_POS EQUAL -1)
    message(FATAL_ERROR "VulkanRendererContext.cpp changed: pipeline-opt include marker not found")
endif()
string(REPLACE
    "${PIPELINE_OPT_INCLUDE_MARKER}"
    "#include <dlfcn.h>\n#include \"VulkanRendererPipelineOpt.h\"\n"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")

set(PIPELINE_OPT_DESTROY_MARKER "    vk_.DeviceWaitIdle(device);\n    for (auto& [id, wt] : texMap) destroyWinTex(wt);")
string(FIND "${VULKAN_CONTEXT_TEXT}" "${PIPELINE_OPT_DESTROY_MARKER}" PIPELINE_OPT_DESTROY_POS)
if(PIPELINE_OPT_DESTROY_POS EQUAL -1)
    message(FATAL_ERROR "VulkanRendererContext.cpp changed: pipeline-opt destroy marker not found")
endif()
string(REPLACE
    "${PIPELINE_OPT_DESTROY_MARKER}"
    "    vk_.DeviceWaitIdle(device);\n    VulkanRendererDestroyOpaquePipeline(this);\n    for (auto& [id, wt] : texMap) destroyWinTex(wt);"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")

set(LANCZOS_DESTROY_MARKER "    if (fsr1Pipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, fsr1Pipeline, nullptr);\n")
string(FIND "${VULKAN_CONTEXT_TEXT}" "${LANCZOS_DESTROY_MARKER}" LANCZOS_DESTROY_POS)
if(LANCZOS_DESTROY_POS EQUAL -1)
    message(FATAL_ERROR "VulkanRendererContext.cpp changed: Lanczos destroy marker not found")
endif()
string(REPLACE
    "${LANCZOS_DESTROY_MARKER}"
    "    if (fsr1Pipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, fsr1Pipeline, nullptr);\n    if (lanczosPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, lanczosPipeline, nullptr);\n"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")

set(RECORD_CMD_MARKER "void VulkanRendererContext::recordCmdBuf(VkCommandBuffer cb, uint32_t imgIdx,")
set(RENDER_LOOP_MARKER "\nvoid VulkanRendererContext::renderLoop() {")
string(FIND "${VULKAN_CONTEXT_TEXT}" "${RECORD_CMD_MARKER}" RECORD_CMD_POS)
string(FIND "${VULKAN_CONTEXT_TEXT}" "${RENDER_LOOP_MARKER}" RENDER_LOOP_POS)
if(RECORD_CMD_POS EQUAL -1 OR RENDER_LOOP_POS EQUAL -1)
    message(FATAL_ERROR "VulkanRendererContext.cpp changed: unable to isolate legacy recordCmdBuf")
endif()
string(REPLACE
    "${RECORD_CMD_MARKER}"
    "#if 0\n${RECORD_CMD_MARKER}"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")
string(REPLACE
    "${RENDER_LOOP_MARKER}"
    "\n#endif\n${RENDER_LOOP_MARKER}"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")

set(FILTER_CREATE_MARKER "    if (mode == 3 && fsr1Pipeline == VK_NULL_HANDLE) createFsr1Pipeline();\n")
string(FIND "${VULKAN_CONTEXT_TEXT}" "${FILTER_CREATE_MARKER}" FILTER_CREATE_POS)
if(FILTER_CREATE_POS EQUAL -1)
    message(FATAL_ERROR "VulkanRendererContext.cpp changed: filter creation marker not found")
endif()
string(REPLACE
    "${FILTER_CREATE_MARKER}"
    "    if (mode == 3 && fsr1Pipeline == VK_NULL_HANDLE) createFsr1Pipeline();\n    if (mode == 4 && lanczosPipeline == VK_NULL_HANDLE) createLanczosPipeline();\n"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")

string(REPLACE
    "auto modeName=[](int m){ return m==1?\"NEAREST\":m==2?\"SGSR\":m==3?\"FSR1\":\"LINEAR\"; };"
    "auto modeName=[](int m){ return m==1?\"NEAREST\":m==2?\"SGSR\":m==3?\"FSR\":m==4?\"LANCZOS2\":m==5?\"COLOR_BOOST\":\"LINEAR\"; };"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")
string(REPLACE
    "filterMode, filterMode==3?\"FSR1\":filterMode==2?\"SGSR\":filterMode==1?\"NEAREST\":\"LINEAR\");"
    "filterMode, filterMode==5?\"COLOR_BOOST\":filterMode==4?\"LANCZOS2\":filterMode==3?\"FSR\":filterMode==2?\"SGSR\":filterMode==1?\"NEAREST\":\"LINEAR\");"
    VULKAN_CONTEXT_TEXT
    "${VULKAN_CONTEXT_TEXT}")

file(WRITE ${VULKAN_CONTEXT_LEGACY} "${VULKAN_CONTEXT_TEXT}")

include_directories(
        ${CMAKE_CURRENT_BINARY_DIR}
        ${VULKAN_RENDERER_DIR}
)

# The DIS engine and its dispatch table use Android-specific Vulkan types
# (VkAndroidSurfaceCreateInfoKHR, AHardwareBuffer interop).  Those only appear in
# vulkan.h when VK_USE_PLATFORM_ANDROID_KHR is set, and CorkOS's own sources do not
# need it -- so scope the define to just these two files.
set_source_files_properties(
        winlator/renderer/vulkan/vk_dispatch.c
        winlator/renderer/vulkan/dis/vkr_dis.c
        PROPERTIES COMPILE_DEFINITIONS "VK_USE_PLATFORM_ANDROID_KHR=1"
)

add_library(vulkan_renderer SHARED
        winlator/renderer/vulkan/vulkan_jni.cpp
        ${VULKAN_CONTEXT_LEGACY}
        winlator/renderer/vulkan/VulkanRendererFilters.cpp
        winlator/renderer/vulkan/VulkanRendererPipelineOpt.cpp

        winlator/renderer/vulkan/view_transformation.cpp
        winlator/renderer/vulkan/xform.cpp

        ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_sgsr_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_fsr1_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_lanczos_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_stretch_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_postfx_frag.h

        winlator/renderer/vulkan/vk_dispatch.c
        winlator/renderer/vulkan/dis/vkr_dis.c
        ${DIS_SPV_HEADERS}
)

set_target_properties(vulkan_renderer PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden
)

target_link_libraries(vulkan_renderer
        log
        android
        vulkan
        adrenotools
        dl
        atomic
)

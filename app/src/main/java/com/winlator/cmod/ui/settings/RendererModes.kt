package com.winlator.cmod.ui.settings

/**
 * Renderer mode labels shared by the container editor and the shortcut editor.
 *
 * Vulkan and OpenGL each split into a DRI3 / Present FLIP pass-through variant ("直通模式") and a
 * composited fallback ("兼容模式"), matching the five-way renderer selector. These are the displayed
 * strings as well as the editor state values, so they must stay identical across both editors —
 * otherwise a mode saved by one would not be recognised by the other.
 */
const val RENDERER_MODE_DISPLAYX = "DisplayX"
const val RENDERER_MODE_VULKAN_COMPAT = "Vulkan 兼容模式"
const val RENDERER_MODE_VULKAN_DIRECT = "Vulkan 直通模式 (DRI3)"
const val RENDERER_MODE_GL_COMPAT = "OpenGL 兼容模式"
const val RENDERER_MODE_GL_DIRECT = "OpenGL 直通模式"

val RENDERER_MODES: List<String> = listOf(
    RENDERER_MODE_VULKAN_COMPAT,
    RENDERER_MODE_VULKAN_DIRECT,
    RENDERER_MODE_DISPLAYX,
    RENDERER_MODE_GL_COMPAT,
    RENDERER_MODE_GL_DIRECT
)

/** True for the two OpenGL(EGL) variants. */
fun isOpenGLRendererMode(mode: String): Boolean =
    mode == RENDERER_MODE_GL_COMPAT || mode == RENDERER_MODE_GL_DIRECT

/** "直通模式 (DRI3)" enables DRI3 present pass-through; the rest fall back to compositing. */
fun isDri3RendererMode(mode: String): Boolean = mode.contains("直通")

/** Backend id stored in the container / shortcut: "Vulkan" | "EGL" | "DisplayX". */
fun rendererBackendOf(mode: String): String = when {
    mode == RENDERER_MODE_DISPLAYX -> "DisplayX"
    isOpenGLRendererMode(mode) -> "EGL"
    else -> "Vulkan"
}

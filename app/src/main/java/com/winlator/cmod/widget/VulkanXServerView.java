package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pixmap;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dalvik.annotation.optimization.FastNative;

@SuppressLint("ViewConstructor")
public class VulkanXServerView extends XServerRendererView implements SurfaceHolder.Callback,
        WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener {

    private long nativeHandle = 0;
    private final Object lock = new Object();
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
    private final Set<Integer> compositeRedirectedWindows = ConcurrentHashMap.newKeySet();

    private boolean fullscreen = false;
    private float magnifierZoom = 1.0f;
    private boolean screenOffsetYRelativeToCursor = false;
    public int surfaceWidth;
    public int surfaceHeight;
    private String[] unviewableWMClasses = null;
    private boolean cursorVisible = false;
    private String driverPath = null;
    private ExecutorService initExecutor = null;
    private volatile boolean initComplete = false;
    private volatile boolean inPipMode = false;
    private String driverLibraryName = null;
    private String nativeLibDir = null;
    private Drawable rootCursorDrawable;
    private Cursor lastCursor = null;
    private static volatile boolean gpuImageChecked = false;

    private int     pendingPresentMode    = 2;
    private int     pendingStretchMode    = 0;
    private int     pendingFilterMode     = 0;
    private int     pendingPostFXMode     = 0;
    private float   pendingSharpness      = 0.5f;
    private boolean pendingSwapRB         = false;

    private WinlatorHUD hudRef = null;
    private FrameRating classicHudRef = null;
    private volatile int fpsWindowId = -1;
    private volatile int fpsLimit = 0;

    private static volatile boolean nativeLibLoaded = false;

    public static synchronized void loadNativeLibrary() {
        if (!nativeLibLoaded) {
            System.loadLibrary("vulkan_renderer");
            nativeLibLoaded = true;
        }
    }

    public VulkanXServerView(Context context, XServer xServer) {
        super(context, xServer);
        setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        getHolder().addCallback(this);

        rootCursorDrawable = createRootCursorDrawable();
        xServer.setXServerView(this);
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
    }

    private Drawable createRootCursorDrawable() {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.cursor, options);
            return Drawable.fromBitmap(bitmap);
        } catch (Exception e) { return null; }
    }

    private native long nativeInit(Surface surface,
                                   int screenWidth, int screenHeight,
                                   String driverPath, String libraryName, String nativeLibDir);
    private native void nativeResize(long handle, int width, int height);
    private native void nativeDestroy(long handle);
    private native void nativeDetachSurface(long handle);
    private native boolean nativeReattachSurface(long handle, Surface surface);
    private native int[] nativeGetSwapchainSize(long handle);

    @FastNative private native void nativeUpdateWindowContent(long handle, long id,
        ByteBuffer pixels, short width, short height, short stride, int x, int y);
    @FastNative private native void nativeUpdateWindowContentAHB(long handle, long id,
        long ahbPtr, short width, short height, int x, int y);
    @FastNative private native void nativeSetTransformAndScissor(long handle,
        float ox, float oy, float sx, float sy,
        boolean hasScissor, int scX, int scY, int scW, int scH);
    @FastNative private native void nativeSetPointerPos(long handle, short x, short y);
    @FastNative private native void nativeSetCursorVisible(long handle, boolean visible);
    @FastNative private native void nativeUpdateCursorImage(long handle,
        ByteBuffer pixels, short width, short height, short stride, short hotX, short hotY);
    @FastNative private native void nativeRemoveWindow(long handle, long id);
    @FastNative private native void nativeSetVerboseLog(long handle, boolean v);
    @FastNative private native void nativeSetSharpness(long handle, float sharpness);

    private native void nativeDumpRendererInfo(long handle);
    private native void nativeSetFilterMode(long handle, int mode);
    private native void nativeSetStretchMode(long handle, int mode);
    private native void nativeSetPostFXMode(long handle, int mode);

    // Frame generation (DIS optical flow)
    private native void nativeSetFrameGenEnabled(long handle, boolean enabled);
    private native void nativeSetFrameGenConfig(long handle, int flowMinSide, int targetFps, float refreshRate);
    private native void nativeSetFrameGenDebugFlow(long handle, boolean on);
    /** strength in [0,1]: 1 = pure interpolation, lower = less ghosting / less smoothing */
    private native void nativeSetFrameGenStrength(long handle, float strength);
    /** motionFloor in [0,1]: fraction of strength surviving where motion/unreliability maxes out */
    private native void nativeSetFrameGenMotionFloor(long handle, float motionFloor);

    // Full frame-generation configuration (mirrors the Bionic build's surface).
    private native void nativeSetFGQualityMode(long handle, int mode);
    private native void nativeSetFGFlowScale(long handle, int scale);
    private native void nativeSetFGRenderScale(long handle, float scale);
    private native void nativeSetFGTargetFps(long handle, int fps);
    private native void nativeSetFGFloorFps(long handle, int fps);
    private native void nativeSetFGMaxMult(long handle, int mult);
    private native void nativeSetFGGeneratedFrameCount(long handle, int count);
    private native void nativeSetFGPostProcess(long handle, boolean on);
    private native void nativeSetFGArtifactClean(long handle, boolean on);
    private native void nativeSetFGPerfMode(long handle, boolean on);
    private native void nativeSetFGFp16(long handle, boolean on);
    private native void nativeSetFGHeatmap(long handle, boolean on);
    private native void nativeSetFGCzCarryPct(long handle, int pct);
    private native void nativeSetFGCzMax(long handle, int czMax);
    private native boolean nativeIsFGActive(long handle);
    private native void nativeLogFGStageTimes(long handle);
    /** {realFrames, generatedFrames, droppedGenerations} */
    private native long[] nativeGetFrameGenCounts(long handle);
    private native void nativeSetFrameGenMultiplier(long handle, int mult);
    private native void nativeSetSwapRB(long handle, boolean enabled);
    private native void nativeSetPresentMode(long handle, int mode);
    private native int[] nativeGetSupportedPresentModes(long handle);

    private native void nativeInitRootWindow(long handle, long rootId, long contentId, int width, int height);
    private native void nativeCreateWindow(long handle, long id, long parentId, long contentId,
                                           int x, int y, int width, int height);
    private native void nativeDestroyWindow(long handle, long id);
    private native void nativeMapWindow(long handle, long id);
    private native void nativeUnmapWindow(long handle, long id);
    private native void nativeReparentWindow(long handle, long id, long newParentId);
    private native void nativeUpdateWindowGeometry(long handle, long id, long contentId,
                                                   int x, int y, int width, int height);
    private native void nativeSyncChildOrder(long handle, long parentId, long[] orderedChildIds, int count);
    private native void nativeSetWindowViewable(long handle, long id, boolean viewable);

    private static long did(Drawable d) {
        return (long) System.identityHashCode(d);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        onSurfaceCreated(holder.getSurface());
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        onSurfaceChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        onSurfaceDestroyed();
    }

    public void onSurfaceCreated(Surface surface) {
        if (!gpuImageChecked) { GPUImage.checkIsSupported(); gpuImageChecked = true; }
        if (initExecutor != null) {
            initExecutor.shutdownNow();
            try { initExecutor.awaitTermination(3, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        initExecutor = Executors.newSingleThreadExecutor();

        final boolean isColdInit = (nativeHandle == 0);

        initExecutor.execute(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) {
                    boolean ok = nativeReattachSurface(nativeHandle, surface);
                    if (!ok) {
                        nativeDestroy(nativeHandle);
                        nativeHandle = 0;
                    } else {

                        nativeSetPresentMode(nativeHandle, pendingPresentMode);
                        nativeSetFilterMode(nativeHandle, pendingFilterMode);
                        nativeSetSwapRB(nativeHandle, pendingSwapRB);
                        nativeSetStretchMode(nativeHandle, pendingStretchMode);
                        nativeSetPostFXMode(nativeHandle, pendingPostFXMode);
                        nativeSetSharpness(nativeHandle, pendingSharpness);
                        updateTransform();
                        nativeSetCursorVisible(nativeHandle, cursorVisible);
                        initComplete = true;
                        return;
                    }
                }

                nativeHandle = nativeInit(
                    surface,
                    xServer.screenInfo.width, xServer.screenInfo.height,
                    driverPath, driverLibraryName, nativeLibDir);

                if (nativeHandle != 0) {
                    nativeSetPresentMode(nativeHandle, pendingPresentMode);
                    nativeSetFilterMode(nativeHandle, pendingFilterMode);
                    nativeSetSwapRB(nativeHandle, pendingSwapRB);
                    nativeSetPostFXMode(nativeHandle, pendingPostFXMode);
                    nativeSetSharpness(nativeHandle, pendingSharpness);
                    updateTransform();
                    nativeSetCursorVisible(nativeHandle, cursorVisible);

                    if (isColdInit) {
                        Window root = xServer.windowManager.rootWindow;
                        nativeInitRootWindow(nativeHandle, root.id, did(root.getContent()),
                            xServer.screenInfo.width, xServer.screenInfo.height);
                    }
                }
            }
            synchronized (lock) {
                if (nativeHandle != 0) {
                    nativeSetVerboseLog(nativeHandle, true);
                    nativeDumpRendererInfo(nativeHandle);
                }
            }
            initComplete = true;
        });
    }

    public void onSurfaceChanged(int width, int height) {
        if (inPipMode) return;
        surfaceWidth = width; surfaceHeight = height;
        viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
        synchronized (lock) {
            if (nativeHandle != 0) { nativeResize(nativeHandle, width, height); updateTransform(); }
        }
    }

    public void onSurfaceDestroyed() {
        initComplete = false;
        if (initExecutor != null) {
            initExecutor.shutdownNow();
            try { initExecutor.awaitTermination(3, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            initExecutor = null;
        }
        synchronized (lock) {
            if (nativeHandle != 0) nativeDetachSurface(nativeHandle);
        }
    }

    @Override
    public void onDestroy() {
        forceCleanup();
    }

    public void forceCleanup() {
        initComplete = false;
        if (initExecutor != null) {
            initExecutor.shutdownNow();
            try { initExecutor.awaitTermination(3, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            initExecutor = null;
        }
        compositeRedirectedWindows.clear();
        synchronized (lock) {
            if (nativeHandle != 0) {
                nativeDestroy(nativeHandle);
                nativeHandle = 0;
            }
        }
    }

    public void queueEvent(Runnable r) { eventExecutor.execute(r); }
    @Override
    public void onPause()  {}
    @Override
    public void onResume() {}

    private void updateTransform() {
        if (nativeHandle == 0) return;
        final float zoom = magnifierZoom;
        final float ptrX = xServer.pointer.getX();
        final float ptrY = xServer.pointer.getY();

        final float ox, oy, sx, sy;
        if (fullscreen) {
            viewTransformation.update(surfaceWidth, surfaceHeight,
                xServer.screenInfo.width, xServer.screenInfo.height);
            if (zoom != 1.0f) {
                ox = viewTransformation.sceneOffsetX + ptrX * viewTransformation.sceneScaleX * (1f - zoom);
                oy = viewTransformation.sceneOffsetY + ptrY * viewTransformation.sceneScaleY * (1f - zoom);
                sx = viewTransformation.sceneScaleX * zoom;
                sy = viewTransformation.sceneScaleY * zoom;
            } else {
                ox = 0f; oy = 0f; sx = 1f; sy = 1f;
            }
        } else {
            float py = 0;
            if (screenOffsetYRelativeToCursor) {
                short halfH = (short)(xServer.screenInfo.height / 2);
                py = Math.max(0, Math.min(ptrY - halfH / 2.0f, halfH));
            }
            ox = viewTransformation.sceneOffsetX + ptrX * viewTransformation.sceneScaleX * (1f - zoom);
            oy = viewTransformation.sceneOffsetY - py + ptrY * viewTransformation.sceneScaleY * (1f - zoom);
            sx = viewTransformation.sceneScaleX * zoom;
            sy = viewTransformation.sceneScaleY * zoom;
        }

        final boolean needScissor = !(fullscreen && zoom == 1.0f);
        nativeSetTransformAndScissor(nativeHandle, ox, oy, sx, sy,
            needScissor,
            viewTransformation.viewOffsetX, viewTransformation.viewOffsetY,
            viewTransformation.viewWidth,   viewTransformation.viewHeight);
    }

    private void sendCursorToNative(Cursor cursor) {
        if (nativeHandle == 0) return;
        Drawable cd; short hotX = 0, hotY = 0;
        boolean effVis = cursorVisible;
        if (cursor != null) {
            if (!cursor.isVisible()) effVis = false;
            cd = cursor.cursorImage; hotX = (short)cursor.hotSpotX; hotY = (short)cursor.hotSpotY;
        } else { cd = rootCursorDrawable; }
        nativeSetCursorVisible(nativeHandle, effVis);
        if (effVis && cd != null && cd.backingAHB != 0) {
            synchronized (cd.renderLock) {
                ByteBuffer pixels = cd.lockBuffer(cd.backingAHB);
                if (pixels != null) {
                    try {
                        nativeUpdateCursorImage(nativeHandle, pixels, cd.width, cd.height,
                            cd.getStride(), hotX, hotY);
                    } finally {
                        cd.unlockBuffer(cd.backingAHB);
                    }
                }
            }
        }
    }

    private void checkViewable(Window window) {
        if (unviewableWMClasses == null || window == xServer.windowManager.rootWindow) return;
        boolean viewable = true;
        String wc = window.getClassName();
        for (String cls : unviewableWMClasses) {
            if (wc.contains(cls)) {
                if (window.attributes.isEnabled()) window.disableAllDescendants();
                viewable = false;
                break;
            }
        }
        final long id = window.id;
        final boolean v = viewable;
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeSetWindowViewable(nativeHandle, id, v);
            }
        });
    }

    @Override
    public void onCreateWindow(Window window, Window parent) {
        final long id = window.id;
        final long parentId = parent != null ? parent.id : 0;
        final long contentId = did(window.getContent());
        final int x = window.getX(), y = window.getY(), w = window.getWidth(), h = window.getHeight();
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeCreateWindow(nativeHandle, id, parentId, contentId, x, y, w, h);
            }
        });
        checkViewable(window);
    }

    @Override
    public void onDestroyWindow(Window window) {
        final long id = window.id;
        compositeRedirectedWindows.remove(window.id);
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeDestroyWindow(nativeHandle, id);
            }
        });
    }

    @Override
    public void onMapWindow(Window window) {
        final long id = window.id;
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeMapWindow(nativeHandle, id);
            }
        });
        checkViewable(window);
    }

    @Override
    public void onUnmapWindow(Window window) {
        final long id = window.id;
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeUnmapWindow(nativeHandle, id);
            }
        });
    }

    @Override
    public void onReparentWindow(Window window, Window newParent, short x, short y) {
        final long id = window.id;
        final long newParentId = newParent != null ? newParent.id : 0;
        final long contentId = did(window.getContent());
        final int width = window.getWidth(), height = window.getHeight();
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) {
                    nativeReparentWindow(nativeHandle, id, newParentId);
                    nativeUpdateWindowGeometry(nativeHandle, id, contentId, x, y, width, height);
                }
            }
        });
    }

    @Override
    public void onChangeWindowZOrder(Window.StackMode stackMode, Window window, Window sibling) {
        Window parent = window.getParent();
        if (parent == null) return;
        List<Window> children = parent.getChildren();
        final long parentId = parent.id;
        final long[] orderedIds = new long[children.size()];
        for (int i = 0; i < orderedIds.length; i++) orderedIds[i] = children.get(i).id;
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeSyncChildOrder(nativeHandle, parentId, orderedIds, orderedIds.length);
            }
        });
    }

    @Override
    public void onUpdateWindowGeometry(Window window, boolean resized) {
        final long id = window.id;
        final long contentId = did(window.getContent());
        final int x = window.getX(), y = window.getY(), w = window.getWidth(), h = window.getHeight();
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeUpdateWindowGeometry(nativeHandle, id, contentId, x, y, w, h);
            }
        });
    }

    @Override
    public void onModifyWindowProperty(Window window, Property property) {

        checkViewable(window);
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            synchronized (lock) {
                Window pw = xServer.inputDeviceManager.getPointWindow();
                if (pw == window) { lastCursor = window.attributes.getCursor(); sendCursorToNative(lastCursor); }
            }
        }
    }

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable) {
        onUpdateWindowContentDirect(window, drawable, (short) 0, (short) 0);
    }

    public void onUpdateWindowContentDirect(Window window, Drawable pixmap, short xOff, short yOff) {
        if (window.id == fpsWindowId) {
            if (hudRef != null) hudRef.onFrame();
            if (classicHudRef != null) classicHudRef.update();
        }
        synchronized (lock) {
            if (nativeHandle == 0 || pixmap == null) return;
            final int rx = window.getRootX() + xOff, ry = window.getRootY() + yOff;
            synchronized (pixmap.renderLock) {
                if (pixmap.backingAHB != 0) {
                    nativeUpdateWindowContentAHB(nativeHandle, did(window.getContent()),
                        pixmap.backingAHB, pixmap.width, pixmap.height, rx, ry);
                }
            }
        }
    }

    public void addDirectContent(int windowId, Drawable drawable, GPUImage gpuImage) {

    }

    public void nativeRemoveDirectContent(int windowId, int pixmapId) {
        Pixmap pixmap = xServer.pixmapManager.getPixmap(pixmapId);
        if (pixmap == null || pixmap.drawable == null) return;
        final long contentId = did(pixmap.drawable);
        queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeRemoveWindow(nativeHandle, contentId);
            }
        });
    }

    @Override
    public void nativeSetCompositeRedirected(int windowId, boolean redirected) {
        if (xServer.windowManager.getWindow(windowId) == null) return;
        if (redirected) compositeRedirectedWindows.add(windowId);
        else compositeRedirectedWindows.remove(windowId);
    }

    private static boolean hasAncestor(Window window, Window ancestor) {
        Window current = window;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }

    private static Window getWindowSibling(Window window, Window other) {
        Window current = window;
        while (current != null) {
            if (current.getParent() == other.getParent()) return current;
            current = current.getParent();
        }
        return null;
    }

    @Override
    public void nativeCompositeRedirect(int srcDrawableId, int dstDrawableId, short dstX, short dstY) {
        Window srcWindow = xServer.windowManager.getWindow(srcDrawableId);
        if (srcWindow == null) return;

        Window dstWindow = xServer.windowManager.getWindow(dstDrawableId);
        if (dstWindow == null) return;

        if (!hasAncestor(srcWindow, dstWindow)) {
            Window sibling = getWindowSibling(srcWindow, dstWindow);
            if (sibling == null) return;

            int posX = dstWindow.getX() + dstX;
            int posY = dstWindow.getY() + dstY;
            boolean positionChanged = sibling.getX() != posX || sibling.getY() != posY;

            if (positionChanged) {
                sibling.setX((short)posX);
                sibling.setY((short)posY);
                onUpdateWindowGeometry(sibling, false);
            }

            Window parent = sibling.getParent();
            if (parent == null || parent != dstWindow.getParent()) return;

            List<Window> children = parent.getChildren();
            int siblingIndex = children.indexOf(sibling);
            int dstIndex = children.indexOf(dstWindow);
            if (siblingIndex < 0 || dstIndex < 0) return;

            if (siblingIndex <= dstIndex) {
                parent.moveChildAbove(sibling, dstWindow);
                onChangeWindowZOrder(Window.StackMode.ABOVE, sibling, dstWindow);
            }
        }

    }

    @Override
    public void onUpdateWindowContent(Window window) {
        synchronized (lock) {
            if (nativeHandle == 0) return;
            Drawable drawable = window.getContent();
            if (drawable == null || !window.attributes.isMapped()) return;

            final int rx = window.getRootX(), ry = window.getRootY();
            synchronized (drawable.renderLock) {
                if (drawable.backingAHB != 0) {
                    nativeUpdateWindowContentAHB(nativeHandle, did(drawable),
                        drawable.backingAHB, drawable.width, drawable.height, rx, ry);
                }
            }
        }
    }

    @Override
    public void onPointerMove(short x, short y) {
        synchronized (lock) {
            if (nativeHandle == 0) return;
            nativeSetPointerPos(nativeHandle, x, y);
            Window pw = xServer.inputDeviceManager.getPointWindow();
            Cursor cursor = pw != null ? pw.attributes.getCursor() : null;
            if (cursor != lastCursor) { lastCursor = cursor; sendCursorToNative(lastCursor); }
            if (screenOffsetYRelativeToCursor || magnifierZoom != 1.0f) updateTransform();
        }
    }

    @Override
    public void setCursorVisible(boolean visible) {
        cursorVisible = visible;
        synchronized (lock) {
            if (nativeHandle != 0) { nativeSetCursorVisible(nativeHandle, visible); if (visible) sendCursorToNative(lastCursor); }
        }
    }

    public boolean isCursorVisible() { return cursorVisible; }

    public void setDriverInfo(String driverPath, String libraryName, String nativeLibDir) {
        this.driverPath = driverPath;
        this.driverLibraryName = libraryName;
        this.nativeLibDir = nativeLibDir;
    }

    public void setVerboseLog(boolean v) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetVerboseLog(nativeHandle, v); }
    }

    public void dumpRendererInfo() {
        synchronized (lock) { if (nativeHandle != 0) nativeDumpRendererInfo(nativeHandle); }
    }

    public void setStretchMode(int mode) {
        pendingStretchMode = mode;
        synchronized (lock) { if (nativeHandle != 0) nativeSetStretchMode(nativeHandle, mode); }
    }

    public void setPostFXMode(int mode) {
        pendingPostFXMode = mode;
        synchronized (lock) { if (nativeHandle != 0) nativeSetPostFXMode(nativeHandle, mode); }
    }

    /** Enables DIS optical flow frame generation. flowMinSide is the shorter-side pixel budget. */
    public void setFrameGenEnabled(boolean enabled, int flowMinSide, int targetFps, float refreshRate) {
        synchronized (lock) {
            if (nativeHandle == 0) return;
            if (enabled) nativeSetFrameGenConfig(nativeHandle, flowMinSide, targetFps, refreshRate);
            nativeSetFrameGenEnabled(nativeHandle, enabled);
        }
    }

    public void setFrameGenDebugFlow(boolean on) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFrameGenDebugFlow(nativeHandle, on); }
    }

    /**
     * Interpolation intensity. 1.0 keeps the pure optical-flow interpolation; lower values
     * bleed the nearest real frame in, which visibly reduces trailing/ghosting on fast-moving
     * and disoccluded content at the cost of some temporal smoothing.
     */
    public void setFrameGenStrength(float strength) {
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetFrameGenStrength(nativeHandle, strength);
        }
    }

    /**
     * Ghosting suppression. How much of {@link #setFrameGenStrength} survives where the adaptive
     * "this interpolation is untrustworthy" signal maxes out. 1.0 disables adaptive suppression;
     * lower values collapse the generated frame toward the real frame on fast motion, which is
     * what actually removes trailing when turning the camera quickly.
     */
    public void setFrameGenMotionFloor(float motionFloor) {
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetFrameGenMotionFloor(nativeHandle, motionFloor);
        }
    }

    /* --- Full frame-generation configuration (Bionic parity) --------------------------------- */

    /** 0 = performance, 1 = stable (fixed target rate), 2 = quality. */
    public void setFGQualityMode(int mode) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGQualityMode(nativeHandle, mode); }
    }

    /** Optical-flow working resolution, as the minimum side of the flow pyramid in pixels. */
    public void setFGFlowScale(int flowMinSide) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGFlowScale(nativeHandle, flowMinSide); }
    }

    /** Resolution the generation runs at as a fraction of the output; lower is much faster. */
    public void setFGRenderScale(float scale) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGRenderScale(nativeHandle, scale); }
    }

    /** Desired output rate. 0 follows the panel. */
    public void setFGTargetFps(int fps) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGTargetFps(nativeHandle, fps); }
    }

    /** Do not generate while the source rate is below this. 0 disables the floor. */
    public void setFGFloorFps(int fps) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGFloorFps(nativeHandle, fps); }
    }

    /** Hard cap on the output multiplier. */
    public void setFGMaxMult(int mult) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGMaxMult(nativeHandle, mult); }
    }

    /** Frames inserted per real frame when pacing is not rate-driven. */
    public void setFGGeneratedFrameCount(int count) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGGeneratedFrameCount(nativeHandle, count); }
    }

    /** Sharpen generated frames to offset interpolation softness. */
    public void setFGPostProcess(boolean on) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGPostProcess(nativeHandle, on); }
    }

    /** Repair unreliable flow before interpolating — this is what removes trailing on fast motion. */
    public void setFGArtifactClean(boolean on) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGArtifactClean(nativeHandle, on); }
    }

    /** Skip the expensive refinement stages. */
    public void setFGPerfMode(boolean on) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGPerfMode(nativeHandle, on); }
    }

    /** Prefer 16-bit intermediates. */
    public void setFGFp16(boolean on) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGFp16(nativeHandle, on); }
    }

    /** Colourise the flow for debugging. */
    public void setFGHeatmap(boolean on) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGHeatmap(nativeHandle, on); }
    }

    /** Pacing carry, percent of an unused generation budget carried to the next frame. */
    public void setFGCzCarryPct(int pct) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGCzCarryPct(nativeHandle, pct); }
    }

    /** Pacing burst cap. */
    public void setFGCzMax(int czMax) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetFGCzMax(nativeHandle, czMax); }
    }

    /** True once generation has actually produced output for the current configuration. */
    public boolean isFGActive() {
        synchronized (lock) { return nativeHandle != 0 && nativeIsFGActive(nativeHandle); }
    }

    /** Log per-stage timings (diagnostics). */
    public void logFGStageTimes() {
        synchronized (lock) { if (nativeHandle != 0) nativeLogFGStageTimes(nativeHandle); }
    }

    /** Output multiplier: 2 inserts one generated frame per real frame. */
    public void setFrameGenMultiplier(int mult) {
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetFrameGenMultiplier(nativeHandle, mult);
        }
    }

    /** {realFrames, generatedFrames, droppedGenerations} — all zero when FG never ran. */
    public long[] getFrameGenCounts() {
        synchronized (lock) {
            if (nativeHandle == 0) return new long[]{0, 0, 0};
            long[] c = nativeGetFrameGenCounts(nativeHandle);
            return c == null ? new long[]{0, 0, 0} : c;
        }
    }

    public void setSharpness(float s) {
        pendingSharpness = s;
        synchronized (lock) { if (nativeHandle != 0) nativeSetSharpness(nativeHandle, s); }
    }

    public void setFilterMode(int mode) {
        pendingFilterMode = mode;
        synchronized (lock) { if (nativeHandle != 0) nativeSetFilterMode(nativeHandle, mode); }
    }

    public void setSwapRB(boolean enabled) {
        pendingSwapRB = enabled;
        synchronized (lock) { if (nativeHandle != 0) nativeSetSwapRB(nativeHandle, enabled); }
    }

    public void setVkPresentMode(int mode) {
        pendingPresentMode = mode;
        synchronized (lock) { if (nativeHandle != 0) nativeSetPresentMode(nativeHandle, mode); }
    }

    public int[] getSupportedPresentModes() {
        synchronized (lock) {
            if (nativeHandle != 0) return nativeGetSupportedPresentModes(nativeHandle);
        }
        return new int[0];
    }

    @Override
    public boolean isFullscreen() { return fullscreen; }
    @Override
    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        synchronized (lock) { updateTransform(); }
    }
    @Override
    public void setScreenOffsetYRelativeToCursor(boolean b) {
        screenOffsetYRelativeToCursor = b;
        synchronized (lock) { updateTransform(); }
    }
    public boolean isScreenOffsetYRelativeToCursor() { return screenOffsetYRelativeToCursor; }
    @Override
    public void setMagnifierZoom(float zoom) {
        magnifierZoom = zoom;
        synchronized (lock) { updateTransform(); }
    }
    @Override
    public float getMagnifierZoom() { return magnifierZoom; }
    @Override
    public void setUnviewableWMClasses(String... classes) { this.unviewableWMClasses = classes; }

    public void setFpsWindowId(int id) { fpsWindowId = id; }
    public void setFrameRating(Object fr) {
        if (fr instanceof WinlatorHUD) hudRef = (WinlatorHUD) fr;
        else if (fr instanceof FrameRating) classicHudRef = (FrameRating) fr;
    }
    public int getFpsLimit() {
        return fpsLimit;
    }
    public void setFpsLimit(int limit) { this.fpsLimit = Math.max(0, Math.min(1000, limit)); }
    public void setPipMode(boolean pip) { inPipMode = pip; }
    public int getSurfaceWidth() { return surfaceWidth; }
    public int getSurfaceHeight() { return surfaceHeight; }
    public void requestRender() {}
}

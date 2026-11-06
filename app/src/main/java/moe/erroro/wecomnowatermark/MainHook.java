package moe.erroro.wecomnowatermark;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 企业微信去水印模块。
 *
 * <p>基于对企业微信 4.x（21 dex，Flutter 仅用于企业盘/文档）的静态逆向结论，
 * 聊天页与通讯录的水印链路为：</p>
 *
 * <pre>
 * 开关  IEnterpriseMgr.isChatWatermarkEnabled()
 *       IEnterpriseMgr.isContactWatermarkEnabled()
 *          |
 * 下发  setConfigurableTextWaterMask(view, followScroll, background)
 *       setTextWaterMask(view)
 *          |
 * 载体  SuperListView / SuperRecyclerView / WaterMaskFrameLayout ...
 *          |
 * 渲染  混淆类 dsb0 的 n(Canvas) / o(Canvas,int,int)：-20 度旋转 + 砖块交错平铺文字
 * </pre>
 *
 * <p>模块设置四层 hook，任意一层生效即可去水印，多层叠加用于抵抗版本变化：</p>
 * <ol>
 *   <li>开关层：强制 enabled 判断返回 false，并把水印文本置空；</li>
 *   <li>下发层：把 setTextWaterMask / setConfigurableTextWaterMask 变空实现，
 *       列表 View 内部的水印对象始终为 null；</li>
 *   <li>浮层：拦截 Activity 级全局水印（AllWaterMaskView）的挂载；</li>
 *   <li>渲染层：清空混淆渲染器的 Canvas 绘制方法，作为最终兜底。</li>
 * </ol>
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String PKG_WECOM = "com.tencent.wework";

    /** 同一进程只安装一次 */
    private static boolean sHooked = false;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!PKG_WECOM.equals(lpparam.packageName)) {
            return;
        }
        if (sHooked) {
            return;
        }
        sHooked = true;

        final boolean mainProcess = lpparam.processName == null
                || PKG_WECOM.equals(lpparam.processName);

        HookUtil.log("attached to " + lpparam.packageName
                + " process=" + lpparam.processName);

        ClassLoader cl = lpparam.classLoader;

        Class<?> enterpriseMgr = null;
        try {
            enterpriseMgr = hookEnterpriseMgr(cl);
        } catch (Throwable t) {
            Report.fail("开关/文本层", t);
        }

        try {
            hookWaterMaskViews(cl);
        } catch (Throwable t) {
            Report.fail("视图层", t);
        }

        try {
            hookGlobalOverlay(cl);
        } catch (Throwable t) {
            Report.fail("全局浮层", t);
        }

        try {
            hookRenderer(cl, enterpriseMgr);
        } catch (Throwable t) {
            Report.fail("渲染层", t);
        }

        // 一个都没挂上：大概率是版本变化导致所有类/方法名都没匹配到
        if (HookUtil.installedCount() == 0) {
            Report.fail("模块未生效", "没有匹配到任何目标方法，可能不支持当前企业微信版本");
        }

        // 只有主进程弹 Toast，避免多进程重复提示
        if (mainProcess) {
            StartupToast.install(cl, BuildConfig.DEBUG, BuildConfig.VERSION_NAME);
        }

        HookUtil.log("all hooks installed, installed=" + HookUtil.installedCount()
                + " failures=" + Report.count());
    }

    // =====================================================================
    // 1. 开关 + 文本 + API 层
    // =====================================================================
    private static Class<?> hookEnterpriseMgr(ClassLoader cl) {
        final String cn = "com.tencent.wework.enterprisemgr.EnterpriseMgrApiImpl";
        Class<?> c = HookUtil.cls(cl, cn);
        if (c == null) {
            HookUtil.log("EnterpriseMgrApiImpl not found, fallback to renderer-only mode");
            return null;
        }

        // 1.1 开关：聊天 / 通讯录（附带其它水印开关，避免其它页面残留）
        int a = HookUtil.forceFalse(c,
                "isChatWatermarkEnabled",
                "isContactWatermarkEnabled",
                "isCalendarWatermarkEnabled",
                "isWebAppWatermarkEnabled",
                "isOpenMailWatermark");

        // 1.2 文本来源置空：getTextWatermark() 与 getTextWatermark(User, boolean) 都会命中
        int b = HookUtil.forceEmptyString(c,
                "getTextWatermark",
                "getConfigurableTextWatermark");

        // 1.3 下发入口全部变空实现
        int d = HookUtil.noop(c,
                "setTextWaterMask",
                "setConfigurableTextWaterMask",
                "setConfigurableTextWaterMaskByCgi",
                "setConfigurableTextWatermark");

        HookUtil.log("EnterpriseMgrApiImpl: flags=" + a + " text=" + b + " setters=" + d);
        return c;
    }

    // =====================================================================
    // 2. 视图层：绕过 API 直接设置水印的地方
    // =====================================================================
    private static void hookWaterMaskViews(ClassLoader cl) {
        final String pkg = "com.tencent.wework.common.views.";

        // 水印容器：内部渲染器字段只在这两个 setter 里创建，置空后 draw 直接 return
        hookView(cl, pkg + "WaterMaskFrameLayout", "setTextWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "WaterMaskLinearLayout", "setTextWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "WaterMaskRelativeLayout", "setTextWaterMask", "setWatermarkConfig");

        // 图片水印
        hookView(cl, pkg + "WaterMaskImageView",
                "setWaterMask", "setWatermarkConfig", "setCustomWaterMask");

        // 真正的列表载体：聊天 MessageListView 与通讯录列表都是 SuperListView 的子类
        hookView(cl, pkg + "SuperListView", "setWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "SuperRecyclerView", "setWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "BaseScrollView", "setWatermarkConfig");

        // 卡片类视图（联系人卡片、成员申请等）
        hookView(cl, pkg + "CommonInfoCardView", "setWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "CommonInfoProfileView", "setWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "MemberApplyInfoCardView", "setWaterMask", "setWatermarkConfig");
        hookView(cl, pkg + "CommonVirtualRecommCorpCardView", "setWaterMask", "setWatermarkConfig");

        // 全屏浮层自身：文本清空后 getEnableLightWatermark() 恒为 false，draw 不再绘制
        hookView(cl, "com.tencent.wework.launch.AllWaterMaskView",
                "setWaterMarkText", "setWaterMarkColor");
    }

    private static void hookView(ClassLoader cl, String className, String... methods) {
        Class<?> c = HookUtil.cls(cl, className);
        if (c == null) {
            return;   // 版本差异导致类缺失时静默跳过
        }
        int n = HookUtil.noop(c, methods);
        if (n > 0) {
            HookUtil.log("view " + className + " -> " + n + " method(s) neutralised");
        }
    }

    // =====================================================================
    // 3. Activity 级全局水印（日历 / 邮件 / 文件预览等）
    // =====================================================================
    private static void hookGlobalOverlay(ClassLoader cl) {
        Class<?> c = HookUtil.cls(cl, "com.tencent.wework.launch.LaunchApiImpl");
        if (c == null) {
            return;
        }
        int n = HookUtil.noop(c, "attachDarkWatermark", "attachDarkWaterMaskForMail");
        HookUtil.log("LaunchApiImpl global overlay -> " + n + " method(s) neutralised");
    }

    // =====================================================================
    // 4. 渲染器兜底：混淆类 dsb0 的 Canvas 绘制全部清空
    // =====================================================================
    private static void hookRenderer(ClassLoader cl, Class<?> enterpriseMgr) {
        Class<?> renderer = findRendererClass(cl, enterpriseMgr);
        if (renderer == null) {
            HookUtil.log("renderer class not resolved, rely on flag/view hooks");
            return;
        }
        int n = HookUtil.noopCanvasDraw(renderer);
        HookUtil.log("renderer " + renderer.getName()
                + " -> " + n + " draw method(s) neutralised");
    }

    /**
     * 动态定位水印渲染器（当前版本为混淆类 dsb0）。
     *
     * <p>依次尝试：API setter 的参数类型 -> 水印容器中的字段类型 -> 硬编码兜底。
     * 混淆名变化后，只要类之间的关系不变仍能命中。</p>
     */
    private static Class<?> findRendererClass(ClassLoader cl, Class<?> enterpriseMgr) {
        // (a) setConfigurableTextWatermark(dsb0) 的唯一参数就是渲染器
        if (enterpriseMgr != null) {
            for (Method m : enterpriseMgr.getDeclaredMethods()) {
                if (!"setConfigurableTextWatermark".equals(m.getName())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && HookUtil.looksLikeRenderer(p[0])) {
                    return p[0];
                }
            }
        }

        // (b) 水印容器中持有渲染器实例的字段
        Class<?> fl = HookUtil.cls(cl, "com.tencent.wework.common.views.WaterMaskFrameLayout");
        Class<?> t = HookUtil.anyRendererField(fl);
        if (t != null) {
            return t;
        }

        // (c) 全屏浮层持有同一个渲染器
        Class<?> overlay = HookUtil.cls(cl, "com.tencent.wework.launch.AllWaterMaskView");
        t = HookUtil.anyRendererField(overlay);
        if (t != null) {
            return t;
        }

        // (d) 兜底：本版本实测的混淆名
        return HookUtil.cls(cl, "dsb0");
    }
}

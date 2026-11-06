package moe.erroro.wecomnowatermark;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 启动提示。
 *
 * <p>模块代码运行在企业微信进程里，{@code handleLoadPackage} 执行时还没有可用的
 * Context，所以这里挂 {@code Application.onCreate}，等应用上下文就绪后再弹 Toast。</p>
 *
 * <p>只在企业微信主进程调用，避免多进程下重复弹窗。</p>
 */
final class StartupToast {

    /** 延迟一点，等首屏出来再弹，否则用户可能看不到 */
    private static final long DELAY_MS = 800L;

    private StartupToast() {
    }

    static void install(ClassLoader cl, final boolean debug, final String version) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context ctx = (param.thisObject instanceof Context)
                            ? (Context) param.thisObject
                            : null;
                    if (ctx == null) {
                        return;
                    }
                    final String message = buildMessage(debug, version);
                    if (message == null) {
                        return;
                    }
                    final Context appCtx = ctx;
                    Handler main = new Handler(Looper.getMainLooper());
                    main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            show(appCtx, message);
                        }
                    }, DELAY_MS);
                }
            });
        } catch (Throwable t) {
            // 连 Application.onCreate 都挂不上，只能写日志了
            HookUtil.log("StartupToast.install failed", t);
        }
    }

    /**
     * 调试版：总是提示一条「已加载」。
     * 正式版：只有 hook 失败时才提示。
     */
    private static String buildMessage(boolean debug, String version) {
        int failures = Report.count();
        if (!debug && failures == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder("企业微信去水印");
        if (version != null && version.length() > 0) {
            sb.append(' ').append(version);
        }
        if (debug) {
            sb.append("（调试版）");
        }

        if (failures == 0) {
            sb.append(" 已加载");
        } else {
            sb.append("：").append(failures).append(" 项 hook 失败\n")
                    .append(Report.first());
        }
        return sb.toString();
    }

    private static void show(Context ctx, String message) {
        try {
            Context target = ctx.getApplicationContext() != null
                    ? ctx.getApplicationContext()
                    : ctx;
            Toast.makeText(target, message, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            HookUtil.log("show toast failed", t);
        }
    }
}

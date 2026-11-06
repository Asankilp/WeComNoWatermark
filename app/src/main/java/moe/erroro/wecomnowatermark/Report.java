package moe.erroro.wecomnowatermark;

import java.util.ArrayList;
import java.util.List;

/**
 * 收集 hook 阶段产生的失败，供启动后的 Toast 统一展示。
 *
 * <p>注意：{@link #count()} 统计全部失败次数，{@link #first()} 只保留前几条样本，
 * 避免 toast 内容过长。</p>
 */
final class Report {

    private static final int MAX_SAMPLES = 6;
    private static final int MAX_LEN = 200;

    private static final List<String> SAMPLES = new ArrayList<>();
    private static int sTotal = 0;

    private Report() {
    }

    /** 记录一个异常型失败（同时写入 Xposed 日志）。 */
    static synchronized void fail(String where, Throwable t) {
        HookUtil.log(where + " failed", t);
        String detail = (t == null) ? "unknown"
                : (t.getClass().getSimpleName() + ": " + t.getMessage());
        record(where + " -> " + detail);
    }

    /** 记录一个说明型失败（同时写入 Xposed 日志）。 */
    static synchronized void fail(String where, String detail) {
        HookUtil.log(where + " failed: " + detail);
        record(where + " -> " + detail);
    }

    private static void record(String text) {
        sTotal++;
        if (SAMPLES.size() >= MAX_SAMPLES) {
            return;
        }
        if (text != null && text.length() > MAX_LEN) {
            text = text.substring(0, MAX_LEN) + "...";
        }
        SAMPLES.add(text);
    }

    static synchronized int count() {
        return sTotal;
    }

    static synchronized String first() {
        return SAMPLES.isEmpty() ? "" : SAMPLES.get(0);
    }
}

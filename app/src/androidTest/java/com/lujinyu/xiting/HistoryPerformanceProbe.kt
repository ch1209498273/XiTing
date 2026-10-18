package com.lujinyu.xiting

import android.os.Debug
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Call from the existing device runner off the UI thread. No synthetic numbers are published. */
object HistoryPerformanceProbe {
    fun run(): JSONObject {
        val samples = List(500) { 1_760_000_000_000L + it * 86_400_000L }
        fun legacy(ms: Long) = SimpleDateFormat("yyyy-MM",Locale.US).format(Date(ms))
        check(samples.map(::legacy) == samples.map(HistoryStore::monthKey))
        repeat(3) { samples.forEach { legacy(it); HistoryStore.monthKey(it) } }
        fun measured(block: () -> Unit): Long {
            val start = Debug.threadCpuTimeNanos(); block(); return Debug.threadCpuTimeNanos()-start
        }
        val old = mutableListOf<Long>(); val optimized = mutableListOf<Long>()
        repeat(9) { round ->
            // Alternate order to limit systematic warm-up/order bias.
            if(round%2==0) {
                old += measured { samples.forEach { legacy(it) } }
                optimized += measured { samples.forEach { HistoryStore.monthKey(it) } }
            } else {
                optimized += measured { samples.forEach { HistoryStore.monthKey(it) } }
                old += measured { samples.forEach { legacy(it) } }
            }
        }
        return JSONObject().put("sample_count",500).put("rounds",9)
            .put("clock","thread_cpu_ns").put("legacy_median_ns",old.sorted()[4])
            .put("optimized_median_ns",optimized.sorted()[4]).put("results_equal",true)
            .put("scope","month-key computation only; not whole-app latency or battery consumption")
    }
}

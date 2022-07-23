package com.kosenko.stopwar

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.kosenko.stopwar.databinding.ActivityMainBinding
import android.widget.TextView
import android.view.View
import android.graphics.Typeface
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okio.IOException
import okhttp3.Response
import org.json.JSONArray
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import java.time.Duration
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.widget.Button
import android.widget.EditText
import android.annotation.SuppressLint
import android.os.Handler
import android.view.MotionEvent
import android.os.Looper
import android.text.TextWatcher
import android.text.Editable
import android.content.Context
import android.view.inputmethod.InputMethodManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.runButton.setOnClickListener {
            if (Target.isRun) {
                b.runButton.text = getString(R.string.run_label)
                Target.isRun = false
            } else {
                b.runButton.text = getString(R.string.stop_label)
                b.statisticsLayout.removeViews(1, b.statisticsLayout.childCount - 1)
                run {
                    val tv = TextView(baseContext)
                    tv.text = getString(R.string.receive_targets_info)
                    tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    tv.setTypeface(null, Typeface.ITALIC)
                    b.statisticsLayout.addView(tv)
                }
                val req = Request.Builder()
                    .url("http://stopwar.kosenko.info/targets").build()
                
                val call = Target.httpClient.newCall(req)
                
                call.enqueue(
                    object: Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                b.statisticsLayout.removeViews(1, b.statisticsLayout.childCount - 1)
                                val tv = TextView(baseContext)
                                tv.text = getString(R.string.receive_targets_net_error_info)
                                tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                tv.setTypeface(null, Typeface.ITALIC)
                                b.statisticsLayout.addView(tv)
                                b.runButton.text = getString(R.string.run_label)      
                            }
                        }
                
                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                val urls = JSONArray(response.body?.string())
                                val tgs = mutableListOf<Target>()
                                (0 until urls.length()).forEach {
                                      tgs.add(Target(urls[it].toString()))
                                    tgs[it].spawn()
                                }
                                Target.isRun = true
                                run {
                                    val tv = TextView(baseContext)
                                    tv.text = getString(R.string.run_targets_info)
                                    tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                    tv.setTypeface(null, Typeface.ITALIC)
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        b.statisticsLayout.removeViews(1, b.statisticsLayout.childCount - 1)
                                        b.statisticsLayout.addView(tv)
                                    }
                                }
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val bt = LocalDateTime.now()
                                    while (Target.isRun || Target.jobsCount.get() > 0) {
                                        val ctgs = mutableListOf<Target>()
                                        tgs.forEach { ctgs.add(it.copy()) }
                                        b.infoText.text = getString(
                                            R.string.targets_jobs_info,
                                            Duration.between(bt, LocalDateTime.now()).seconds,
                                            ctgs.count(),
                                            Target.jobsCount.get())
                                        val stgs = ctgs.sortedWith {
                                            t1: Target, t2: Target ->
                                                when {
                                                    t1.jobsCount.get() < t2.jobsCount.get() -> 1
                                                    t1.jobsCount.get() > t2.jobsCount.get() -> -1
                                                    else -> when {
                                                        t1.failRate() < t2.failRate() -> -1
                                                        t1.failRate() > t2.failRate() -> 1
                                                        else -> when {
                                                            t1.successCount.get() < t2.successCount.get() -> 1
                                                            t1.successCount.get() > t2.successCount.get() -> -1
                                                            else -> 0
                                                            }
                                                    }
                                                }
                                        }
                                        withContext(Dispatchers.Main) {
                                            b.statisticsLayout.removeViews(1, b.statisticsLayout.childCount - 1)
                                            stgs.forEach {
                                                if (it.jobsCount.get() >= Target.showTargetLimit.get()) {
                                                    val r = it.newRow(baseContext)
                                                    withContext(Dispatchers.Main) {
                                                        b.statisticsLayout.addView(r)
                                                    }
                                                }
                                            }
                                        
                                            if (b.statisticsLayout.childCount == 1) {
                                                val tv = TextView(baseContext)
                                                tv.text = getString(R.string.check_targets_info)
                                                tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                                tv.setTypeface(null, Typeface.ITALIC)
                                                b.statisticsLayout.addView(tv)
                                            }
                                        }
                                        if (Target.isRun) {
                                            tgs.forEach {
                                                if (it.jobsCount.get() == 0) {
                                                    it.spawn()
                                                }
                                            }
                                        }
                                        delay(1000)
                                    }
                                
                                    b.infoText.text = getString(R.string.press_run_to_start_info)
                                    val tv = TextView(baseContext)
                                    tv.text = getString(R.string.no_jobs_now_info)
                                    tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                    tv.setTypeface(null, Typeface.ITALIC)
                                    withContext(Dispatchers.Main) {
                                        b.statisticsLayout.removeViews(1, b.statisticsLayout.childCount - 1)
                                        b.statisticsLayout.addView(tv)
                                    }
                                }
                            } else {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    b.statisticsLayout.removeViews(1, b.statisticsLayout.childCount - 1)
                                    val tv = TextView(baseContext)
                                    tv.text = getString(R.string.receive_targets_http_error_info, response.code)
                                    tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                    tv.setTypeface(null, Typeface.ITALIC)
                                    b.statisticsLayout.addView(tv)
                                    b.runButton.text = getString(R.string.run_label)
                                }
                            }
                        }
                    }
                )            
            }   
        }
        setSpinner(
            b.totalJobsLimitMinusButton,
            b.totalJobsLimitPlusButton,
            b.totalJobsLimitEditor,
            Target.TOTAL_JOBS_LIMIT_DEFAULT,
            b.totalJobsLimitSetButton,
        )
        setSpinner(
            b.targetJobsLimitMinusButton,
            b.targetJobsLimitPlusButton,
            b.targetJobsLimitEditor,
            Target.TARGET_JOBS_LIMIT_DEFAULT,
            b.targetJobsLimitSetButton,
        )
        setSpinner(
            b.showTargetLimitMinusButton,
            b.showTargetLimitPlusButton,
            b.showTargetLimitEditor,
            Target.SHOW_TARGET_LIMIT_DEFAULT,
            b.showTargetLimitSetButton,
        )
    }
    private lateinit var b: ActivityMainBinding
    @SuppressLint("ClickableViewAccessibility")
    private fun setSpinner(minusButton: Button,
                           plusButton: Button,
                           editor: EditText,
                           default: UInt,
                           setButton: Button) {
        minusButton.setOnTouchListener(OnSpinListener(this, spinMinus(editor, default)))
        plusButton.setOnTouchListener(OnSpinListener(this, spinPlus(editor, default)))
        editor.addTextChangedListener(OnTextChangeListener(setButton))
    }
    class OnSpinListener(private val activity: MainActivity, private val spin: () -> Unit): View.OnTouchListener {
        private var h: Handler? = null
    
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, e: MotionEvent?): Boolean {
            when (e!!.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    if (activity.currentFocus != null) {
                        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(activity.currentFocus!!.windowToken, 0)
                        activity.currentFocus!!.clearFocus()
                    }
    
                    if (h == null) {
                        h = Handler(Looper.getMainLooper())
                        h!!.postDelayed(call, 250)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (h != null) {
                        h!!.removeCallbacks(call)
                        h = null
                    }
                }
            }
            return true
        }
    
        private fun loop() {
            spin()
            if (h != null) {
              h!!.postDelayed(call, 50)
            }
        }
    
        private val call = { loop() }
    }
    private fun spinMinus(e: EditText, d: UInt): () -> Unit {
        return {
            e.setText(
                if (e.text.isEmpty()) {
                    d - 1U
                } else {
                    val v = e.text.toString().toUInt()
                    if (v == 0U) { 0U } else { v - 1U }
                }.toString()
            )
        }
    }
    
    private fun spinPlus(e: EditText, d: UInt): () -> Unit {
        return {
            e.setText(
                if (e.text.isEmpty()) {
                    d + 1U
                } else {
                    e.text.toString().toUInt() + 1U
                }.toString()
            )
        }
    }
    class OnTextChangeListener(private val setButton: Button): TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
            // No actions
        }
    
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            // No actions
        }
    
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            setButton.isEnabled = true
        }
    }
    fun onTotalJobsLimitSetClick(v: View) {
        hideKeyboard()
        Target.totalJobsLimit.set(
            if (b.totalJobsLimitEditor.text.isEmpty()) {
                Target.TOTAL_JOBS_LIMIT_DEFAULT.toInt()
            } else {
                b.totalJobsLimitEditor.text.toString().toInt()
            }
        )
        v.isEnabled = false
    }
    
    fun onTargetJobsLimitSetClick(v: View) {
        hideKeyboard()
        Target.targetJobsLimit.set(
            if (b.targetJobsLimitEditor.text.isEmpty()) {
                256
            } else {
                b.targetJobsLimitEditor.text.toString().toInt()
            }
        )
        v.isEnabled = false
    }
    
    fun onShowTargetLimitSetClick(v: View) {
        hideKeyboard()
        Target.showTargetLimit.set(
            if (b.showTargetLimitEditor.text.isEmpty()) {
                2
            } else {
                b.showTargetLimitEditor.text.toString().toInt()
            }
        )
        v.isEnabled = false
    }
    private fun hideKeyboard() {
        if (currentFocus != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
            currentFocus!!.clearFocus()
        }
    }
}

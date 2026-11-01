package com.example.clocklauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView

class MainActivity : Activity() {

    private class App(val label: String, val pkg: String, val icon: Drawable, val hidden: Boolean)

    private lateinit var prefs: SharedPreferences
    private lateinit var list: ListView
    private var apps = listOf<App>()
    private var showAll = false
    private var taps = 0
    private var lastTap = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("hidden", MODE_PRIVATE)

        list = ListView(this)
        setContentView(list)

        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 64)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) countTap()
                true
            }
        }
        list.addHeaderView(clock, null, false)

        list.setOnItemClickListener { _, _, pos, _ ->
            val app = apps.getOrNull(pos - list.headerViewsCount) ?: return@setOnItemClickListener
            packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
        }

        list.setOnItemLongClickListener { _, _, pos, _ ->
            val app = apps.getOrNull(pos - list.headerViewsCount) ?: return@setOnItemLongClickListener true
            val option = if (app.hidden) "בטל הסתרה" else "הסתר"
            AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(arrayOf(option)) { _, _ -> toggleHidden(app.pkg) }
                .show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        showAll = false
        refresh()
    }

    private fun countTap() {
        val now = SystemClock.uptimeMillis()
        taps = if (now - lastTap < 500) taps + 1 else 1
        lastTap = now
        if (taps >= 3) {
            taps = 0
            showAll = true
            refresh()
        }
    }

    private fun toggleHidden(pkg: String) {
        val set = prefs.getStringSet("h", emptySet())!!.toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        prefs.edit().putStringSet("h", set).apply()
        refresh()
    }

    private fun refresh() {
        val hidden = prefs.getStringSet("h", emptySet())!!
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val p = it.activityInfo.packageName
                App(it.loadLabel(pm).toString(), p, it.loadIcon(pm), p in hidden)
            }
            .filter { showAll || !it.hidden }
            .sortedBy { it.label.lowercase() }

        list.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int) = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: ViewGroup): View {
                val a = apps[i]
                val tv = (v as? TextView) ?: TextView(this@MainActivity).apply {
                    textSize = 18f
                    setPadding(32, 24, 32, 24)
                    compoundDrawablePadding = 32
                    gravity = Gravity.CENTER_VERTICAL
                }
                tv.text = if (a.hidden) "🔒 ${a.label}" else a.label
                a.icon.setBounds(0, 0, 96, 96)
                tv.setCompoundDrawables(a.icon, null, null, null)
                return tv
            }
        }
    }
}

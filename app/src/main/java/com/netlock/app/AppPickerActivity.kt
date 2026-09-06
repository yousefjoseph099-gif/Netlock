package com.netlock.app

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.netlock.app.data.Prefs
import com.netlock.app.util.AppsHelper

class AppPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        val prefs = Prefs.getInstance(this)
        val selected = prefs.selectedPackages.toMutableSet()

        val candidates = AppsHelper.listBrowserCandidates(packageManager)

        val rv = findViewById<RecyclerView>(R.id.rvApps)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = AppPickerAdapter(candidates, packageManager, selected)

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            prefs.selectedPackages = selected
            setResult(RESULT_OK)
            finish()
        }
    }
}

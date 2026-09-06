package com.netlock.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.netlock.app.data.Prefs
import com.netlock.app.util.PasswordUtils

/**
 * Transparent-ish dialog activity used as a gate before any sensitive action
 * (stopping protection, editing lists, changing mode) when a password is set.
 * Call via startActivityForResult and check RESULT_OK.
 */
class UnlockActivity : Activity() {

    private lateinit var etPassword: EditText
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock)

        etPassword = findViewById(R.id.etPassword)
        tvError = findViewById(R.id.tvError)

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            attemptUnlock()
        }
    }

    private fun attemptUnlock() {
        val prefs = Prefs.getInstance(this)
        val entered = etPassword.text.toString()
        val salt = prefs.passwordSalt
        val hash = prefs.passwordHash

        if (salt == null || hash == null) {
            setResult(RESULT_OK)
            finish()
            return
        }

        if (PasswordUtils.verify(entered, salt, hash)) {
            setResult(RESULT_OK)
            finish()
        } else {
            tvError.visibility = View.VISIBLE
        }
    }
}

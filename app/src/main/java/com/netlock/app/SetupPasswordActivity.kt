package com.netlock.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.netlock.app.data.Prefs
import com.netlock.app.util.PasswordUtils

class SetupPasswordActivity : Activity() {

    private lateinit var etOld: EditText
    private lateinit var etNew: EditText
    private lateinit var etConfirm: EditText
    private lateinit var tvError: TextView
    private lateinit var tvOldLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_password)

        val prefs = Prefs.getInstance(this)

        etOld = findViewById(R.id.etOldPassword)
        etNew = findViewById(R.id.etNewPassword)
        etConfirm = findViewById(R.id.etConfirmPassword)
        tvError = findViewById(R.id.tvError)
        tvOldLabel = findViewById(R.id.tvOldPasswordLabel)

        val hasPassword = prefs.hasPassword()
        tvOldLabel.visibility = if (hasPassword) View.VISIBLE else View.GONE
        etOld.visibility = if (hasPassword) View.VISIBLE else View.GONE

        findViewById<Button>(R.id.btnRemovePassword).visibility =
            if (hasPassword) View.VISIBLE else View.GONE

        findViewById<Button>(R.id.btnSave).setOnClickListener { save(prefs, hasPassword) }
        findViewById<Button>(R.id.btnRemovePassword).setOnClickListener { removePassword(prefs) }
    }

    private fun save(prefs: Prefs, hadPassword: Boolean) {
        if (hadPassword) {
            val old = etOld.text.toString()
            val salt = prefs.passwordSalt
            val hash = prefs.passwordHash
            if (salt == null || hash == null || !PasswordUtils.verify(old, salt, hash)) {
                showError("Current password is incorrect")
                return
            }
        }

        val newPass = etNew.text.toString()
        val confirm = etConfirm.text.toString()

        if (newPass.length < 4) {
            showError("Password must be at least 4 characters")
            return
        }
        if (newPass != confirm) {
            showError("Passwords do not match")
            return
        }

        val salt = PasswordUtils.generateSalt()
        val hash = PasswordUtils.hash(newPass, salt)
        prefs.passwordSalt = salt
        prefs.passwordHash = hash

        setResult(RESULT_OK)
        finish()
    }

    private fun removePassword(prefs: Prefs) {
        if (prefs.hasPassword()) {
            val old = etOld.text.toString()
            val salt = prefs.passwordSalt
            val hash = prefs.passwordHash
            if (salt == null || hash == null || !PasswordUtils.verify(old, salt, hash)) {
                showError("Current password is incorrect")
                return
            }
        }
        prefs.clearPassword()
        setResult(RESULT_OK)
        finish()
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}

package com.example.tiktokoverlay

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etTiktok = findViewById<EditText>(R.id.etTiktokUsername)
        val rgRole = findViewById<RadioGroup>(R.id.rgRole)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        rgRole.setOnCheckedChangeListener { _, checkedId ->
            etTiktok.visibility =
                if (checkedId == R.id.rbCreator) android.view.View.VISIBLE else android.view.View.GONE
        }

        findViewById<Button>(R.id.btnSignup).setOnClickListener {
            val role = if (rgRole.checkedRadioButtonId == R.id.rbCreator) "creator" else "fan"
            lifecycleScope.launch {
                try {
                    val response = ApiClient.service.signup(
                        SignupRequest(
                            email = etEmail.text.toString(),
                            password = etPassword.text.toString(),
                            role = role,
                            tiktok_username = if (role == "creator") etTiktok.text.toString() else null
                        )
                    )
                    if (response.isSuccessful && response.body() != null) {
                        onAuthSuccess(response.body()!!)
                    } else {
                        tvStatus.text = "Signup failed: ${response.errorBody()?.string()}"
                    }
                } catch (e: Exception) {
                    tvStatus.text = "Network error: ${e.message}"
                }
            }
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val response = ApiClient.service.login(
                        LoginRequest(etEmail.text.toString(), etPassword.text.toString())
                    )
                    if (response.isSuccessful && response.body() != null) {
                        onAuthSuccess(response.body()!!)
                    } else {
                        tvStatus.text = "Login failed: ${response.errorBody()?.string()}"
                    }
                } catch (e: Exception) {
                    tvStatus.text = "Network error: ${e.message}"
                }
            }
        }
    }

    private fun onAuthSuccess(auth: AuthResponse) {
        ApiClient.authToken = auth.token
        getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit()
            .putString("token", auth.token)
            .putString("role", auth.user.role)
            .apply()
        finish()
    }
}

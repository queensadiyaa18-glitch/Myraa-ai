package com.myra.assistant.ui.splash

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.databinding.ActivitySplashBinding
import com.myra.assistant.data.memory.AppDatabase
import com.myra.assistant.service.BackgroundWakeWordService
import com.myra.assistant.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated Professional Loading Screen (Splash Screen).
 * Minimalist, professional dark branding featuring:
 * - App Name: "MYRA" prominently at the center
 * - Tagline: "Your AI Assistant, Redefined"
 * - Centered sleek pulsing glowing orb loading animation
 * - Preloads core services (Room Database, Gemini setup cache, background wake-word engine)
 * - Transitions smoothly to Home (MainActivity)
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startPulseAnimation()
        initializeCoreServices()
    }

    private fun startPulseAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.88f, 1.12f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.88f, 1.12f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.75f, 1.0f)

        ObjectAnimator.ofPropertyValuesHolder(binding.splashOrbView, scaleX, scaleY, alpha).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun initializeCoreServices() {
        val startTime = System.currentTimeMillis()

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Preload local SQLite Room database
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.memoryDao().getPublicMemories()
            } catch (e: Exception) {
                // Non-fatal
            }

            // 2. Start background Wake Word voice guard if enabled
            try {
                val prefs = getSharedPreferences("myra_prefs", MODE_PRIVATE)
                val wakeWordEnabled = prefs.getBoolean("wake_word_enabled", true)
                if (wakeWordEnabled) {
                    val serviceIntent = Intent(applicationContext, BackgroundWakeWordService::class.java)
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Non-fatal
            }

            // Ensure at least 1500ms for smooth splash display
            val elapsed = System.currentTimeMillis() - startTime
            val remainingDelay = (1500L - elapsed).coerceAtLeast(400L)

            withContext(Dispatchers.Main) {
                binding.splashStatusText.text = "AI Core Ready"
                handler.postDelayed({
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }, remainingDelay)
            }
        }
    }
}

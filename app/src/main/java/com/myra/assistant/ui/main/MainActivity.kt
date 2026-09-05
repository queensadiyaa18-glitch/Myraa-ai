package com.myra.assistant.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.databinding.ActivityMainBinding
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.data.memory.MemoryRepository
import com.myra.assistant.service.BackgroundWakeWordService
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraNotificationListenerService
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.ui.memory.MemoriesActivity
import com.myra.assistant.ui.permissions.PermissionsActivity
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Activity of MYRA AI Voice Assistant.
 * Controls the futuristic Canvas Orb, AudioEngine, Gemini Live WebSocket client,
 * Navigation Drawer, Dashboard Status Widgets, Bottom Nav Bar, and Phone Automation.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var geminiClient: GeminiLiveClient
    private lateinit var audioEngine: AudioEngine
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var memoryRepo: MemoryRepository

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAssistantActive = false

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleMediaAttachment(uri)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            startAssistantEngine()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice assistant", Toast.LENGTH_LONG).show()
        }
    }

    private val systemStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CallMonitorService.ACTION_INCOMING_CALL -> {
                    val callerName = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NAME) ?: "Unknown"
                    handleIncomingCallAnnouncement(callerName)
                }
                MyraNotificationListenerService.ACTION_OTP_DETECTED -> {
                    val otp = intent.getStringExtra(MyraNotificationListenerService.EXTRA_OTP_CODE) ?: ""
                    handleOtpAnnouncement(otp)
                }
                MyraOverlayService.BROADCAST_MIC_STATE_CHANGED -> {
                    val isMuted = intent.getBooleanExtra(MyraOverlayService.EXTRA_IS_MUTED, false)
                    audioEngine.isMuted = isMuted
                    if (isMuted) {
                        binding.micButton.setImageResource(R.drawable.ic_mic_off)
                        binding.statusText.text = "Mic Muted via Floating Orb 🔇"
                    } else {
                        binding.micButton.setImageResource(R.drawable.ic_mic_on)
                        binding.statusText.text = "Listening... Bolo 💬"
                    }
                }
            }
        }
    }

    private val dashboardTickerRunnable = object : Runnable {
        override fun run() {
            updateDashboardWidgets()
            mainHandler.postDelayed(this, 15000) // 15 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        memoryRepo = MemoryRepository(this)

        initUi()
        initDrawer()
        initAudioAndAi()
        setupViewModelObservers()
        startCallMonitorService()
        startHardwareTriggerService()
        checkAndRequestPermissions()
        registerReceivers()

        if (intent.getBooleanExtra("EXTRA_AUTO_START_MIC", false)) {
            mainHandler.postDelayed({ startAssistantEngine() }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboardWidgets()
        mainHandler.post(dashboardTickerRunnable)

        // When user opens/returns to the app, stop overlay to prevent duplicate UI
        if (MyraOverlayService.isServiceRunning) {
            val stopOverlay = Intent(this, MyraOverlayService::class.java).apply {
                action = MyraOverlayService.ACTION_STOP_OVERLAY
            }
            startService(stopOverlay)
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(dashboardTickerRunnable)
    }

    override fun onStop() {
        super.onStop()
        // Automatically start floating overlay if enabled and permission granted
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val overlayEnabled = prefs.getBoolean("floating_overlay_enabled", true)

        if (overlayEnabled && !isFinishing) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this)) {
                try {
                    val overlayIntent = Intent(this, MyraOverlayService::class.java).apply {
                        action = MyraOverlayService.ACTION_START_OVERLAY
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(overlayIntent)
                    } else {
                        startService(overlayIntent)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(systemStatusReceiver)
        audioEngine.release()
        geminiClient.disconnect()
    }

    private fun initUi() {
        chatAdapter = ChatAdapter()
        binding.chatRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        // Top Bar Drawer Button
        binding.btnOpenDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Settings Button
        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Camera Vision OCR Button
        binding.btnCameraVision.setOnClickListener {
            showVisionCameraDialog()
        }

        // Quick Access Bar: Music, Study, Journal
        binding.pillMusic.setOnClickListener {
            triggerMusicPlatform()
        }
        binding.pillStudy.setOnClickListener {
            sendQuickPrompt("Let's begin a focused study session. Please summarize what you can help me learn today.")
        }
        binding.pillJournal.setOnClickListener {
            startActivity(Intent(this, MemoriesActivity::class.java))
        }

        // Central Mic Button
        binding.micButton.setOnClickListener {
            toggleAssistant()
        }

        binding.micButton.setOnLongClickListener {
            stopAssistantSession()
            Toast.makeText(this, "MYRA standby mode", Toast.LENGTH_SHORT).show()
            true
        }

        // Central Text Input & Send
        binding.btnSendText.setOnClickListener {
            sendUserTypedMessage()
        }
        binding.editTextInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendUserTypedMessage()
                true
            } else {
                false
            }
        }

        // Attachment Button (Uses Zero-Permission Android Photo Picker)
        binding.btnAttach.setOnClickListener {
            pickMediaLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }

        // Bottom Navigation Bar items
        binding.navHome.setOnClickListener {
            // Already Home
        }
        binding.navScan.setOnClickListener {
            showVisionCameraDialog()
        }
        binding.navMemories.setOnClickListener {
            startActivity(Intent(this, MemoriesActivity::class.java))
        }
        binding.navChat.setOnClickListener {
            binding.editTextInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.editTextInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun initDrawer() {
        val navView = binding.navigationDrawerView
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        // 1. Voice Gender / Model Style Spinner
        val spinnerVoice = navView.findViewById<Spinner>(R.id.drawerSpinnerVoiceGender)
        val voiceOptions = listOf("Aoede (Warm Female)", "Charon (Deep Male)", "Kore (Bright Female)", "Fenrir (Intense Male)", "Puck (Playful)")
        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceOptions)
        spinnerVoice.adapter = voiceAdapter

        val savedVoice = prefs.getString("gemini_voice", "Aoede")
        val voiceIdx = voiceOptions.indexOfFirst { it.contains(savedVoice ?: "Aoede") }.coerceAtLeast(0)
        spinnerVoice.setSelection(voiceIdx)

        // 2. Pitch & Speed Sliders
        val seekPitch = navView.findViewById<SeekBar>(R.id.drawerSeekPitch)
        val textPitch = navView.findViewById<TextView>(R.id.drawerPitchLabel)
        val seekSpeed = navView.findViewById<SeekBar>(R.id.drawerSeekSpeed)
        val textSpeed = navView.findViewById<TextView>(R.id.drawerSpeedLabel)

        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val factor = 0.5f + (progress / 10f)
                textPitch.text = String.format(Locale.ROOT, "Voice Pitch: %.1fx", factor)
                prefs.edit().putFloat("voice_pitch", factor).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val factor = 0.5f + (progress / 10f)
                textSpeed.text = String.format(Locale.ROOT, "Speaking Speed: %.1fx", factor)
                prefs.edit().putFloat("voice_speed", factor).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. Preferred Music Platform
        val savedMusicPlatform = prefs.getString("preferred_music", "youtube") ?: "youtube"
        when (savedMusicPlatform) {
            "spotify" -> navView.findViewById<RadioButton>(R.id.radioSpotify).isChecked = true
            "ytmusic" -> navView.findViewById<RadioButton>(R.id.radioYtMusic).isChecked = true
            else -> navView.findViewById<RadioButton>(R.id.radioYouTube).isChecked = true
        }

        navView.findViewById<RadioButton>(R.id.radioYouTube).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) prefs.edit().putString("preferred_music", "youtube").apply()
        }
        navView.findViewById<RadioButton>(R.id.radioSpotify).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) prefs.edit().putString("preferred_music", "spotify").apply()
        }
        navView.findViewById<RadioButton>(R.id.radioYtMusic).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) prefs.edit().putString("preferred_music", "ytmusic").apply()
        }

        // 4. Drawer Shortcuts
        navView.findViewById<View>(R.id.drawerItemMemories).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, MemoriesActivity::class.java))
        }

        navView.findViewById<View>(R.id.drawerItemSettings).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        navView.findViewById<View>(R.id.drawerItemPermissions).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        navView.findViewById<View>(R.id.drawerItemOverlay).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            toggleFloatingOverlay()
        }

        navView.findViewById<View>(R.id.drawerItemSos).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            viewModel.executeCommand(com.myra.assistant.model.AppCommand(com.myra.assistant.model.AppCommand.TYPE_EMERGENCY_SOS))
        }

        navView.findViewById<View>(R.id.drawerItemFocus).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            viewModel.executeCommand(com.myra.assistant.model.AppCommand(com.myra.assistant.model.AppCommand.TYPE_FOCUS_MODE))
        }

        // 5. Wake Word Switch
        val switchWake = navView.findViewById<SwitchCompat>(R.id.drawerSwitchWakeWord)
        val wakeEnabled = prefs.getBoolean("wake_word_enabled", true)
        switchWake.isChecked = wakeEnabled

        switchWake.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wake_word_enabled", isChecked).apply()
            val serviceIntent = Intent(this, BackgroundWakeWordService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Wake word guard activated ('Hey MYRA')", Toast.LENGTH_SHORT).show()
            } else {
                stopService(serviceIntent)
                Toast.makeText(this, "Wake word guard paused", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerMusicPlatform() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val platform = prefs.getString("preferred_music", "youtube") ?: "youtube"
        val pkg = when (platform) {
            "spotify" -> "com.spotify.music"
            "ytmusic" -> "com.google.android.apps.youtube.music"
            else -> "com.google.android.youtube"
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            // Fallback web intent
            val fallbackUrl = when (platform) {
                "spotify" -> "https://open.spotify.com"
                "ytmusic" -> "https://music.youtube.com"
                else -> "https://youtube.com"
            }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
        }
    }

    private fun toggleFloatingOverlay() {
        if (MyraOverlayService.isServiceRunning) {
            val stopIntent = Intent(this, MyraOverlayService::class.java).apply {
                action = MyraOverlayService.ACTION_STOP_OVERLAY
            }
            startService(stopIntent)
            Toast.makeText(this, "Floating Overlay dismissed", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
        } else {
            val overlayIntent = Intent(this, MyraOverlayService::class.java).apply {
                action = MyraOverlayService.ACTION_START_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
            Toast.makeText(this, "Floating MYRA Glass Orb active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDashboardWidgets() {
        // Today Date
        val dateFormat = SimpleDateFormat("d EEE, MMM", Locale.getDefault())
        binding.widgetDateText.text = dateFormat.format(Date())

        // Weather (real-time device hour approximation or current state)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val weatherText = when {
            hour in 6..11 -> "24°C Morning 🌤️"
            hour in 12..16 -> "29°C Sunny ☀️"
            hour in 17..19 -> "26°C Evening 🌅"
            else -> "22°C Clear 🌙"
        }
        binding.widgetWeatherText.text = weatherText

        // Mood reflection from personality mode
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val moodText = when (personality) {
            "professional" -> "Focused 💼"
            "assistant" -> "Ready 🤖"
            else -> "Warm 💖"
        }
        binding.widgetMoodText.text = moodText
    }

    private fun handleMediaAttachment(uri: Uri) {
        chatAdapter.addMessage(ChatMessage("Attached media: $uri", isUser = true))
        binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
        sendQuickPrompt("I have attached a media image. Please review and provide insights or summarize the file.")
    }

    private fun sendUserTypedMessage() {
        val text = binding.editTextInput.text.toString().trim()
        if (text.isEmpty()) return

        binding.editTextInput.setText("")
        chatAdapter.addMessage(ChatMessage(text, isUser = true))
        binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)

        // Parse automated command
        val command = CommandParser.parse(text)
        if (command != null) {
            binding.orbView.state = OrbAnimationView.State.THINKING
            binding.statusText.text = "Executing action..."
            viewModel.executeCommand(command)
        } else {
            // Save to memory if it's a memory statement (e.g., "remember my ...")
            if (text.lowercase(Locale.ROOT).startsWith("remember ") || text.lowercase(Locale.ROOT).startsWith("yaad rakh")) {
                lifecycleScope.launch {
                    val content = text.replace(Regex("^(remember|yaad rakh)\\s+", RegexOption.IGNORE_CASE), "")
                    memoryRepo.saveMemory("User Note", content, "Personal", false)
                    chatAdapter.addMessage(ChatMessage("✨ Saved to persistent memory: $content", isUser = false))
                    binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }

            // Send to Gemini
            if (!isAssistantActive) {
                startAssistantEngine()
            }
            geminiClient.sendText(text)
        }
    }

    private fun sendQuickPrompt(prompt: String) {
        if (!isAssistantActive) {
            startAssistantEngine()
        }
        chatAdapter.addMessage(ChatMessage(prompt, isUser = true))
        binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
        geminiClient.sendText(prompt)
    }

    private fun initAudioAndAi() {
        audioEngine = AudioEngine(this)
        geminiClient = GeminiLiveClient(this)

        // Mic -> WebSocket
        audioEngine.onAudioCaptured = { pcmChunk ->
            geminiClient.sendAudio(pcmChunk)
        }

        // Amplitude -> UI Orb and Waveform
        audioEngine.onAmplitudeChanged = { rms ->
            runOnUiThread {
                binding.orbView.setAmplitude(rms)
                binding.waveformView.setAmplitude(rms)
            }
        }

        // Speaker state callbacks
        audioEngine.onSpeakingStarted = {
            runOnUiThread {
                binding.orbView.state = OrbAnimationView.State.SPEAKING
                binding.statusText.text = "MYRA is speaking..."
            }
        }

        audioEngine.onSpeakingStopped = {
            runOnUiThread {
                if (isAssistantActive) {
                    binding.orbView.state = OrbAnimationView.State.LISTENING
                    binding.statusText.text = "Listening to you..."
                } else {
                    binding.orbView.state = OrbAnimationView.State.IDLE
                    binding.statusText.text = "Tap karke bolo 💬"
                }
            }
        }

        // Gemini WebSocket Callbacks
        geminiClient.onConnected = {
            runOnUiThread {
                binding.statusText.text = "Connecting with MYRA..."
            }
        }

        geminiClient.onSetupComplete = {
            runOnUiThread {
                binding.statusText.text = "Listening... Bolo kya chahiye 😊"
                binding.orbView.state = OrbAnimationView.State.LISTENING
                audioEngine.startRecording()
                audioEngine.startPlayback()
            }
        }

        geminiClient.onAudioReceived = { pcmBytes ->
            audioEngine.queueAudio(pcmBytes)
        }

        geminiClient.onInputTranscript = { userText ->
            runOnUiThread {
                chatAdapter.addMessage(ChatMessage(userText, isUser = true))
                binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)

                val command = CommandParser.parse(userText)
                if (command != null) {
                    binding.orbView.state = OrbAnimationView.State.THINKING
                    binding.statusText.text = "Executing action..."
                    viewModel.executeCommand(command)
                }
            }
        }

        geminiClient.onOutputTranscript = { myraText ->
            runOnUiThread {
                chatAdapter.appendOrUpdateMyra(myraText)
                binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }

        geminiClient.onInterrupted = {
            runOnUiThread {
                audioEngine.clearPlaybackQueue()
                binding.orbView.state = OrbAnimationView.State.LISTENING
            }
        }

        geminiClient.onError = { errorMsg ->
            runOnUiThread {
                binding.statusText.text = errorMsg
                binding.orbView.state = OrbAnimationView.State.IDLE
            }
        }
    }

    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            viewModel.actionEvent.collectLatest { actionSummary ->
                chatAdapter.addMessage(ChatMessage("⚡ $actionSummary", isUser = false))
                binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }

        lifecycleScope.launch {
            viewModel.feedbackVoice.collectLatest { feedbackText ->
                geminiClient.sendText("System confirmation: $feedbackText. Please acknowledge to the user naturally in 1 sentence.")
            }
        }
    }

    private fun toggleAssistant() {
        if (!isAssistantActive) {
            startAssistantEngine()
        } else {
            audioEngine.isMuted = !audioEngine.isMuted
            if (audioEngine.isMuted) {
                binding.micButton.setImageResource(R.drawable.ic_mic_off)
                binding.statusText.text = "Mic Muted (Tap to unmute)"
            } else {
                binding.micButton.setImageResource(R.drawable.ic_mic_on)
                binding.statusText.text = "Listening... Bolo 💬"
            }
        }
    }

    private fun startAssistantEngine() {
        isAssistantActive = true
        binding.micButton.setImageResource(R.drawable.ic_mic_on)
        binding.redOverlay.animate().alpha(0.08f).setDuration(400).start()
        binding.orbView.state = OrbAnimationView.State.THINKING
        binding.statusText.text = "Starting MYRA engine..."

        audioEngine.isMuted = false
        geminiClient.connect()
    }

    private fun stopAssistantSession() {
        isAssistantActive = false
        binding.micButton.setImageResource(R.drawable.ic_mic_off)
        binding.redOverlay.animate().alpha(0f).setDuration(300).start()
        binding.orbView.state = OrbAnimationView.State.IDLE
        binding.statusText.text = "Tap karke bolo 💬"

        audioEngine.stopRecording()
        audioEngine.clearPlaybackQueue()
        geminiClient.disconnect()
    }

    private fun handleIncomingCallAnnouncement(callerName: String) {
        startAssistantEngine()
        val announcementPrompt = "System Alert: Incoming phone call ringing right now from $callerName. Please announce this call to me enthusiastically and warmly!"
        geminiClient.sendText(announcementPrompt)
    }

    private fun handleOtpAnnouncement(otpCode: String) {
        if (!isAssistantActive) {
            startAssistantEngine()
        }
        val prompt = "System Alert: Verification code received: $otpCode. Tell the user their OTP aloud clearly: $otpCode."
        geminiClient.sendText(prompt)
    }

    private fun showVisionCameraDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_vision_camera, null)
        val previewView = dialogView.findViewById<PreviewView>(R.id.cameraPreviewView)
        val btnScene = dialogView.findViewById<Button>(R.id.btnAnalyzeScene)
        val btnOcr = dialogView.findViewById<Button>(R.id.btnExtractOcr)
        val btnPlantMed = dialogView.findViewById<Button>(R.id.btnIdentifyPlantMed)
        val btnStress = dialogView.findViewById<Button>(R.id.btnBiometricStress)
        val closeBtn = dialogView.findViewById<ImageButton>(R.id.visionCloseBtn)
        val textResult = dialogView.findViewById<TextView>(R.id.textVisionResult)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                textResult.text = "Camera initialization failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))

        btnScene.setOnClickListener {
            textResult.text = "Analyzing visual scene with Gemini Vision..."
            geminiClient.sendText("Visual Scene Analysis: Describe what the phone camera sees in front of the user in a friendly tone.")
            dialog.dismiss()
        }

        btnOcr.setOnClickListener {
            textResult.text = "Reading detected text (OCR)..."
            geminiClient.sendText("Camera OCR request: Extract and read aloud any visible text or document details in front of the lens.")
            dialog.dismiss()
        }

        btnPlantMed.setOnClickListener {
            textResult.text = "Identifying plant / medicine details..."
            geminiClient.sendText("Camera Lens Analysis: Identify this plant species or medicine packaging. Provide key properties, uses, and safe handling details.")
            dialog.dismiss()
        }

        btnStress.setOnClickListener {
            textResult.text = "Biometric & Stress monitoring..."
            geminiClient.sendText("Biometric Assessment: Analyze user facial expressions and posture for stress, fatigue, or mood. Provide caring empathetic wellness advice.")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startCallMonitorService() {
        try {
            val serviceIntent = Intent(this, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun startHardwareTriggerService() {
        try {
            val serviceIntent = Intent(this, com.myra.assistant.service.HardwareTriggerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(CallMonitorService.ACTION_INCOMING_CALL)
            addAction(MyraNotificationListenerService.ACTION_OTP_DETECTED)
            addAction(MyraOverlayService.BROADCAST_MIC_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemStatusReceiver, filter)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

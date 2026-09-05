package com.myra.assistant.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.BuildConfig
import com.example.R
import com.example.databinding.ActivitySettingsBinding
import com.myra.assistant.model.PrimeContact
import com.myra.assistant.service.AccessibilityHelperService

/**
 * Settings Activity for MYRA configuring AI engine, voice, personality,
 * Prime contacts, smart modes, and system permissions.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val primeContacts = mutableListOf<PrimeContact>()
    private lateinit var primeAdapter: PrimeContactsAdapter

    private val modelsList = listOf(
        "Native Audio (Human Voice) — DEFAULT" to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Flash Live (Fast)" to "models/gemini-2.0-flash-live-001",
        "Pro Audio Dialog" to "models/gemini-2.5-flash-preview-native-audio-dialog"
    )

    private val voicesList = listOf(
        "Aoede (Warm Female — Default)",
        "Charon (Deep Male)",
        "Kore (Bright Female)",
        "Fenrir (Intense Male)",
        "Puck (Playful Neutral)",
        "Leda (Calm Female)",
        "Orus (Bold Male)",
        "Zephyr (Gentle Neutral)"
    )

    private val smartModes = listOf(
        "Normal Standby Mode",
        "Driving Mode (Hands-free + Volume Boost)",
        "Sleep Mode (Low Volume + Whispering)",
        "Work Mode (Spam Filter + Focus)",
        "Gaming Mode (DND + Quick Responses)",
        "Anime Companion Mode",
        "Coding Companion Mode"
    )

    private val orbSizeOptions = listOf(
        "Medium (Standard 110dp) — Recommended" to "medium",
        "Small (Compact 90dp)" to "small",
        "Large (Expanded 135dp)" to "large"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupPrimeContactsRecycler()
        loadSavedPreferences()
        setupListeners()
        updateAccessibilityStatus()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun setupSpinners() {
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelsList.map { it.first })
        binding.spinnerModel.adapter = modelAdapter

        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voicesList)
        binding.spinnerVoice.adapter = voiceAdapter

        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, smartModes)
        binding.spinnerSmartMode.adapter = modeAdapter

        val orbSizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, orbSizeOptions.map { it.first })
        binding.spinnerOrbSize.adapter = orbSizeAdapter
    }

    private fun setupPrimeContactsRecycler() {
        primeAdapter = PrimeContactsAdapter(primeContacts) { position ->
            primeContacts.removeAt(position)
            primeAdapter.notifyItemRemoved(position)
        }
        binding.recyclerPrimeContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerPrimeContacts.adapter = primeAdapter
    }

    private fun loadSavedPreferences() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        // API Key
        val savedKey = prefs.getString("api_key", "") ?: ""
        if (savedKey.isNotEmpty()) {
            binding.editApiKey.setText(savedKey)
        } else {
            try {
                if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                    binding.editApiKey.setText(BuildConfig.GEMINI_API_KEY)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // User Name
        binding.editUserName.setText(prefs.getString("user_name", "Boss"))

        // Model
        val savedModel = prefs.getString("gemini_model", modelsList[0].second)
        val modelIndex = modelsList.indexOfFirst { it.second == savedModel }.coerceAtLeast(0)
        binding.spinnerModel.setSelection(modelIndex)

        // Voice
        val savedVoice = prefs.getString("gemini_voice", "Aoede")
        val voiceIndex = voicesList.indexOfFirst { it.startsWith(savedVoice ?: "Aoede") }.coerceAtLeast(0)
        binding.spinnerVoice.setSelection(voiceIndex)

        // Personality
        when (prefs.getString("personality_mode", "gf")) {
            "professional" -> binding.radioProfessional.isChecked = true
            "assistant" -> binding.radioAssistant.isChecked = true
            else -> binding.radioGf.isChecked = true
        }

        // 1. Hardware & Sensor Controls
        binding.switchShakeSummon.isChecked = prefs.getBoolean("shake_to_summon", true)
        binding.switchFlipMute.isChecked = prefs.getBoolean("flip_to_mute", true)

        // 2. Call & Notification Reader
        binding.switchSmartCall.isChecked = prefs.getBoolean("smart_call_prompt", true)
        binding.switchNotifReader.isChecked = prefs.getBoolean("notif_reader_enabled", true)

        // 3. Display & Performance Boost
        binding.switchPerformanceBoost.isChecked = prefs.getBoolean("performance_boost", true)

        // 4. Security & Anti-Theft Guard
        binding.switchAntiTheft.isChecked = prefs.getBoolean("anti_theft_unplug", false)
        binding.switchPanicSos.isChecked = prefs.getBoolean("panic_sos_enabled", true)

        // 5. Floating Overlay & Edge Lighting
        binding.switchFloatingOverlay.isChecked = prefs.getBoolean("floating_overlay_enabled", true)
        binding.switchEdgeLighting.isChecked = prefs.getBoolean("overlay_edge_lighting_enabled", true)
        val savedOrbSize = prefs.getString("overlay_orb_size", "medium") ?: "medium"
        val orbIndex = orbSizeOptions.indexOfFirst { it.second == savedOrbSize }
        if (orbIndex >= 0) {
            binding.spinnerOrbSize.setSelection(orbIndex)
        }

        // Prime Contacts
        val rawContacts = prefs.getString("prime_contacts", "") ?: ""
        primeContacts.clear()
        if (rawContacts.isNotBlank()) {
            rawContacts.split(";").forEach { line ->
                val parts = line.split("|")
                if (parts.size == 2) {
                    primeContacts.add(PrimeContact(parts[0], parts[1]))
                }
            }
        }
        if (primeContacts.isEmpty()) {
            // Default sample prime contact for seamless demonstration
            primeContacts.add(PrimeContact("Priya", "+919876543210"))
        }
        primeAdapter.notifyDataSetChanged()
    }

    private fun setupListeners() {
        binding.settingsBackBtn.setOnClickListener {
            finish()
        }

        binding.btnAddPrimeContact.setOnClickListener {
            showAddContactDialog()
        }

        binding.cardAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.cardAllPermissions.setOnClickListener {
            startActivity(Intent(this, com.myra.assistant.ui.permissions.PermissionsActivity::class.java))
        }

        binding.btnSaveSettings.setOnClickListener {
            saveAllPreferences()
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_prime_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.dialogNameInput)
        val numberInput = dialogView.findViewById<EditText>(R.id.dialogNumberInput)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("ADD") { _, _ ->
                val name = nameInput.text.toString().trim()
                val number = numberInput.text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    primeContacts.add(PrimeContact(name, number))
                    primeAdapter.notifyItemInserted(primeContacts.size - 1)
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun updateAccessibilityStatus() {
        val active = AccessibilityHelperService.isRunning()
        if (active) {
            binding.textAccessibilityStatus.text = "Status: ACTIVE ✅ (Full UI automation enabled)"
            binding.textAccessibilityStatus.setTextColor(Color.parseColor("#00E676"))
            binding.badgeAccessibilityIcon.text = "⚡"
        } else {
            binding.textAccessibilityStatus.text = "Status: INACTIVE ❌ (Tap to enable MYRA service)"
            binding.textAccessibilityStatus.setTextColor(Color.parseColor("#FF1744"))
            binding.badgeAccessibilityIcon.text = "⚠️"
        }
    }

    private fun saveAllPreferences() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        val selectedModel = modelsList[binding.spinnerModel.selectedItemPosition].second
        val fullVoice = voicesList[binding.spinnerVoice.selectedItemPosition]
        val voiceName = fullVoice.substringBefore(" ")

        val personality = when {
            binding.radioProfessional.isChecked -> "professional"
            binding.radioAssistant.isChecked -> "assistant"
            else -> "gf"
        }

        val contactsRaw = primeContacts.joinToString(";") { "${it.name}|${it.number}" }

        prefs.edit()
            .putString("api_key", binding.editApiKey.text.toString().trim())
            .putString("user_name", binding.editUserName.text.toString().trim())
            .putString("gemini_model", selectedModel)
            .putString("gemini_voice", voiceName)
            .putString("personality_mode", personality)
            .putBoolean("shake_to_summon", binding.switchShakeSummon.isChecked)
            .putBoolean("flip_to_mute", binding.switchFlipMute.isChecked)
            .putBoolean("smart_call_prompt", binding.switchSmartCall.isChecked)
            .putBoolean("notif_reader_enabled", binding.switchNotifReader.isChecked)
            .putBoolean("performance_boost", binding.switchPerformanceBoost.isChecked)
            .putBoolean("anti_theft_unplug", binding.switchAntiTheft.isChecked)
            .putBoolean("panic_sos_enabled", binding.switchPanicSos.isChecked)
            .putBoolean("floating_overlay_enabled", binding.switchFloatingOverlay.isChecked)
            .putBoolean("overlay_edge_lighting_enabled", binding.switchEdgeLighting.isChecked)
            .putString("overlay_orb_size", orbSizeOptions[binding.spinnerOrbSize.selectedItemPosition].second)
            .putString("prime_contacts", contactsRaw)
            .apply()

        Toast.makeText(this, "Settings saved! Restarting assistant engine...", Toast.LENGTH_SHORT).show()
        finish()
    }

    class PrimeContactsAdapter(
        private val list: List<PrimeContact>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<PrimeContactsAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.primeItemName)
            val number: TextView = v.findViewById(R.id.primeItemNumber)
            val deleteBtn: ImageButton = v.findViewById(R.id.primeItemDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prime_contact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.name.text = item.name
            holder.number.text = item.number
            holder.deleteBtn.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(pos)
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}

package com.myra.assistant.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.databinding.ActivityPermissionsBinding
import com.myra.assistant.service.AccessibilityHelperService

/**
 * All-in-One Permissions Setup Manager for MYRA.
 * Checks, displays, and requests all runtime and special system permissions
 * in one unified dashboard screen.
 */
class PermissionsActivity : AppCompatActivity() {

    companion object {
        val REQUIRED_RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun hasAllCorePermissions(context: Context): Boolean {
            val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val phone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            return audio && phone
        }

        fun start(context: Context) {
            val intent = Intent(context, PermissionsActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityPermissionsBinding
    private val permissionItems = mutableListOf<PermissionGroup>()
    private lateinit var adapter: PermissionAdapter

    data class PermissionGroup(
        val id: String,
        val title: String,
        val desc: String,
        val icon: String,
        val permissions: List<String>,
        var isGranted: Boolean = false
    )

    private val multiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAllStatuses()
    }

    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAllStatuses()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPermissionGroups()
        setupRecyclerView()
        setupListeners()
        refreshAllStatuses()
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatuses()
    }

    private fun initPermissionGroups() {
        permissionItems.clear()

        // 1. Microphone
        permissionItems.add(
            PermissionGroup(
                id = "audio",
                title = "Record Audio (Voice Engine)",
                desc = "Bidirectional voice speech input for Gemini Live assistant",
                icon = "🎤",
                permissions = listOf(Manifest.permission.RECORD_AUDIO)
            )
        )

        // 2. Camera
        permissionItems.add(
            PermissionGroup(
                id = "camera",
                title = "Camera Vision & OCR",
                desc = "Scene visual inspection and real-time document OCR",
                icon = "📷",
                permissions = listOf(Manifest.permission.CAMERA)
            )
        )

        // 3. Contacts
        permissionItems.add(
            PermissionGroup(
                id = "contacts",
                title = "Read Contacts",
                desc = "Caller ID resolution and hands-free Prime Contact calling",
                icon = "👥",
                permissions = listOf(Manifest.permission.READ_CONTACTS)
            )
        )

        // 4. Phone & Calling
        val phonePerms = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        permissionItems.add(
            PermissionGroup(
                id = "phone",
                title = "Phone Calling & State",
                desc = "Direct voice calling and incoming call detection/answering",
                icon = "📞",
                permissions = phonePerms
            )
        )

        // 5. SMS & OTP
        permissionItems.add(
            PermissionGroup(
                id = "sms",
                title = "SMS Messaging & OTP",
                desc = "Send texts by voice & read incoming verification codes",
                icon = "💬",
                permissions = listOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            )
        )

        // 6. Location
        permissionItems.add(
            PermissionGroup(
                id = "location",
                title = "Precise & Coarse Location",
                desc = "Local place navigation, driving mode & weather updates",
                icon = "📍",
                permissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        )

        // 7. Storage / Media
        val storagePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        val storageTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "Storage & Notifications"
        } else {
            "Device Storage & Cache"
        }
        permissionItems.add(
            PermissionGroup(
                id = "storage",
                title = storageTitle,
                desc = "Storage analysis, junk cache cleaning and system alerts",
                icon = "💾",
                permissions = storagePerms
            )
        )
    }

    private fun setupRecyclerView() {
        adapter = PermissionAdapter(permissionItems) { group ->
            val ungranted = group.permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (ungranted.isNotEmpty()) {
                singlePermissionLauncher.launch(ungranted.toTypedArray())
            } else {
                Toast.makeText(this, "${group.title} is already active! ✅", Toast.LENGTH_SHORT).show()
            }
        }
        binding.recyclerRuntimePermissions.layoutManager = LinearLayoutManager(this)
        binding.recyclerRuntimePermissions.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnGrantAllRuntime.setOnClickListener {
            requestAllMissingRuntimePermissions()
        }

        // Special Permission: Accessibility
        binding.btnToggleAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Special Permission: Notification Listener
        binding.btnToggleNotificationListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Special Permission: Draw Overlays
        binding.btnToggleOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Special Permission: Battery Exemption
        binding.btnToggleBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            }
        }

        binding.btnCompletePermissions.setOnClickListener {
            finish()
        }
    }

    private fun requestAllMissingRuntimePermissions() {
        val allNeeded = mutableSetOf<String>()

        REQUIRED_RUNTIME_PERMISSIONS.forEach { perm ->
            // On API 33+, skip external storage if deprecated, ensure notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (perm != Manifest.permission.READ_EXTERNAL_STORAGE && perm != Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                        allNeeded.add(perm)
                    }
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    allNeeded.add(perm)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                allNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (allNeeded.isNotEmpty()) {
            multiPermissionLauncher.launch(allNeeded.toTypedArray())
        } else {
            Toast.makeText(this, "All runtime permissions are already granted! 🎉", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAllStatuses() {
        var grantedCount = 0
        var totalCount = 0

        // 1. Runtime Permissions
        permissionItems.forEach { group ->
            val allGranted = group.permissions.all { perm ->
                ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            }
            group.isGranted = allGranted
            totalCount++
            if (allGranted) grantedCount++
        }
        adapter.notifyDataSetChanged()

        // 2. Accessibility
        val isAccessibilityActive = AccessibilityHelperService.isRunning()
        totalCount++
        if (isAccessibilityActive) grantedCount++
        if (isAccessibilityActive) {
            binding.statusAccessibility.text = "Status: ACTIVE ✅ Full UI automation active"
            binding.statusAccessibility.setTextColor(Color.parseColor("#00E676"))
            binding.btnToggleAccessibility.text = "CONFIGURED"
            binding.btnToggleAccessibility.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.statusAccessibility.text = "Status: INACTIVE ⚠️ Tap to enable MYRA service"
            binding.statusAccessibility.setTextColor(Color.parseColor("#FF6D00"))
            binding.btnToggleAccessibility.text = "CONFIGURE"
            binding.btnToggleAccessibility.setTextColor(Color.parseColor("#FFD700"))
        }

        // 3. Notification Listener
        val isNotificationListenerActive = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        totalCount++
        if (isNotificationListenerActive) grantedCount++
        if (isNotificationListenerActive) {
            binding.statusNotificationListener.text = "Status: ACTIVE ✅ Reading OTPs & filtering spam"
            binding.statusNotificationListener.setTextColor(Color.parseColor("#00E676"))
            binding.btnToggleNotificationListener.text = "CONFIGURED"
            binding.btnToggleNotificationListener.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.statusNotificationListener.text = "Status: INACTIVE ⚠️ Tap to enable notification access"
            binding.statusNotificationListener.setTextColor(Color.parseColor("#FF6D00"))
            binding.btnToggleNotificationListener.text = "ENABLE"
            binding.btnToggleNotificationListener.setTextColor(Color.parseColor("#FFD700"))
        }

        // 4. Overlay Permission
        val isOverlayActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        totalCount++
        if (isOverlayActive) grantedCount++
        if (isOverlayActive) {
            binding.statusOverlay.text = "Status: ACTIVE ✅ Floating Orb available anywhere"
            binding.statusOverlay.setTextColor(Color.parseColor("#00E676"))
            binding.btnToggleOverlay.text = "ALLOWED"
            binding.btnToggleOverlay.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.statusOverlay.text = "Status: INACTIVE ⚠️ Tap to grant draw over other apps"
            binding.statusOverlay.setTextColor(Color.parseColor("#FF6D00"))
            binding.btnToggleOverlay.text = "ALLOW"
            binding.btnToggleOverlay.setTextColor(Color.parseColor("#FFD700"))
        }

        // 5. Battery Optimization Exemption
        val isBatteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true
        totalCount++
        if (isBatteryExempt) grantedCount++
        if (isBatteryExempt) {
            binding.statusBattery.text = "Status: EXEMPTED ✅ Unrestricted 24/7 background standby"
            binding.statusBattery.setTextColor(Color.parseColor("#00E676"))
            binding.btnToggleBattery.text = "EXEMPTED"
            binding.btnToggleBattery.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.statusBattery.text = "Status: OPTIMIZED ⚠️ Tap to prevent OS from killing MYRA"
            binding.statusBattery.setTextColor(Color.parseColor("#FF6D00"))
            binding.btnToggleBattery.text = "EXEMPT"
            binding.btnToggleBattery.setTextColor(Color.parseColor("#FFD700"))
        }

        // 6. Header HUD calculation
        val percent = if (totalCount > 0) (grantedCount * 100) / totalCount else 0
        binding.permissionProgressBar.progress = percent
        binding.textOverallPercent.text = "$percent%"

        binding.textSummaryCount.text = "$grantedCount of $totalCount capabilities active"
        if (percent == 100) {
            binding.textSummaryTitle.text = "ALL SYSTEMS READY ✅"
            binding.textSummaryTitle.setTextColor(Color.parseColor("#00E676"))
            binding.textOverallPercent.setTextColor(Color.parseColor("#00E676"))
            binding.btnGrantAllRuntime.text = "ALL RUNTIME PERMISSIONS GRANTED ✅"
            binding.btnGrantAllRuntime.setBackgroundColor(Color.parseColor("#1B5E20"))
            binding.btnGrantAllRuntime.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.textSummaryTitle.text = "ACTION REQUIRED — $percent% CONFIGURED"
            binding.textSummaryTitle.setTextColor(Color.parseColor("#FFD700"))
            binding.textOverallPercent.setTextColor(Color.parseColor("#FFD700"))
            binding.btnGrantAllRuntime.text = "GRANT ALL RUNTIME PERMISSIONS ⚡"
            binding.btnGrantAllRuntime.setBackgroundColor(Color.parseColor("#FF6D00"))
            binding.btnGrantAllRuntime.setTextColor(Color.parseColor("#050505"))
        }
    }

    class PermissionAdapter(
        private val items: List<PermissionGroup>,
        private val onItemClick: (PermissionGroup) -> Unit
    ) : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.textPermIcon)
            val title: TextView = v.findViewById(R.id.textPermTitle)
            val desc: TextView = v.findViewById(R.id.textPermDesc)
            val badge: TextView = v.findViewById(R.id.badgeStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_permission_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.text = item.icon
            holder.title.text = item.title
            holder.desc.text = item.desc

            if (item.isGranted) {
                holder.badge.text = "GRANTED ✅"
                holder.badge.setTextColor(Color.parseColor("#00E676"))
                holder.badge.setBackgroundColor(Color.parseColor("#1A00E676"))
            } else {
                holder.badge.text = "GRANT ⚡"
                holder.badge.setTextColor(Color.parseColor("#FFD700"))
                holder.badge.setBackgroundColor(Color.parseColor("#26FFD700"))
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}

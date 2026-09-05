package com.myra.assistant.ui.memory

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.databinding.ActivityMemoriesBinding
import com.myra.assistant.data.memory.MemoryEntity
import com.myra.assistant.data.memory.MemoryRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoriesBinding
    private lateinit var repository: MemoryRepository
    private val memoryList = mutableListOf<MemoryEntity>()
    private lateinit var adapter: MemoriesAdapter
    private var isVaultUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MemoryRepository(this)
        setupRecyclerView()
        setupListeners()
        observeMemories()
    }

    private fun setupRecyclerView() {
        adapter = MemoriesAdapter(
            memories = memoryList,
            onDelete = { memory ->
                lifecycleScope.launch {
                    repository.deleteMemory(memory.id)
                    Toast.makeText(this@MemoriesActivity, "Memory erased", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerMemories.layoutManager = LinearLayoutManager(this)
        binding.recyclerMemories.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddMemory.setOnClickListener {
            showAddMemoryDialog()
        }

        binding.btnUnlockVault.setOnClickListener {
            showVaultAuthDialog()
        }
    }

    private fun observeMemories() {
        lifecycleScope.launch {
            repository.getAllMemoriesFlow().collectLatest { all ->
                memoryList.clear()
                val filtered = if (isVaultUnlocked) {
                    all
                } else {
                    all.filter { !it.isSecureVault }
                }
                memoryList.addAll(filtered)
                adapter.notifyDataSetChanged()

                binding.emptyStateText.visibility = if (memoryList.isEmpty()) View.VISIBLE else View.GONE
                binding.memoriesCountText.text = "${memoryList.size} MEMORIES STORED"
            }
        }
    }

    private fun showAddMemoryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_memory, null)
        val editKey = dialogView.findViewById<EditText>(R.id.editMemoryKey)
        val editContent = dialogView.findViewById<EditText>(R.id.editMemoryContent)
        val spinnerCat = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val checkVault = dialogView.findViewById<android.widget.CheckBox>(R.id.checkSecureVault)

        AlertDialog.Builder(this)
            .setTitle("Add Memory to MYRA")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val key = editKey.text.toString().trim()
                val content = editContent.text.toString().trim()
                val cat = spinnerCat.selectedItem?.toString() ?: "General"
                val isVault = checkVault.isChecked

                if (key.isNotEmpty() && content.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.saveMemory(key, content, cat, isVault)
                        Toast.makeText(this@MemoriesActivity, "Memory remembered by MYRA ✨", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please fill key and content", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVaultAuthDialog() {
        val input = EditText(this).apply {
            hint = "Enter PIN (Default: 1234)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(40, 20, 40, 20)
            setTextColor(Color.WHITE)
        }

        AlertDialog.Builder(this)
            .setTitle("🔒 Secure Vault Authentication")
            .setMessage("Enter your 4-digit PIN to access encrypted credentials and private memories:")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val pin = input.text.toString().trim()
                val prefs = getSharedPreferences("myra_prefs", MODE_PRIVATE)
                val savedPin = prefs.getString("vault_pin", "1234") ?: "1234"

                if (pin == savedPin) {
                    isVaultUnlocked = true
                    binding.btnUnlockVault.text = "🔒 Vault Unlocked"
                    binding.btnUnlockVault.setBackgroundColor(Color.parseColor("#00E676"))
                    observeMemories()
                    Toast.makeText(this, "Secure Vault Unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class MemoriesAdapter(
    private val memories: List<MemoryEntity>,
    private val onDelete: (MemoryEntity) -> Unit
) : RecyclerView.Adapter<MemoriesAdapter.MemoryViewHolder>() {

    class MemoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.memoryKeyText)
        val contentText: TextView = view.findViewById(R.id.memoryContentText)
        val categoryBadge: TextView = view.findViewById(R.id.memoryCategoryBadge)
        val dateText: TextView = view.findViewById(R.id.memoryDateText)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDeleteMemory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_card, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = memories[position]
        holder.titleText.text = if (memory.isSecureVault) "🔒 ${memory.keyName}" else memory.keyName
        holder.contentText.text = memory.content
        holder.categoryBadge.text = memory.category.uppercase(Locale.ROOT)
        
        val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(memory.timestamp))
        holder.dateText.text = date

        holder.deleteBtn.setOnClickListener {
            onDelete(memory)
        }
    }

    override fun getItemCount(): Int = memories.size
}

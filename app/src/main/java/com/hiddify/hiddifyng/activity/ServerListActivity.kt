package com.hiddify.hiddifyng.activity

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.viewmodel.MainViewModel

class ServerListActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var serverAdapter: ServerAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_list)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        
        // Set up UI components
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        recyclerView = findViewById(R.id.server_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        
        // Set up RecyclerView
        serverAdapter = ServerAdapter(
            onItemClick = { server -> 
                startVpnWithServer(server)
            },
            onEditClick = { server ->
                showEditServerDialog(server)
            },
            onDeleteClick = { server ->
                showDeleteConfirmation(server)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ServerListActivity)
            adapter = serverAdapter
            addItemDecoration(DividerItemDecoration(this@ServerListActivity, DividerItemDecoration.VERTICAL))
        }
        
        // Set up FAB for adding new server
        val fab: FloatingActionButton = findViewById(R.id.fab_add_server)
        fab.setOnClickListener {
            showAddServerDialog()
        }
        
        // Observe servers list
        viewModel.allServers.observe(this) { servers ->
            serverAdapter.submitList(servers)
            updateEmptyView(servers.isEmpty())
        }
        
        // Observe action status
        viewModel.actionStatus.observe(this) { status ->
            handleActionStatus(status)
        }
    }
    
    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    
    private fun handleActionStatus(status: MainViewModel.ActionStatus) {
        when (status) {
            is MainViewModel.ActionStatus.LOADING -> {
                // Show loading indicator
                // This would be implemented in a real app
            }
            is MainViewModel.ActionStatus.SUCCESS -> {
                // Clear loading indicator
            }
            is MainViewModel.ActionStatus.SUCCESS_WITH_DATA -> {
                showSnackbar(status.message)
            }
            is MainViewModel.ActionStatus.ERROR -> {
                showSnackbar(status.message)
            }
        }
    }
    
    private fun startVpnWithServer(server: Server) {
        viewModel.startVpn(server.id)
        finish()
    }
    
    private fun showAddServerDialog() {
        val options = arrayOf(
            getString(R.string.manual_input),
            getString(R.string.qr_code_scan),
            getString(R.string.import_from_clipboard),
            getString(R.string.import_from_subscription)
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_server))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showManualServerInputDialog()
                    1 -> startQrCodeScan()
                    2 -> importFromClipboard()
                    3 -> showSubscriptionInputDialog()
                }
            }
            .show()
    }
    
    private fun showManualServerInputDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_manual_server_input, null)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_server))
            .setView(view)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                // Get values from input fields
                val nameInput = view.findViewById<TextInputEditText>(R.id.server_name_input)
                val addressInput = view.findViewById<TextInputEditText>(R.id.server_address_input)
                val portInput = view.findViewById<TextInputEditText>(R.id.server_port_input)
                val protocolInput = view.findViewById<TextInputEditText>(R.id.server_protocol_input)
                
                val name = nameInput.text.toString()
                val address = addressInput.text.toString()
                val port = portInput.text.toString().toIntOrNull() ?: 443
                val protocol = protocolInput.text.toString()
                
                if (name.isNotEmpty() && address.isNotEmpty() && protocol.isNotEmpty()) {
                    val server = Server(
                        name = name,
                        address = address,
                        port = port,
                        protocol = protocol
                    )
                    viewModel.addServer(server)
                } else {
                    showSnackbar(getString(R.string.invalid_server_details))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showEditServerDialog(server: Server) {
        val view = layoutInflater.inflate(R.layout.dialog_manual_server_input, null)
        
        // Populate fields with server data
        view.findViewById<TextInputEditText>(R.id.server_name_input).setText(server.name)
        view.findViewById<TextInputEditText>(R.id.server_address_input).setText(server.address)
        view.findViewById<TextInputEditText>(R.id.server_port_input).setText(server.port.toString())
        view.findViewById<TextInputEditText>(R.id.server_protocol_input).setText(server.protocol)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_server))
            .setView(view)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                // Get updated values
                val nameInput = view.findViewById<TextInputEditText>(R.id.server_name_input)
                val addressInput = view.findViewById<TextInputEditText>(R.id.server_address_input)
                val portInput = view.findViewById<TextInputEditText>(R.id.server_port_input)
                val protocolInput = view.findViewById<TextInputEditText>(R.id.server_protocol_input)
                
                val name = nameInput.text.toString()
                val address = addressInput.text.toString()
                val port = portInput.text.toString().toIntOrNull() ?: 443
                val protocol = protocolInput.text.toString()
                
                if (name.isNotEmpty() && address.isNotEmpty() && protocol.isNotEmpty()) {
                    // Create updated server (keeping other properties unchanged)
                    val updatedServer = server.copy(
                        name = name,
                        address = address,
                        port = port,
                        protocol = protocol
                    )
                    viewModel.updateServer(updatedServer)
                } else {
                    showSnackbar(getString(R.string.invalid_server_details))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showDeleteConfirmation(server: Server) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_server))
            .setMessage(getString(R.string.delete_server_confirmation, server.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteServer(server)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun startQrCodeScan() {
        // This would launch QR code scanner in a real implementation
        showSnackbar(getString(R.string.qr_scan_not_implemented))
    }
    
    private fun importFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text.toString()
            
            if (text.startsWith("vmess://") || text.startsWith("vless://") || 
                text.startsWith("trojan://") || text.startsWith("ss://") || 
                text.startsWith("hysteria://")) {
                // Import server from URI
                viewModel.importFromServerUrl(text)
            } else if (text.startsWith("http://") || text.startsWith("https://")) {
                // Could be subscription URL
                showSubscriptionImportDialog(text)
            } else {
                showSnackbar(getString(R.string.invalid_clipboard_content))
            }
        } else {
            showSnackbar(getString(R.string.clipboard_empty))
        }
    }
    
    private fun showSubscriptionInputDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_subscription_input, null)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_subscription))
            .setView(view)
            .setPositiveButton(getString(R.string.import_)) { _, _ ->
                val urlInput = view.findViewById<TextInputEditText>(R.id.subscription_url_input)
                val url = urlInput.text.toString()
                
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    viewModel.importFromUrl(url, null)
                } else {
                    showSnackbar(getString(R.string.invalid_subscription_url))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showSubscriptionImportDialog(url: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_subscription))
            .setMessage(getString(R.string.import_subscription_prompt, url))
            .setPositiveButton(getString(R.string.import_)) { _, _ ->
                viewModel.importFromUrl(url, null)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.server_list_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_ping_all -> {
                viewModel.pingAllServers()
                true
            }
            R.id.action_import_subscription -> {
                showSubscriptionInputDialog()
                true
            }
            R.id.action_sort_by_ping -> {
                // Sort servers by ping
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // RecyclerView Adapter for Server list
    private inner class ServerAdapter(
        private val onItemClick: (Server) -> Unit,
        private val onEditClick: (Server) -> Unit,
        private val onDeleteClick: (Server) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {
        
        private var servers: List<Server> = emptyList()
        
        fun submitList(newList: List<Server>) {
            servers = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false)
            return ServerViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
            val server = servers[position]
            holder.bind(server)
        }
        
        override fun getItemCount(): Int = servers.size
        
        inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameText: TextView = itemView.findViewById(R.id.server_name)
            private val addressText: TextView = itemView.findViewById(R.id.server_address)
            private val pingText: TextView = itemView.findViewById(R.id.server_ping)
            private val editButton: View = itemView.findViewById(R.id.edit_button)
            private val deleteButton: View = itemView.findViewById(R.id.delete_button)
            
            fun bind(server: Server) {
                nameText.text = server.name
                addressText.text = "${server.protocol}://${server.address}:${server.port}"
                
                if (server.ping > 0 && server.ping < Integer.MAX_VALUE) {
                    pingText.text = "${server.ping} ms"
                    pingText.visibility = View.VISIBLE
                } else {
                    pingText.visibility = View.GONE
                }
                
                itemView.setOnClickListener { onItemClick(server) }
                editButton.setOnClickListener { onEditClick(server) }
                deleteButton.setOnClickListener { onDeleteClick(server) }
            }
        }
    }
}

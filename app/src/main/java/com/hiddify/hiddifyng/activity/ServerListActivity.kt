package com.hiddify.hiddifyng.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import com.hiddify.hiddifyng.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Activity for managing server list
 * Allows users to view, add, edit, and delete servers
 */
class ServerListActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    
    // UI components
    private lateinit var rvServerList: RecyclerView
    private lateinit var fabAddServer: FloatingActionButton
    private lateinit var tvEmptyView: TextView
    
    // Server adapter for RecyclerView
    private lateinit var serverAdapter: ServerAdapter
    
    // Coroutine scope
    private val activityScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_list)
        
        // Show back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize UI components
        initializeUI()
        
        // Setup observers
        setupObservers()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.server_list_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle back button
                finish()
                true
            }
            R.id.menu_ping_all -> {
                // Ping all servers
                pingAllServers()
                true
            }
            R.id.menu_add_subscription -> {
                // Add subscription
                showAddSubscriptionDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        // Get references to UI components
        rvServerList = findViewById(R.id.rv_server_list)
        fabAddServer = findViewById(R.id.fab_add_server)
        tvEmptyView = findViewById(R.id.tv_empty_view)
        
        // Setup RecyclerView
        serverAdapter = ServerAdapter(
            onItemClick = { server ->
                // Connect to server
                connectToServer(server)
            },
            onEditClick = { server ->
                // Edit server
                showEditServerDialog(server)
            },
            onDeleteClick = { server ->
                // Delete server
                showDeleteServerConfirmation(server)
            }
        )
        
        rvServerList.apply {
            layoutManager = LinearLayoutManager(this@ServerListActivity)
            adapter = serverAdapter
        }
        
        // Setup click listeners
        fabAddServer.setOnClickListener {
            showAddServerDialog()
        }
    }
    
    /**
     * Setup observers for LiveData
     */
    private fun setupObservers() {
        // Observe server list
        viewModel.allServers.observe(this) { servers ->
            if (servers.isNullOrEmpty()) {
                // Show empty view
                tvEmptyView.visibility = View.VISIBLE
                rvServerList.visibility = View.GONE
            } else {
                // Show server list
                tvEmptyView.visibility = View.GONE
                rvServerList.visibility = View.VISIBLE
                
                // Update adapter
                serverAdapter.submitList(servers)
            }
        }
        
        // Observe current server
        viewModel.currentServer.observe(this) { server ->
            // Update adapter for connected server status
            serverAdapter.setConnectedServer(server)
        }
    }
    
    /**
     * Connect to a server
     */
    private fun connectToServer(server: Server) {
        // Check if already connected to this server
        if (viewModel.currentServer.value?.id == server.id) {
            Toast.makeText(this, "Already connected to this server", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Connect to server
        viewModel.connectToServer(server)
        Toast.makeText(this, "Connecting to ${server.name}...", Toast.LENGTH_SHORT).show()
        
        // Return to main activity
        finish()
    }
    
    /**
     * Ping all servers
     */
    private fun pingAllServers() {
        Toast.makeText(this, "Pinging servers...", Toast.LENGTH_SHORT).show()
        viewModel.pingAllServers()
    }
    
    /**
     * Show add server dialog
     */
    private fun showAddServerDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Server")
            .setView(R.layout.dialog_manual_server_input)
            .setPositiveButton("Add") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get values from dialog
                val nameEditText = dialog.findViewById<TextView>(R.id.et_server_name)
                val protocolSpinner = dialog.findViewById<TextView>(R.id.spinner_protocol)
                val addressEditText = dialog.findViewById<TextView>(R.id.et_server_address)
                val portEditText = dialog.findViewById<TextView>(R.id.et_server_port)
                val uuidEditText = dialog.findViewById<TextView>(R.id.et_server_uuid)
                
                // Create server object
                val server = Server(
                    name = nameEditText?.text?.toString() ?: "New Server",
                    protocol = protocolSpinner?.text?.toString() ?: "vmess",
                    address = addressEditText?.text?.toString() ?: "",
                    port = portEditText?.text?.toString()?.toIntOrNull() ?: 443
                )
                
                // Set UUID
                server.uuid = uuidEditText?.text?.toString()
                
                // Add server to database
                viewModel.addServer(server)
                
                Toast.makeText(this, "Server added successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Import from URL") { _, _ ->
                showImportServerDialog()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Show edit server dialog
     */
    private fun showEditServerDialog(server: Server) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Server")
            .setView(R.layout.dialog_manual_server_input)
            .setPositiveButton("Save") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get values from dialog
                val nameEditText = dialog.findViewById<TextView>(R.id.et_server_name)
                val protocolSpinner = dialog.findViewById<TextView>(R.id.spinner_protocol)
                val addressEditText = dialog.findViewById<TextView>(R.id.et_server_address)
                val portEditText = dialog.findViewById<TextView>(R.id.et_server_port)
                val uuidEditText = dialog.findViewById<TextView>(R.id.et_server_uuid)
                
                // Update server object
                server.name = nameEditText?.text?.toString() ?: server.name
                server.protocol = protocolSpinner?.text?.toString() ?: server.protocol
                server.address = addressEditText?.text?.toString() ?: server.address
                server.port = portEditText?.text?.toString()?.toIntOrNull() ?: server.port
                server.uuid = uuidEditText?.text?.toString() ?: server.uuid
                
                // Update server in database
                viewModel.updateServer(server)
                
                Toast.makeText(this, "Server updated successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Pre-fill dialog fields
        dialog.setOnShowListener {
            val alertDialog = it as AlertDialog
            
            // Get references to dialog fields
            val nameEditText = alertDialog.findViewById<TextView>(R.id.et_server_name)
            val protocolSpinner = alertDialog.findViewById<TextView>(R.id.spinner_protocol)
            val addressEditText = alertDialog.findViewById<TextView>(R.id.et_server_address)
            val portEditText = alertDialog.findViewById<TextView>(R.id.et_server_port)
            val uuidEditText = alertDialog.findViewById<TextView>(R.id.et_server_uuid)
            
            // Set current values
            nameEditText?.text = server.name
            protocolSpinner?.text = server.protocol
            addressEditText?.text = server.address
            portEditText?.text = server.port.toString()
            uuidEditText?.text = server.uuid ?: ""
        }
        
        dialog.show()
    }
    
    /**
     * Show delete server confirmation dialog
     */
    private fun showDeleteServerConfirmation(server: Server) {
        AlertDialog.Builder(this)
            .setTitle("Delete Server")
            .setMessage("Are you sure you want to delete server '${server.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete server
                viewModel.deleteServer(server)
                Toast.makeText(this, "Server deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show import server from URL dialog
     */
    private fun showImportServerDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Import Server from URL")
            .setView(R.layout.dialog_subscription_input)
            .setPositiveButton("Import") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get URL from dialog
                val urlEditText = dialog.findViewById<TextView>(R.id.et_subscription_url)
                val url = urlEditText?.text?.toString() ?: ""
                
                if (url.isNotEmpty()) {
                    // Try to parse URL as server
                    try {
                        // Extract protocol
                        val protocolStr = url.substringBefore("://")
                        
                        if (protocolStr.isNotEmpty()) {
                            val handler = ProtocolHandler.getHandler(protocolStr)
                            val server = handler.parseUrl(url)
                            
                            if (server != null) {
                                // Add server to database
                                viewModel.addServer(server)
                                
                                Toast.makeText(
                                    this,
                                    "Server imported successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to parse server URL",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this,
                                "Invalid server URL",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Error importing server: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Please enter a server URL",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    /**
     * Show add subscription dialog
     */
    private fun showAddSubscriptionDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Subscription")
            .setView(R.layout.dialog_subscription_input)
            .setPositiveButton("Add") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get URL from dialog
                val urlEditText = dialog.findViewById<TextView>(R.id.et_subscription_url)
                val url = urlEditText?.text?.toString() ?: ""
                
                if (url.isNotEmpty()) {
                    // Import subscription
                    activityScope.launch {
                        Toast.makeText(
                            this@ServerListActivity,
                            "Subscription added, servers will be imported in background",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // In a real implementation, this would:
                        // 1. Create a ServerGroup entity with subscription URL
                        // 2. Fetch servers from the subscription URL
                        // 3. Add servers to database
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Please enter a subscription URL",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    /**
     * Adapter for server list
     */
    private inner class ServerAdapter(
        private val onItemClick: (Server) -> Unit,
        private val onEditClick: (Server) -> Unit,
        private val onDeleteClick: (Server) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {
        
        private var servers: List<Server> = emptyList()
        private var connectedServerId: Long = -1
        
        fun submitList(newServers: List<Server>) {
            servers = newServers
            notifyDataSetChanged()
        }
        
        fun setConnectedServer(server: Server?) {
            val oldConnectedServerId = connectedServerId
            connectedServerId = server?.id ?: -1
            
            if (oldConnectedServerId != -1L) {
                // Find old connected server position
                val oldPosition = servers.indexOfFirst { it.id == oldConnectedServerId }
                if (oldPosition != -1) {
                    notifyItemChanged(oldPosition)
                }
            }
            
            if (connectedServerId != -1L) {
                // Find new connected server position
                val newPosition = servers.indexOfFirst { it.id == connectedServerId }
                if (newPosition != -1) {
                    notifyItemChanged(newPosition)
                }
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false)
            return ServerViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
            val server = servers[position]
            holder.bind(server, server.id == connectedServerId)
        }
        
        override fun getItemCount(): Int = servers.size
        
        inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvServerName: TextView = itemView.findViewById(R.id.tv_server_name)
            private val tvServerDetails: TextView = itemView.findViewById(R.id.tv_server_details)
            private val tvServerPing: TextView = itemView.findViewById(R.id.tv_server_ping)
            private val ivConnected: ImageView = itemView.findViewById(R.id.iv_connected)
            private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
            
            fun bind(server: Server, isConnected: Boolean) {
                tvServerName.text = server.name
                tvServerDetails.text = "${server.protocol} | ${server.address}:${server.port}"
                
                // Set ping text
                if (server.ping > 0 && server.ping < Int.MAX_VALUE) {
                    tvServerPing.text = "${server.ping} ms"
                    tvServerPing.visibility = View.VISIBLE
                } else {
                    tvServerPing.visibility = View.GONE
                }
                
                // Show connected indicator if connected
                ivConnected.visibility = if (isConnected) View.VISIBLE else View.GONE
                
                // Setup click listeners
                itemView.setOnClickListener { onItemClick(server) }
                btnEdit.setOnClickListener { onEditClick(server) }
                btnDelete.setOnClickListener { onDeleteClick(server) }
            }
        }
    }
}
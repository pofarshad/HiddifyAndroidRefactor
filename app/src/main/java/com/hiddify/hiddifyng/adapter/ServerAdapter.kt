package com.hiddify.hiddifyng.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.databinding.ItemServerBinding

/**
 * Adapter for displaying server list in RecyclerView
 */
class ServerAdapter(private val onServerClick: (Server) -> Unit) : 
    ListAdapter<Server, ServerAdapter.ServerViewHolder>(SERVER_COMPARATOR) {
    
    // Currently selected server
    private var selectedServerId: Long = -1
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServerViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = getItem(position)
        holder.bind(server, selectedServerId == server.id)
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onServerClick(server)
        }
    }
    
    /**
     * Set selected server
     * @param server Server to select
     */
    fun setSelectedServer(server: Server) {
        val oldSelectedId = selectedServerId
        selectedServerId = server.id
        
        // Update UI for old selected item
        if (oldSelectedId != -1L) {
            val oldPos = currentList.indexOfFirst { it.id == oldSelectedId }
            if (oldPos != -1) {
                notifyItemChanged(oldPos)
            }
        }
        
        // Update UI for new selected item
        val newPos = currentList.indexOfFirst { it.id == selectedServerId }
        if (newPos != -1) {
            notifyItemChanged(newPos)
            
            // Update server isSelected state
            val updatedList = currentList.map { 
                if (it.id == selectedServerId) {
                    it.copy(isSelected = true)
                } else {
                    it.copy(isSelected = false)
                }
            }
            submitList(updatedList)
        }
    }
    
    /**
     * ViewHolder for server item
     */
    inner class ServerViewHolder(private val binding: ItemServerBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        /**
         * Bind server data to view
         * @param server Server data
         * @param isSelected Whether this server is selected
         */
        fun bind(server: Server, isSelected: Boolean) {
            binding.apply {
                // Set server basic info
                serverName.text = server.name
                serverAddress.text = server.address
                protocolTag.text = server.protocol.uppercase()
                
                // Set ping value with color based on value
                server.avgPing?.let { ping ->
                    pingValue.text = "$ping ms"
                    pingValue.setTextColor(getPingColor(ping, binding.root.context))
                } ?: run {
                    pingValue.text = "-- ms"
                    pingValue.setTextColor(0xFFCCCCCC.toInt())
                }
                
                // Set selected indicator
                selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            }
        }
        
        /**
         * Get color for ping value based on ping time
         * @param ping Ping time in ms
         * @param context Context
         * @return Color resource ID
         */
        private fun getPingColor(ping: Int, context: android.content.Context): Int {
            return when {
                ping < 100 -> context.getColor(com.hiddify.hiddifyng.R.color.status_connected)
                ping < 200 -> context.getColor(com.hiddify.hiddifyng.R.color.status_connecting)
                else -> context.getColor(com.hiddify.hiddifyng.R.color.status_disconnected)
            }
        }
    }
    
    companion object {
        /**
         * DiffUtil for efficient RecyclerView updates
         */
        private val SERVER_COMPARATOR = object : DiffUtil.ItemCallback<Server>() {
            override fun areItemsTheSame(oldItem: Server, newItem: Server): Boolean {
                return oldItem.id == newItem.id
            }
            
            override fun areContentsTheSame(oldItem: Server, newItem: Server): Boolean {
                return oldItem == newItem
            }
        }
    }
}
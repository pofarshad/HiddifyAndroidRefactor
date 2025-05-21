package com.hiddify.hiddifyng.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.utils.RoutingManager
import com.hiddify.hiddifyng.utils.UpdateInfo
import com.hiddify.hiddifyng.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fragment displaying app information, version numbers, and update options
 */
class AboutFragment : Fragment() {

    private lateinit var xrayManager: XrayManager
    private lateinit var routingManager: RoutingManager
    private lateinit var updateManager: UpdateManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        xrayManager = XrayManager(requireContext())
        routingManager = RoutingManager(requireContext())
        updateManager = UpdateManager(requireContext())
        
        setupVersionInfo(view)
        setupButtons(view)
    }
    
    private fun setupVersionInfo(view: View) {
        // App version
        val appVersionView = view.findViewById<TextView>(R.id.app_version)
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 0
            )
            appVersionView.text = "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            appVersionView.text = "Version Unknown"
        }
        
        // Xray core version
        val xrayVersionView = view.findViewById<TextView>(R.id.xray_version)
        xrayVersionView.text = "Loading..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val xrayVersion = xrayManager.checkVersion()
                withContext(Dispatchers.Main) {
                    xrayVersionView.text = xrayVersion
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    xrayVersionView.text = "Unknown"
                }
            }
        }
        
        // Routing rules version
        val routingVersionView = view.findViewById<TextView>(R.id.routing_version)
        val lastUpdateTime = routingManager.getLastUpdateTime()
        
        if (lastUpdateTime > 0) {
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            routingVersionView.text = "Last updated: ${dateFormat.format(Date(lastUpdateTime))}"
        } else {
            routingVersionView.text = "Not yet updated"
        }
    }
    
    private fun setupButtons(view: View) {
        // Check for app updates
        view.findViewById<Button>(R.id.btn_check_app_updates).setOnClickListener {
            checkForAppUpdates()
        }
        
        // Update Xray core
        view.findViewById<Button>(R.id.btn_update_xray_core).setOnClickListener {
            updateXrayCore()
        }
        
        // Update routing rules
        view.findViewById<Button>(R.id.btn_update_routing_rules).setOnClickListener {
            updateRoutingRules()
        }
    }
    
    private fun checkForAppUpdates() {
        // Show a progress indicator
        Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val updatesAvailable = updateManager.checkForAppUpdates()
                
                withContext(Dispatchers.Main) {
                    if (updatesAvailable != null) {
                        showUpdateDialog(updatesAvailable)
                    } else {
                        Toast.makeText(
                            requireContext(), 
                            "You're running the latest version",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error checking for updates: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun updateXrayCore() {
        Toast.makeText(requireContext(), "Updating Xray Core...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val success = updateManager.updateXrayCore()
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        // Refresh the Xray version display
                        val xrayVersionView = view?.findViewById<TextView>(R.id.xray_version)
                        xrayVersionView?.text = xrayManager.checkVersion()
                        
                        Toast.makeText(
                            requireContext(),
                            "Xray Core updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Xray Core update failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error updating Xray Core: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun updateRoutingRules() {
        Toast.makeText(requireContext(), "Updating routing rules...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val success = routingManager.updateRoutingRules()
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        // Refresh the routing rules version display
                        val routingVersionView = view?.findViewById<TextView>(R.id.routing_version)
                        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
                        routingVersionView?.text = "Last updated: ${dateFormat.format(Date())}"
                        
                        Toast.makeText(
                            requireContext(),
                            "Routing rules updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Routing rules update failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error updating routing rules: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        // Create and show a dialog with update information
        // This would be implemented with Material AlertDialog or a custom fragment
        Toast.makeText(
            requireContext(),
            "New version available: ${updateInfo.version}",
            Toast.LENGTH_LONG
        ).show()
    }
}
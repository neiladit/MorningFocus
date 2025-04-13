package com.example.morningfocus.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages the list of user-defined blocked websites
 */
class BlockedSitesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Get the current list of blocked sites
     */
    fun getBlockedSites(): List<String> {
        val sitesJson = prefs.getString(KEY_BLOCKED_SITES, null) ?: return DEFAULT_BLOCKED_SITES
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson<List<String>>(sitesJson, type)
        } catch (e: Exception) {
            DEFAULT_BLOCKED_SITES
        }
    }
    
    /**
     * Add a site to the blocked list
     */
    fun addBlockedSite(site: String): Boolean {
        // Make sure the domain is properly formatted
        val formattedSite = formatSiteDomain(site)
        if (formattedSite.isEmpty()) {
            return false
        }
        
        val currentSites = getBlockedSites().toMutableList()
        
        // Check if site already exists
        if (currentSites.contains(formattedSite)) {
            return false
        }
        
        // Add the new site and save
        currentSites.add(formattedSite)
        return saveSites(currentSites)
    }
    
    /**
     * Remove a site from the blocked list
     */
    fun removeBlockedSite(site: String): Boolean {
        val currentSites = getBlockedSites().toMutableList()
        if (currentSites.remove(site)) {
            return saveSites(currentSites)
        }
        return false
    }
    
    /**
     * Save the entire list of blocked sites
     */
    private fun saveSites(sites: List<String>): Boolean {
        val sitesJson = gson.toJson(sites)
        return prefs.edit()
            .putString(KEY_BLOCKED_SITES, sitesJson)
            .commit()
    }
    
    /**
     * Format a site domain to ensure it's properly stored
     * Removes http://, https://, www. prefixes and any paths
     */
    fun formatSiteDomain(input: String): String {
        if (input.isBlank()) return ""
        
        var domain = input.trim().lowercase()
        
        // Remove protocol if present
        if (domain.startsWith("http://")) {
            domain = domain.substring(7)
        } else if (domain.startsWith("https://")) {
            domain = domain.substring(8)
        }
        
        // Remove www. if present
        if (domain.startsWith("www.")) {
            domain = domain.substring(4)
        }
        
        // Remove any paths (keep only domain)
        val pathIndex = domain.indexOf('/')
        if (pathIndex > 0) {
            domain = domain.substring(0, pathIndex)
        }
        
        return domain
    }
    
    companion object {
        private const val PREFS_NAME = "blocked_sites_prefs"
        private const val KEY_BLOCKED_SITES = "blocked_sites"
        private val DEFAULT_BLOCKED_SITES = listOf("reddit.com", "x.com", "twitter.com")
    }
} 
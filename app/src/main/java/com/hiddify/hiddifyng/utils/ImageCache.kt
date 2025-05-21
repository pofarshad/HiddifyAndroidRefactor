package com.hiddify.hiddifyng.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Advanced image caching system using both memory and disk cache
 * Significantly improves UI performance and reduces network usage
 */
class ImageCache(private val context: Context) {
    companion object {
        private const val TAG = "ImageCache"
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
        private const val MEMORY_CACHE_SIZE = 20 * 1024 * 1024 // 20MB in bytes
    }

    // Memory cache
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // The cache size will be measured in bytes
            return bitmap.byteCount
        }
    }

    // Disk cache directory
    private val cacheDir: File by lazy {
        File(context.cacheDir, "image_cache").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Load image from cache or network
     * @param url The URL of the image to load
     * @param width Target width for scaling (0 for original size)
     * @param height Target height for scaling (0 for original size)
     * @return The bitmap, or null if loading failed
     */
    suspend fun loadImage(url: String, width: Int = 0, height: Int = 0): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = getCacheKey(url)
            
            // Try memory cache first (fastest)
            val memCached = getBitmapFromMemCache(cacheKey)
            if (memCached != null) {
                Log.d(TAG, "Memory cache hit for $url")
                return@withContext memCached
            }
            
            // Try disk cache next
            val diskCached = getBitmapFromDiskCache(cacheKey, width, height)
            if (diskCached != null) {
                Log.d(TAG, "Disk cache hit for $url")
                // Store in memory cache for faster future access
                addBitmapToMemCache(cacheKey, diskCached)
                return@withContext diskCached
            }
            
            // Download from network
            Log.d(TAG, "Cache miss, downloading from network: $url")
            val downloaded = downloadBitmap(url)
            if (downloaded != null) {
                // Save to both caches
                saveBitmapToDiskCache(cacheKey, downloaded)
                
                // Scale if needed
                val scaled = if (width > 0 && height > 0) {
                    Bitmap.createScaledBitmap(downloaded, width, height, true)
                } else {
                    downloaded
                }
                
                addBitmapToMemCache(cacheKey, scaled)
                return@withContext scaled
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image from $url", e)
            null
        }
    }
    
    /**
     * Get bitmap from memory cache
     */
    private fun getBitmapFromMemCache(key: String): Bitmap? {
        return memoryCache.get(key)
    }
    
    /**
     * Add bitmap to memory cache
     */
    private fun addBitmapToMemCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }
    
    /**
     * Get bitmap from disk cache
     */
    private fun getBitmapFromDiskCache(key: String, width: Int, height: Int): Bitmap? {
        val file = File(cacheDir, key)
        if (!file.exists()) return null
        
        return try {
            val options = BitmapFactory.Options().apply {
                if (width > 0 && height > 0) {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.path, this)
                    
                    // Calculate sample size
                    inSampleSize = calculateInSampleSize(this, width, height)
                    inJustDecodeBounds = false
                }
            }
            
            BitmapFactory.decodeFile(file.path, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading bitmap from disk cache", e)
            null
        }
    }
    
    /**
     * Save bitmap to disk cache
     */
    private fun saveBitmapToDiskCache(key: String, bitmap: Bitmap) {
        val file = File(cacheDir, key)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bitmap to disk cache", e)
        }
    }
    
    /**
     * Download bitmap from network
     */
    private fun downloadBitmap(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000  // 10s timeout
            connection.readTimeout = 15000     // 15s read timeout
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error downloading image: HTTP $responseCode")
                return null
            }
            
            return BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image from $urlString", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Calculate optimal sample size for scaling
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Get a unique cache key for a URL
     */
    private fun getCacheKey(url: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(url.toByteArray())
        val bytes = digest.digest()
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Clear all cached images
     */
    fun clearCache() {
        // Clear memory cache
        memoryCache.evictAll()
        
        // Clear disk cache
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
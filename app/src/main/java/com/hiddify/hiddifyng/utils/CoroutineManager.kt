package com.hiddify.hiddifyng.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlin.coroutines.CoroutineContext

/**
 * Centralized coroutine manager to improve performance and prevent memory leaks
 * This helps track and properly cancel coroutines when they're no longer needed
 */
object CoroutineManager {
    // Application-level scope that lives for the duration of the app
    // Uses SupervisorJob so failures in one coroutine don't affect others
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Scope for UI-related operations
    val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Scope for IO operations (database, network, file)
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Scope for CPU-intensive operations
    val computationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Custom scope with specific context
    fun createScope(context: CoroutineContext): CoroutineScope {
        return CoroutineScope(SupervisorJob() + context)
    }
    
    // Cancel all coroutines in application scope (use carefully)
    fun cancelAllApplicationCoroutines() {
        applicationScope.coroutineContext.cancelChildren()
    }
    
    // Cancel all coroutines in main scope
    fun cancelAllMainCoroutines() {
        mainScope.coroutineContext.cancelChildren()
    }
    
    // Cancel all coroutines in IO scope
    fun cancelAllIoCoroutines() {
        ioScope.coroutineContext.cancelChildren()
    }
    
    // Cancel all coroutines in computation scope
    fun cancelAllComputationCoroutines() {
        computationScope.coroutineContext.cancelChildren()
    }
}
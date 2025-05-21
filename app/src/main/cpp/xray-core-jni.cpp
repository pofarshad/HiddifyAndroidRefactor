#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <dirent.h>
#include <sys/system_properties.h>

#define LOG_TAG "XrayCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Store Xray process ID
static pid_t xray_pid = -1;
static pthread_mutex_t pid_mutex = PTHREAD_MUTEX_INITIALIZER;

// Path to Xray binary
static const char* XRAY_BIN = "libxray.so";
static std::string xray_bin_path;

// Function prototypes
static bool prepare_xray_environment(JNIEnv *env, const std::string& internal_dir);
static bool execute_xray(const std::string& config_path);
static bool kill_xray_process();
static std::string get_xray_path(const std::string& internal_dir);
// Commenting out unused function declaration
// static int copy_asset_to_file(JNIEnv *env, jobject context, const std::string& asset_name, 
//                              const std::string& output_path);

extern "C" {

/**
 * Initialize the Xray environment
 */
JNIEXPORT jint JNICALL
Java_com_hiddify_hiddifyng_core_XrayManager_initXrayEnvironment(JNIEnv *env, jclass clazz, 
                                                              jobject context, jstring internal_dir) {
    LOGI("Initializing Xray environment");
    
    // Get internal dir as C++ string
    const char *dir = env->GetStringUTFChars(internal_dir, nullptr);
    std::string internal_directory(dir);
    env->ReleaseStringUTFChars(internal_dir, dir);
    
    // Set up Xray binary path
    xray_bin_path = get_xray_path(internal_directory);
    
    // Prepare Xray environment
    bool success = prepare_xray_environment(env, internal_directory);
    
    return success ? 0 : -1;
}

/**
 * Start Xray with the given configuration
 */
JNIEXPORT jint JNICALL
Java_com_hiddify_hiddifyng_core_XrayManager_startXray(JNIEnv *env, jclass clazz, jstring config_path) {
    const char *path = env->GetStringUTFChars(config_path, nullptr);
    LOGI("Starting Xray with config: %s", path);
    
    bool success = execute_xray(std::string(path));
    env->ReleaseStringUTFChars(config_path, path);
    
    return success ? 0 : -1;
}

/**
 * Stop Xray service
 */
JNIEXPORT jint JNICALL
Java_com_hiddify_hiddifyng_core_XrayManager_stopXray(JNIEnv *env, jclass clazz) {
    LOGI("Stopping Xray");
    
    bool success = kill_xray_process();
    
    return success ? 0 : -1;
}

/**
 * Get Xray version
 */
JNIEXPORT jstring JNICALL
Java_com_hiddify_hiddifyng_core_XrayManager_checkVersion(JNIEnv *env, jclass clazz) {
    LOGI("Checking Xray version");
    
    // Execute Xray with --version flag and capture output
    std::string version = "Unknown";
    
    if (!xray_bin_path.empty()) {
        char cmd[512];
        snprintf(cmd, sizeof(cmd), "%s --version", xray_bin_path.c_str());
        
        FILE* pipe = popen(cmd, "r");
        if (pipe) {
            char buffer[128];
            if (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
                version = buffer;
                // Trim newline
                if (!version.empty() && version[version.length()-1] == '\n') {
                    version.erase(version.length()-1);
                }
            }
            pclose(pipe);
        }
    }
    
    LOGI("Xray version: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

/**
 * Update GeoIP and GeoSite databases
 */
JNIEXPORT jint JNICALL
Java_com_hiddify_hiddifyng_core_XrayManager_updateGeoDB(JNIEnv *env, jclass clazz, jstring path) {
    const char *db_path = env->GetStringUTFChars(path, nullptr);
    LOGI("Updating GeoDB at: %s", db_path);
    
    // In a full implementation, this would download updated GeoIP and GeoSite databases
    // For now we'll just log a success message
    LOGI("GeoDB update successfully simulated");
    
    env->ReleaseStringUTFChars(path, db_path);
    return 0;
}

} // extern "C"

/**
 * Prepare the Xray environment by setting up binaries and permissions
 */
static bool prepare_xray_environment(JNIEnv *env, const std::string& internal_dir) {
    // Make sure Xray binary path is valid
    if (xray_bin_path.empty()) {
        LOGE("Xray binary path not set");
        return false;
    }
    
    // Check if Xray binary exists and is executable
    struct stat st;
    if (stat(xray_bin_path.c_str(), &st) != 0) {
        LOGE("Xray binary not found at %s", xray_bin_path.c_str());
        // In a full implementation, we would extract the binary from assets here
        return false;
    }
    
    // Make Xray binary executable
    if (chmod(xray_bin_path.c_str(), 0755) != 0) {
        LOGE("Failed to make Xray binary executable");
        return false;
    }
    
    LOGI("Xray environment prepared successfully");
    return true;
}

/**
 * Execute Xray with the given configuration
 */
static bool execute_xray(const std::string& config_path) {
    // Create child process
    pthread_mutex_lock(&pid_mutex);
    
    // Make sure any existing Xray process is killed
    if (xray_pid > 0) {
        kill_xray_process();
    }
    
    pid_t pid = fork();
    
    if (pid < 0) {
        // Fork failed
        LOGE("Failed to fork Xray process");
        pthread_mutex_unlock(&pid_mutex);
        return false;
    } else if (pid == 0) {
        // Child process
        LOGI("Starting Xray process with config: %s", config_path.c_str());
        
        // Execute Xray
        if (execl(xray_bin_path.c_str(), XRAY_BIN, "run", "-c", config_path.c_str(), NULL) < 0) {
            LOGE("Failed to execute Xray: %s", strerror(errno));
            exit(1);
        }
        
        // This should never be reached
        exit(0);
    } else {
        // Parent process
        LOGI("Xray process started with PID: %d", pid);
        xray_pid = pid;
        pthread_mutex_unlock(&pid_mutex);
        return true;
    }
}

/**
 * Kill the Xray process
 */
static bool kill_xray_process() {
    pthread_mutex_lock(&pid_mutex);
    
    if (xray_pid <= 0) {
        // No Xray process running
        pthread_mutex_unlock(&pid_mutex);
        return true;
    }
    
    // Send SIGTERM to Xray process
    if (kill(xray_pid, SIGTERM) < 0) {
        LOGE("Failed to kill Xray process (PID: %d): %s", xray_pid, strerror(errno));
        pthread_mutex_unlock(&pid_mutex);
        return false;
    }
    
    LOGI("Sent SIGTERM to Xray process (PID: %d)", xray_pid);
    
    // Wait a bit for process to exit
    usleep(100000); // 100ms
    
    // Check if process is still running
    if (kill(xray_pid, 0) == 0) {
        // Process still exists, try SIGKILL
        LOGI("Xray process still running, sending SIGKILL");
        kill(xray_pid, SIGKILL);
    }
    
    xray_pid = -1;
    pthread_mutex_unlock(&pid_mutex);
    return true;
}

/**
 * Get path to Xray binary
 */
static std::string get_xray_path(const std::string& internal_dir) {
    return internal_dir + "/bin/" + XRAY_BIN;
}

/**
 * Copy an asset file to internal storage
 * Currently not used but kept for future implementation
 */
/* Commented out to avoid unused function warning
static int copy_asset_to_file(JNIEnv *env, jobject context, const std::string& asset_name, 
                             const std::string& output_path) {
    // In a real implementation, this would:
    // 1. Open the asset from the APK
    // 2. Copy it to the specified path
    // 3. Set appropriate permissions
    
    LOGI("Would copy asset %s to %s", asset_name.c_str(), output_path.c_str());
    return 0;
}
*/
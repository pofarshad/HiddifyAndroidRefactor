cmake_minimum_required(VERSION 3.18.1)

project("hiddifyng")

# Create a simple placeholder native library for now
# In the actual implementation, this would be replaced with the xray-core code
add_library(
    xray-core-jni
    SHARED
    xray-core-jni.cpp
)

# Include directories for header files
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Find and link against required libraries
find_library(
    log-lib
    log
)

# Link the target library with the log library
target_link_libraries(
    xray-core-jni
    ${log-lib}
)
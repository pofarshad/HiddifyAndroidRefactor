cmake_minimum_required(VERSION 3.18.1)

project("hiddifyng")

# Set compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Werror")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall -Werror")

# Define the xray-core-jni library
add_library(
    xray-core-jni
    SHARED
    xray-core-jni.cpp
)

# Include directories for header files
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Link the target library with required libraries
target_link_libraries(
    xray-core-jni
    ${log-lib}
    ${android-lib}
)

# Set compile options
target_compile_options(xray-core-jni PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

# Set link options
set_target_properties(xray-core-jni PROPERTIES
    LINK_FLAGS "-Wl,--gc-sections"
)

# In a production build, you would include the Xray core code here
# For example:
#
# # Add Xray core library
# add_subdirectory(xray-core)
#
# # Link with Xray core
# target_link_libraries(
#     xray-core-jni
#     xray-core
# )
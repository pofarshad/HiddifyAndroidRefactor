cmake_minimum_required(VERSION 3.4.1)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add project
project(xray-core)

# Find the Android log library
find_library(log-lib log)

# Add Xray core library
add_library(xray-core SHARED
            xray-core-jni.cpp
            # In a real implementation, you would add other source files here
            )

# Link to required libraries
target_link_libraries(xray-core ${log-lib})
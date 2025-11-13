cmake_minimum_required(VERSION 3.10.2)
project("flamassignment")

add_library( native-lib SHARED native-lib.cpp )

find_library( log-lib log )

# If using OpenCV Android SDK:
# set(OpenCV_DIR /path/to/OpenCV-android-sdk/sdk/native/jni)
# find_package(OpenCV REQUIRED COMPONENTS core imgproc)
# include_directories(${OpenCV_INCLUDE_DIRS})
# target_link_libraries(native-lib ${OpenCV_LIBS} ${log-lib})

# If using OpenCV AAR, configure Gradle to include it and link appropriately.

target_link_libraries( native-lib ${log-lib} )

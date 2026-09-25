cmake_minimum_required(VERSION 3.22.1)
project("evdev_reader")

add_library(${CMAKE_PROJECT_NAME} SHARED
    evdev_reader.cpp
    evdev_jni.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log)

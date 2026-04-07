cmake_minimum_required(VERSION 3.16)
project(moonpackethook LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

add_library(moonpackethook SHARED
    src/moon_jni_packet_hook.cpp
)

target_include_directories(moonpackethook PRIVATE
    ${JNI_INCLUDE_DIRS}
)

if(WIN32)
    set_target_properties(moonpackethook PROPERTIES PREFIX "")
    set_target_properties(moonpackethook PROPERTIES OUTPUT_NAME "moonpackethook")
endif()

set(MOONPACKETHOOK_OUT "${CMAKE_SOURCE_DIR}/out" CACHE PATH "Output directory for moonpackethook shared library")
file(MAKE_DIRECTORY "${MOONPACKETHOOK_OUT}")

# Visual Studio multi-config puts binaries in a config subdir unless per-config dirs are set.
if(CMAKE_CONFIGURATION_TYPES)
    foreach(CFG ${CMAKE_CONFIGURATION_TYPES})
	string(TOUPPER "${CFG}" CFGU)
	set_target_properties(moonpackethook PROPERTIES
	    "RUNTIME_OUTPUT_DIRECTORY_${CFGU}" "${MOONPACKETHOOK_OUT}"
	    "LIBRARY_OUTPUT_DIRECTORY_${CFGU}" "${MOONPACKETHOOK_OUT}")
    endforeach()
else()
    set_target_properties(moonpackethook PROPERTIES
	LIBRARY_OUTPUT_DIRECTORY "${MOONPACKETHOOK_OUT}"
	RUNTIME_OUTPUT_DIRECTORY "${MOONPACKETHOOK_OUT}")
endif()

cmake_minimum_required(VERSION 3.16)
project(havenbot LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

add_library(havenbot SHARED
    src/haven_bot.cpp
)

target_include_directories(havenbot PRIVATE
    ${JNI_INCLUDE_DIRS}
)

if(WIN32)
    set_target_properties(havenbot PROPERTIES PREFIX "")
    set_target_properties(havenbot PROPERTIES OUTPUT_NAME "havenbot")
endif()

set(HAVENBOT_OUT "${CMAKE_SOURCE_DIR}/out" CACHE PATH "Output directory for havenbot shared library")
file(MAKE_DIRECTORY "${HAVENBOT_OUT}")

set_target_properties(havenbot PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${HAVENBOT_OUT}"
    RUNTIME_OUTPUT_DIRECTORY "${HAVENBOT_OUT}"
)

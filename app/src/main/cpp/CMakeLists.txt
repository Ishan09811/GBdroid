cmake_minimum_required(VERSION 3.22.1)
project(gbdroid)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(MGBA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/mgba)

if(NOT EXISTS ${MGBA_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
            "mGBA submodule not found at ${MGBA_DIR}. Run:\n"
            "  git submodule update --init --recursive\n"
            "from the project root before building.")
endif()

set(BUILD_QT OFF CACHE BOOL "" FORCE)
set(BUILD_SDL OFF CACHE BOOL "" FORCE)
set(BUILD_SDL2 OFF CACHE BOOL "" FORCE)
set(BUILD_PERF OFF CACHE BOOL "" FORCE)
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_SUITE OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(BUILD_LIBRETRO OFF CACHE BOOL "" FORCE)
set(USE_FFMPEG OFF CACHE BOOL "" FORCE)
set(USE_LIBZIP OFF CACHE BOOL "" FORCE)
set(USE_MINIZIP OFF CACHE BOOL "" FORCE)
set(USE_ZLIB ON CACHE BOOL "" FORCE)
set(USE_SQLITE3 OFF CACHE BOOL "" FORCE)
set(USE_DISCORD_RPC OFF CACHE BOOL "" FORCE)
set(USE_EDITLINE OFF CACHE BOOL "" FORCE)
set(USE_GDB_STUB OFF CACHE BOOL "" FORCE)
set(USE_EPOXY OFF CACHE BOOL "" FORCE)      # desktop GL loader does not work in android
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)

add_subdirectory(${MGBA_DIR} mgba_build EXCLUDE_FROM_ALL)

if(TARGET zlibstatic)
    message(STATUS "mGBA ZIP-ROM support: bundled zlibstatic target found - OK")
endif()

if(TARGET mgba)
    set(MGBA_CORE_TARGET mgba)
elseif(TARGET libmgba)
    set(MGBA_CORE_TARGET libmgba)
else()
    message(FATAL_ERROR
            "Could not find the mGBA core CMake target under the expected "
            "names ('mgba' / 'libmgba'). Run `cmake --build . --target help` "
            "in your build dir after configuring to see the actual target "
            "list produced by ${MGBA_DIR}/CMakeLists.txt, then update "
            "MGBA_CORE_TARGET in this file to match.")
endif()


add_library(gbdroid SHARED
    core.cpp
    core_interface.cpp
    audio/oboe_audio_player.cpp
)

target_include_directories(gbdroid PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${MGBA_DIR}/include
    ${CMAKE_CURRENT_BINARY_DIR}/mgba_build
)

find_package(oboe REQUIRED CONFIG)

find_library(log-lib log)
find_library(android-lib android)
find_library(gles-lib GLESv2)
find_library(z-lib z)

target_link_libraries(gbdroid
    ${MGBA_CORE_TARGET}
    ${log-lib}
    ${android-lib}
    ${gles-lib}
    ${z-lib}
    oboe::oboe
)

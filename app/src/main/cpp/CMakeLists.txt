cmake_minimum_required(VERSION 3.16.9)
project("rpcs3-android")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE on)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
    set(ARCH_FLAGS "-mcpu=cortex-a53")
else()
    set(ARCH_FLAGS "-mno-avx")
endif()

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${ARCH_FLAGS}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${ARCH_FLAGS}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${ARCH_FLAGS}")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${ARCH_FLAGS}")


set(FFMPEG_PATH ${CMAKE_CURRENT_SOURCE_DIR}/FFmpeg)
add_custom_command(
    OUTPUT ${FFMPEG_PATH}/config.h
    COMMAND ./configure
        --disable-libdrm
        --disable-vaapi
        --disable-vdpau
        --disable-zlib
        --disable-lzma
        --enable-pic
        --disable-asm
        --extra-cflags="--target=${CMAKE_SYSTEM_PROCESSOR}-none-linux-android${ANDROID_NATIVE_API_LEVEL} -fpic"
        --extra-ldflags="--target=${CMAKE_SYSTEM_PROCESSOR}-none-linux-android${ANDROID_NATIVE_API_LEVEL}"
        --cc="${CMAKE_C_COMPILER}"
        --arch="${CMAKE_SYSTEM_PROCESSOR}"
        --ld="${CMAKE_C_COMPILER}"
        --target_os=android
        --enable-cross-compile
    COMMENT "Configuring FFmpeg..."
    WORKING_DIRECTORY ${FFMPEG_PATH}
)
add_custom_target(ffmpeg-configure DEPENDS ${FFMPEG_PATH}/config.h)

function(import_ffmpeg_library name)
    if (${CMAKE_GENERATOR} STREQUAL "Unix Makefiles")
        set(MAKE_COMMAND $(MAKE) -j$(nproc))
    elseif (${CMAKE_GENERATOR} STREQUAL "Ninja")
        set(MAKE_COMMAND make -j$$(nproc))
    else()
        set(MAKE_COMMAND make)
    endif()

    add_custom_command(
        OUTPUT "${FFMPEG_PATH}/lib${name}/lib${name}.a"
        COMMAND ${MAKE_COMMAND} -C ${FFMPEG_PATH} "lib${name}/lib${name}.a"
        COMMENT "Building lib${name}/lib${name}.a"
        DEPENDS ffmpeg-configure
        WORKING_DIRECTORY ${FFMPEG_PATH}
    )

    add_custom_target(ffmpeg-build-${name} DEPENDS "${FFMPEG_PATH}/lib${name}/lib${name}.a")

    add_library(ffmpeg::${name} STATIC IMPORTED GLOBAL)
    set_property(TARGET ffmpeg::${name} PROPERTY IMPORTED_LOCATION "${FFMPEG_PATH}/lib${name}/lib${name}.a")
    set_property(TARGET ffmpeg::${name} PROPERTY INTERFACE_INCLUDE_DIRECTORIES "${FFMPEG_PATH}")
    add_dependencies(ffmpeg::${name} ffmpeg-build-${name})
endfunction()

import_ffmpeg_library(avcodec)
import_ffmpeg_library(avformat)
import_ffmpeg_library(avfilter)
import_ffmpeg_library(avdevice)
import_ffmpeg_library(avutil)
import_ffmpeg_library(swscale)
import_ffmpeg_library(swresample)
import_ffmpeg_library(postproc)

add_library(3rdparty_ffmpeg INTERFACE)
target_link_libraries(3rdparty_ffmpeg INTERFACE
    ffmpeg::avformat
    ffmpeg::avcodec
    ffmpeg::avutil
    ffmpeg::swscale
    ffmpeg::swresample
)

set(USE_SYSTEM_LIBUSB off)
set(USE_SYSTEM_CURL off)
set(USE_DISCORD_RPC off)
set(USE_SYSTEM_OPENCV off)
set(USE_SYSTEM_FFMPEG off)
set(USE_FAUDIO off)
set(USE_SDL2 off)
set(BUILD_LLVM on)
set(DISABLE_LTO on)
set(USE_LTO off)
set(USE_OPENSL off)
set(ASMJIT_NO_SHM_OPEN on)
set(USE_SYSTEM_ZLIB on)
set(USE_LIBEVDEV off)

add_subdirectory(rpcs3 EXCLUDE_FROM_ALL)

add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp
    rpcs3/rpcs3/stb_image.cpp
    rpcs3/rpcs3/Input/ds3_pad_handler.cpp
    rpcs3/rpcs3/Input/ds4_pad_handler.cpp
    rpcs3/rpcs3/Input/dualsense_pad_handler.cpp
    rpcs3/rpcs3/Input/evdev_joystick_handler.cpp
    rpcs3/rpcs3/Input/evdev_gun_handler.cpp
#    rpcs3/rpcs3/Input/gui_pad_thread.cpp
    rpcs3/rpcs3/Input/hid_pad_handler.cpp
    rpcs3/rpcs3/Input/virtual_pad_handler.cpp
    rpcs3/rpcs3/Input/mm_joystick_handler.cpp
    rpcs3/rpcs3/Input/pad_thread.cpp
    rpcs3/rpcs3/Input/product_info.cpp
    rpcs3/rpcs3/Input/ps_move_calibration.cpp
    rpcs3/rpcs3/Input/ps_move_config.cpp
    rpcs3/rpcs3/Input/ps_move_handler.cpp
    rpcs3/rpcs3/Input/ps_move_tracker.cpp
    rpcs3/rpcs3/Input/raw_mouse_config.cpp
    rpcs3/rpcs3/Input/raw_mouse_handler.cpp
    rpcs3/rpcs3/Input/sdl_pad_handler.cpp
    rpcs3/rpcs3/Input/skateboard_pad_handler.cpp
    rpcs3/rpcs3/rpcs3_version.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PUBLIC rpcs3/rpcs3)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    rpcs3_emu
    nativehelper
    3rdparty::libusb
    3rdparty::hidapi
    3rdparty::wolfssl
    3rdparty::libcurl
    3rdparty::zlib
    3rdparty::fusion
)

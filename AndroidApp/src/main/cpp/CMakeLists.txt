cmake_minimum_required(VERSION 3.22.1)
project(sandroid LANGUAGES C ASM)

set(ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../..")
set(ENGINE "${ROOT}/Engine")
set(BRIDGE "${ROOT}/Shared/Bridge")

file(GLOB SAM CONFIGURE_DEPENDS "${ENGINE}/sam/*.c")
file(GLOB ANNA CONFIGURE_DEPENDS "${ENGINE}/anna/*.c")
file(GLOB ONECORE CONFIGURE_DEPENDS "${ENGINE}/onecore/*.c")
file(GLOB SAPI4 CONFIGURE_DEPENDS "${ENGINE}/sapi4/src/*.c" "${ENGINE}/sapi4/lib/*.c" "${ENGINE}/sapi4/port/*.c")
file(GLOB OPENTV CONFIGURE_DEPENDS "${ENGINE}/opentv/src/engine/*.c" "${ENGINE}/opentv/src/port/*.c")

set(BRIDGES
    "${BRIDGE}/cv_bridge.c"
    "${BRIDGE}/cv_resample.c"
    "${BRIDGE}/cv4n_bridge.c"
    "${BRIDGE}/cvt_bridge.c"
    "${BRIDGE}/cva_bridge.c"
    "${BRIDGE}/cvo_bridge.c"
    "${BRIDGE}/cvn_bridge.c"
    "${CMAKE_CURRENT_LIST_DIR}/sandroid_jni.c")

set(TRUVOICE_TABLES "${ROOT}/TruVoiceData/tvdata.s")
if(EXISTS "${TRUVOICE_TABLES}")
    list(APPEND BRIDGES "${TRUVOICE_TABLES}")
    set(TRUVOICE_SOURCES ${OPENTV} "${BRIDGE}/cvt_bridge.c")
else()
    message(WARNING "TruVoiceData/tvdata.s missing: building a non-speaking TruVoice placeholder. Stage it with make truvoice-data to enable TruVoice.")
    set(TRUVOICE_SOURCES "${CMAKE_CURRENT_LIST_DIR}/truvoice_stub.c")
endif()

list(REMOVE_ITEM BRIDGES "${BRIDGE}/cvt_bridge.c")
add_library(sandroid SHARED ${SAM} ${ANNA} ${ONECORE} ${SAPI4} ${TRUVOICE_SOURCES} ${BRIDGES})
target_include_directories(sandroid PRIVATE
    "${ENGINE}/sam" "${ENGINE}/anna" "${ENGINE}/onecore"
    "${ENGINE}/sapi4/src" "${ENGINE}/sapi4/lib" "${ENGINE}/sapi4/port"
    "${ENGINE}/opentv/src" "${ENGINE}/opentv/include" "${ENGINE}/opentv/generated"
    "${BRIDGE}")
target_compile_definitions(sandroid PRIVATE CV_NO_DEBUG_ENV=1)
set_source_files_properties(${SAPI4} PROPERTIES COMPILE_OPTIONS "-fno-builtin")
target_compile_options(sandroid PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-O2>
    $<$<COMPILE_LANGUAGE:C>:-fno-fast-math>
    $<$<COMPILE_LANGUAGE:C>:-ffp-contract=off>
    $<$<COMPILE_LANGUAGE:C>:-fwrapv>
    $<$<COMPILE_LANGUAGE:C>:-fno-strict-aliasing>
    $<$<COMPILE_LANGUAGE:C>:-Wno-unused-parameter>)
target_link_libraries(sandroid log m)

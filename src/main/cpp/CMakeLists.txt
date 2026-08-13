cmake_minimum_required(VERSION 3.22.1)

project("nityapanchangephemeris")

add_library(
    nityapanchangephemeris
    SHARED
    native-lib.cpp
    swecl.c
    swedate.c
    swehouse.c
    swejpl.c
    swemmoon.c
    swemplan.c
    sweph.c
    swephlib.c
)

find_library(
    log-lib
    log
)

target_link_libraries(
    nityapanchangephemeris
    ${log-lib}
)

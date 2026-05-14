cmake_minimum_required(VERSION 3.22.1)
project(native-key C)

add_library(native-key SHARED native-key.c)

set_target_properties(native-key PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
)

find_library(log-lib log)
target_link_libraries(native-key ${log-lib})

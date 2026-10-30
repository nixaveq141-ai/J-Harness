cmake_minimum_required(VERSION 3.15)
project(jh_owned_directory_probe LANGUAGES CXX)
add_executable(owned-directory-probe ../winpty-directory-probe.cpp)
target_compile_features(owned-directory-probe PRIVATE cxx_std_11)

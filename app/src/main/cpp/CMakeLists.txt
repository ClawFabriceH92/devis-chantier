# Couche native de la dictée assistée « sur le téléphone » : llama.cpp (moteur
# d'inférence des modèles GGUF, licence MIT) compilé pour Android et un pont
# JNI minuscule (devis_llm.cpp). Le modèle lui-même (LFM3-1.7B de Liquid AI)
# n'est pas dans l'APK : il est téléchargé par l'artisan depuis les réglages.
cmake_minimum_required(VERSION 3.22.1)
project(devis_llm LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Version de llama.cpp épinglée. LFM3 est pris en charge depuis b11960 ; on
# reste sur une étiquette précise pour que deux compilations donnent le même APK.
set(DEVIS_LLAMA_CPP_TAG "b12361" CACHE STRING "Étiquette git de llama.cpp")

include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        ${DEVIS_LLAMA_CPP_TAG}
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   TRUE
)

# Options de llama.cpp. Ce sont des variables normales : grâce à la politique
# CMP0077 (cmake_minimum_required >= 3.13 dans llama.cpp) elles priment sur les
# option() du sous-projet.
set(BUILD_SHARED_LIBS ON)
set(LLAMA_BUILD_COMMON ON)      # gabarits de conversation (Jinja), échantillonnage, grammaire
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_TOOLS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_APP OFF)
set(LLAMA_OPENSSL OFF)          # aucun téléchargement par llama.cpp : l'app s'en charge
set(LLAMA_SUBPROCESS OFF)
set(LLAMA_ALL_WARNINGS OFF)
set(LLAMA_BUILD_IS_DEV OFF)

set(GGML_NATIVE OFF)            # pas de -march=native : on compile pour un téléphone, pas pour la machine de build
set(GGML_OPENMP OFF)            # le pool de threads de ggml suffit et évite d'embarquer libomp.so
set(GGML_LLAMAFILE OFF)
set(GGML_CPU_KLEIDIAI OFF)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Un seul APK pour tous les téléphones 64 bits : ggml compile plusieurs
    # variantes du backend CPU (ARMv8.0 de base, +dotprod, +fp16, +i8mm, SVE,
    # SME) en bibliothèques séparées et charge la meilleure au démarrage selon
    # les instructions réellement présentes sur le processeur.
    set(GGML_BACKEND_DL ON)
    set(GGML_CPU_ALL_VARIANTS ON)
else()
    # x86_64 ne sert qu'à l'émulateur de la QA : une variante générique suffit.
    set(GGML_BACKEND_DL OFF)
    set(GGML_CPU_ALL_VARIANTS OFF)
endif()

FetchContent_MakeAvailable(llama_cpp)

add_library(devis_llm SHARED devis_llm.cpp)

target_include_directories(devis_llm PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/common
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

target_link_libraries(devis_llm PRIVATE llama llama-common android log)

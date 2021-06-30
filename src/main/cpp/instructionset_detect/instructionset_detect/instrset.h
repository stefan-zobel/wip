/****************************  instrset.h   **********************************
* Author:        Agner Fog
* Date created:  2012-05-30
* Last modified: 2020-11-04
* Version:       2.01.03
* Project:       vector class library
* Description:
* Header file for various compiler-specific tasks as well as common
* macros and templates. This file contains:
*
* > Selection of the supported instruction set
* > Defines compiler version macros
* > Undefines certain macros that prevent function overloading
* > Helper functions that depend on instruction set, compiler, or platform
* > Common templates for permute, blend, etc.
*
* For instructions, see vcl_manual.pdf
*
* (c) Copyright 2012-2020 Agner Fog.
* Apache License version 2.0 or later.
******************************************************************************/

#ifndef INSTRSET_H
#define INSTRSET_H 20103


// Allow the use of floating point permute instructions on integer vectors.
// Some CPU's have an extra latency of 1 or 2 clock cycles for this, but
// it may still be faster than alternative implementations:
#define ALLOW_FP_PERMUTE  true


// Macro to indicate 64 bit mode
#if (defined(_M_AMD64) || defined(_M_X64) || defined(__amd64) ) && ! defined(__x86_64__)
#define __x86_64__ 1  // There are many different macros for this, decide on only one
#endif

// The following values of INSTRSET are currently defined:
// 2:  SSE2
// 3:  SSE3
// 4:  SSSE3
// 5:  SSE4.1
// 6:  SSE4.2
// 7:  AVX
// 8:  AVX2
// 9:  AVX512F
// 10: AVX512BW/DQ/VL
// In the future, INSTRSET = 11 may include AVX512VBMI and AVX512VBMI2, but this
// decision cannot be made before the market situation for CPUs with these
// instruction sets is known (these future instruction set extensions are already
// used in some VCL functions and tested with an emulator)

// Find instruction set from compiler macros if INSTRSET is not defined.
// Note: Most of these macros are not defined in Microsoft compilers
#ifndef INSTRSET
#if defined ( __AVX512VL__ ) && defined ( __AVX512BW__ ) && defined ( __AVX512DQ__ )
#define INSTRSET 10
#elif defined ( __AVX512F__ ) || defined ( __AVX512__ )
#define INSTRSET 9
#elif defined ( __AVX2__ )
#define INSTRSET 8
#elif defined ( __AVX__ )
#define INSTRSET 7
#elif defined ( __SSE4_2__ )
#define INSTRSET 6
#elif defined ( __SSE4_1__ )
#define INSTRSET 5
#elif defined ( __SSSE3__ )
#define INSTRSET 4
#elif defined ( __SSE3__ )
#define INSTRSET 3
#elif defined ( __SSE2__ ) || defined ( __x86_64__ )
#define INSTRSET 2
#elif defined ( __SSE__ )
#define INSTRSET 1
#elif defined ( _M_IX86_FP )           // Defined in MS compiler. 1: SSE, 2: SSE2
#define INSTRSET _M_IX86_FP
#else
#define INSTRSET 0
#endif // instruction set defines
#endif // INSTRSET

// Include the appropriate header file for intrinsic functions
#if INSTRSET > 7                       // AVX2 and later
#if defined (__GNUC__) && ! defined (__INTEL_COMPILER)
#include <x86intrin.h>                 // x86intrin.h includes header files for whatever instruction
                                       // sets are specified on the compiler command line, such as:
                                       // xopintrin.h, fma4intrin.h
#else
#include <immintrin.h>                 // MS/Intel version of immintrin.h covers AVX and later
#endif // __GNUC__
#elif INSTRSET == 7
#include <immintrin.h>                 // AVX
#elif INSTRSET == 6
#include <nmmintrin.h>                 // SSE4.2
#elif INSTRSET == 5
#include <smmintrin.h>                 // SSE4.1
#elif INSTRSET == 4
#include <tmmintrin.h>                 // SSSE3
#elif INSTRSET == 3
#include <pmmintrin.h>                 // SSE3
#elif INSTRSET == 2
#include <emmintrin.h>                 // SSE2
#elif INSTRSET == 1
#include <xmmintrin.h>                 // SSE
#endif // INSTRSET

#if INSTRSET >= 8 && !defined(__FMA__)
// Assume that all processors that have AVX2 also have FMA3
#if defined (__GNUC__) && ! defined (__INTEL_COMPILER)
// Prevent error message in g++ and Clang when using FMA intrinsics with avx2:
#if !defined(DISABLE_WARNING_AVX2_WITHOUT_FMA)
#pragma message "It is recommended to specify also option -mfma when using -mavx2 or higher"
#endif
#elif ! defined (__clang__)
#define __FMA__  1
#endif
#endif

// AMD  instruction sets
#if defined (__XOP__) || defined (__FMA4__)
#ifdef __GNUC__
#include <x86intrin.h>                 // AMD XOP (Gnu)
#else
#include <ammintrin.h>                 // AMD XOP (Microsoft)
#endif //  __GNUC__
#elif defined (__SSE4A__)              // AMD SSE4A
#include <ammintrin.h>
#endif // __XOP__

// FMA3 instruction set
#if defined (__FMA__) && (defined(__GNUC__) || defined(__clang__))  && ! defined (__INTEL_COMPILER)
#include <fmaintrin.h>
#endif // __FMA__

// FMA4 instruction set
#if defined (__FMA4__) && (defined(__GNUC__) || defined(__clang__))
#include <fma4intrin.h> // must have both x86intrin.h and fma4intrin.h, don't know why
#endif // __FMA4__


#include <stdint.h>                    // Define integer types with known size
#include <stdlib.h>                    // define abs(int)
#include "jni.h"

#ifdef _MSC_VER                        // Microsoft compiler or compatible Intel compiler
#include <intrin.h>                    // define _BitScanReverse(int), __cpuid(int[4],int), _xgetbv(int)
#endif // _MSC_VER


// functions in instrset_detect.cpp:
#ifdef VCL_NAMESPACE
namespace VCL_NAMESPACE {
#endif
    int  instrset_detect(void);        // tells which instruction sets are supported
#ifdef VCL_NAMESPACE
}
#endif


#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     net_volcanite_util_CPU
     * Method:    detectInstructionSet
     * Signature: ()I
     */
    JNIEXPORT jint JNICALL Java_net_volcanite_util_CPU_detectInstructionSet
    (JNIEnv*, jclass);

#ifdef __cplusplus
}
#endif


// GCC version
#if defined(__GNUC__) && !defined (GCC_VERSION) && !defined (__clang__)
#define GCC_VERSION  ((__GNUC__) * 10000 + (__GNUC_MINOR__) * 100 + (__GNUC_PATCHLEVEL__))
#endif

// Clang version
#if defined (__clang__)
#define CLANG_VERSION  ((__clang_major__) * 10000 + (__clang_minor__) * 100 + (__clang_patchlevel__))
// Problem: The version number is not consistent across platforms
// http://llvm.org/bugs/show_bug.cgi?id=12643
// Apple bug 18746972
#endif

// Fix problem with non-overloadable macros named min and max in WinDef.h
#ifdef _MSC_VER
#if defined (_WINDEF_) && defined(min) && defined(max)
#undef min
#undef max
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif

// warning for poor support for AVX512F in MS compiler
#ifndef __INTEL_COMPILER
#if INSTRSET == 9
#pragma message("Warning: MS compiler cannot generate code for AVX512F without AVX512DQ")
#endif
#if _MSC_VER < 1920 && INSTRSET > 8
#pragma message("Warning: Your compiler has poor support for AVX512. Code may be erroneous.\nPlease use a newer compiler version or a different compiler!")
#endif
#endif // __INTEL_COMPILER
#endif // _MSC_VER

/* Intel compiler problem:
The Intel compiler currently cannot compile version 2.00 of VCL. It seems to have
a problem with constexpr function returns not being constant enough.
*/
#if defined(__INTEL_COMPILER) && __INTEL_COMPILER < 9999
#error The Intel compiler version 19.00 cannot compile VCL version 2. Use Version 1.xx of VCL instead
#endif

/* Clang problem:
The Clang compiler treats the intrinsic vector types __m128, __m128i, and __m128d as identical.
See the bug report at https://bugs.llvm.org/show_bug.cgi?id=17164
Additional problem: The version number is not consistent across platforms. The Apple build has
different version numbers. We have to rely on __apple_build_version__ on the Mac platform:
http://llvm.org/bugs/show_bug.cgi?id=12643
We have to make switches here when - hopefully - the error some day has been fixed.
We need different version checks with and whithout __apple_build_version__
*/
#if (defined (__clang__) || defined(__apple_build_version__)) && !defined(__INTEL_COMPILER)
#define FIX_CLANG_VECTOR_ALIAS_AMBIGUITY
#endif

#if defined (GCC_VERSION) && GCC_VERSION < 99999 && !defined(__clang__)
#define ZEXT_MISSING  // Gcc 7.4.0 does not have _mm256_zextsi128_si256 and similar functions
#endif


#ifdef VCL_NAMESPACE
namespace VCL_NAMESPACE {
#endif


    /*****************************************************************************
    *
    *    Helper functions that depend on instruction set, compiler, or platform
    *
    *****************************************************************************/

    // Define interface to cpuid instruction.
    // input:  functionnumber = leaf (eax), ecxleaf = subleaf(ecx)
    // output: output[0] = eax, output[1] = ebx, output[2] = ecx, output[3] = edx
    static inline void cpuid(int output[4], int functionnumber, int ecxleaf = 0) {
#if defined(__GNUC__) || defined(__clang__)           // use inline assembly, Gnu/AT&T syntax
        int a, b, c, d;
        __asm("cpuid" : "=a"(a), "=b"(b), "=c"(c), "=d"(d) : "a"(functionnumber), "c"(ecxleaf) : );
        output[0] = a;
        output[1] = b;
        output[2] = c;
        output[3] = d;

#elif defined (_MSC_VER)                              // Microsoft compiler, intrin.h included
        __cpuidex(output, functionnumber, ecxleaf);       // intrinsic function for CPUID

#else                                                 // unknown platform. try inline assembly with masm/intel syntax
        __asm {
            mov eax, functionnumber
            mov ecx, ecxleaf
            cpuid;
            mov esi, output
                mov[esi], eax
                mov[esi + 4], ebx
                mov[esi + 8], ecx
                mov[esi + 12], edx
        }
#endif
}


#ifdef VCL_NAMESPACE
}
#endif


#endif // INSTRSET_H

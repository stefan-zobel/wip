
#ifndef _JAVASOFT_JNI_H_
#include <jni.h>
#endif /* _JAVASOFT_JNI_H_ */

#include <stdlib.h>

#if defined (_WIN64)
#include <windows.h>
#else /* Linux / Unix */
#include <sys/mman.h>
#include <stddef.h>
#endif /* (_WIN64) */


#ifdef _WIN64
#define jlong_to_ptr(a) ((void*)(a))
#define ptr_to_jlong(a) ((jlong)(a))
#endif

#ifdef __linux
  #ifdef _LP64
    #ifndef jlong_to_ptr
      #define jlong_to_ptr(a) ((void*)(a))
    #endif
    #ifndef ptr_to_jlong
      #define ptr_to_jlong(a) ((jlong)(a))
    #endif
  #else
    #ifndef jlong_to_ptr
      #define jlong_to_ptr(a) ((void*)(int)(a))
    #endif
    #ifndef ptr_to_jlong
      #define ptr_to_jlong(a) ((jlong)(int)(a))
    #endif
  #endif
#endif


/* Output type for mincore(2) */
typedef unsigned char mincore_vec_t;


#ifdef __cplusplus
extern "C" {
#endif


/*
 * Class:     mmap_impl_MMapUtils
 * Method:    isLoaded0
 * Signature: (JJJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_mmap_impl_MMapUtils_isLoaded0(JNIEnv* env, jclass,
  jlong address,
  jlong length,
  jlong pageCount) {
#if defined (_WIN64)

    /* Information is not available under Windows */
    return JNI_FALSE;

#else /* Linux / Unix */

    void* a = jlong_to_ptr(address);
    size_t len = (size_t) length;
    int numPages = (int) pageCount;

    mincore_vec_t* vec = (mincore_vec_t*) malloc(numPages);

    if (vec == NULL) {
        // TODO: throw "out-of-memory"
        return JNI_FALSE;
    }

    int result = mincore(a, len, vec);
    if (result == -1) {
        free(vec);
        // TODO: throw "mincore failed"
        return JNI_FALSE;
    }

    jboolean loaded = JNI_TRUE;
    for (int i = 0; i < numPages; i++) {
        if (vec[i] == 0) {
            loaded = JNI_FALSE;
            break;
        }
    }

    free(vec);
    return loaded;

#endif /* (_WIN64) */
}

/*
 * Class:     mmap_impl_MMapUtils
 * Method:    load0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_mmap_impl_MMapUtils_load0(JNIEnv* env, jclass,
  jlong address,
  jlong length) {
#if defined (_WIN64)

    WIN32_MEMORY_RANGE_ENTRY range = {(PVOID) jlong_to_ptr(address), (SIZE_T) length};
    // PrefetchVirtualMemory returns non-zero on success
    int result = PrefetchVirtualMemory(GetCurrentProcess(), 1, &range, 0);
    if (result == 0) {
        // TODO: shouldn't that be ignored??
    }

#else /* Linux / Unix */

    char* a = (char *) jlong_to_ptr(address);
    int result = madvise((caddr_t) a, (size_t) length, MADV_WILLNEED);
    if (result == -1) {
        // TODO: throw "madvise MADV_WILLNEED failed"
        // TODO: shouldn't that be ignored??
    }

#endif /* (_WIN64) */
}

/*
 * Class:     mmap_impl_MMapUtils
 * Method:    unload0
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_mmap_impl_MMapUtils_unload0(JNIEnv* env, jclass,
  jlong address,
  jlong length) {
#if defined (_WIN64)
    // TODO: ...

#else /* Linux / Unix */
    // TODO: ...

#endif /* (_WIN64) */
}

/*
 * Class:     mmap_impl_MMapUtils
 * Method:    force0
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL
Java_mmap_impl_MMapUtils_force0(JNIEnv* env, jclass,
  jlong fd,
  jlong address,
  jlong length) {
#if defined (_WIN64)
    HANDLE fileHandle = jlong_to_ptr(fd);
    // TODO: ...

#else /* Linux / Unix */
    // TODO: ...

#endif /* (_WIN64) */
}


#ifdef __cplusplus
}
#endif // #ifdef __cplusplus

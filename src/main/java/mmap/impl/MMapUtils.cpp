/*
 * Copyright 2020 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        return JNI_FALSE;
    }

    int result = mincore(a, len, vec);
    if (result == -1) {
        free(vec);
        return JNI_FALSE;
    }

    jboolean loaded = JNI_TRUE;
    for (int i = 0; i < numPages; ++i) {
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
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_mmap_impl_MMapUtils_load0(JNIEnv* env, jclass,
  jlong address,
  jlong length) {
#if defined (_WIN64)

    WIN32_MEMORY_RANGE_ENTRY range = {(PVOID) jlong_to_ptr(address), (SIZE_T) length};
    // PrefetchVirtualMemory returns non-zero on success
    int result = PrefetchVirtualMemory(GetCurrentProcess(), 1, &range, 0);
    if (result == 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;

#else /* Linux / Unix */

    char* a = (char*) jlong_to_ptr(address);
    int result = madvise((caddr_t) a, (size_t) length, MADV_WILLNEED);
    if (result == -1) {
        return JNI_FALSE;
    }
    return JNI_TRUE;

#endif /* (_WIN64) */
}

/*
 * Class:     mmap_impl_MMapUtils
 * Method:    unload0
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_mmap_impl_MMapUtils_unload0(JNIEnv* env, jclass,
  jlong address,
  jlong length) {
#if defined (_WIN64)

    void* a = jlong_to_ptr(address);
    // If any of the pages in the specified range are not locked,
    // VirtualUnlock removes such pages from the working set,
    // sets last error to ERROR_NOT_LOCKED, and returns FALSE.
    // Calling VirtualUnlock on a range of memory that is not locked
    // releases the pages from the process's working set
    BOOL result = VirtualUnlock((LPVOID) a, (SIZE_T) length);
    if ((result != 0) && (GetLastError() == ERROR_NOT_LOCKED)) {
        return JNI_TRUE;
    }
    if (result == 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;

#else /* Linux / Unix */

    char* a = (char*) jlong_to_ptr(address);
    int result = madvise((caddr_t) a, (size_t) length, MADV_DONTNEED);
    if (result == -1) {
        return JNI_FALSE;
    }
    return JNI_TRUE;

#endif /* (_WIN64) */
}

/*
 * Class:     mmap_impl_MMapUtils
 * Method:    force0
 * Signature: (Ljava/io/FileDescriptor;JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_mmap_impl_MMapUtils_force0(JNIEnv* env, jclass,
  jobject fd,
  jlong address,
  jlong length) {
#if defined (_WIN64)

    void* a = jlong_to_ptr(address);
    /*
     * FlushViewOfFile can fail with ERROR_LOCK_VIOLATION if the memory
     * system is writing dirty pages to disk. As there is no way to
     * synchronize the flushing then we retry a limited number of times.
     */
    int retry = 0;
    BOOL result = 0;
    do {
        result = FlushViewOfFile(a, (SIZE_T) length);
        if ((result != 0) || (GetLastError() != ERROR_LOCK_VIOLATION)) {
            break;
        }
        retry++;
    } while (retry < 3);

    /**
     * FlushViewOfFile only initiates the writing of dirty pages to the
     * disk cache so we have to call FlushFileBuffers to ensure they are
     * physically written to the disk
     */
    if (result != 0 && fd != nullptr) {
        static jfieldID handle_fd;
        if (handle_fd == nullptr) {
            jclass clazz = env->FindClass("java/io/FileDescriptor");
            handle_fd = env->GetFieldID(clazz, "handle", "J");
        }
        HANDLE fileHandle = (HANDLE) jlong_to_ptr(env->GetLongField(fd, handle_fd));
        result = FlushFileBuffers(fileHandle);
        if (result == 0 && GetLastError() == ERROR_ACCESS_DENIED) {
            // this is a read-only mapping
            result = 1;
        }
    }

    if (result == 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;

#else /* Linux / Unix */

    void* a = jlong_to_ptr(address);
    int result = msync(a, (size_t) length, MS_SYNC);
    if (result == -1) {
        return JNI_FALSE;
    }
    return JNI_TRUE;

#endif /* (_WIN64) */
}


#ifdef __cplusplus
}
#endif // #ifdef __cplusplus

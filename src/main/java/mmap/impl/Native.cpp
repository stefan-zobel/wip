
#ifndef _JAVASOFT_JNI_H_
#include <jni.h>
#endif /* _JAVASOFT_JNI_H_ */

#include <string.h>


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


#define MBYTE 1048576

#define GETCRITICAL(bytes, env, obj) { \
    bytes = (jbyte*) env->GetPrimitiveArrayCritical((jarray) obj, NULL); \
}

#define RELEASECRITICAL(bytes, env, obj, mode) { \
    env->ReleasePrimitiveArrayCritical((jarray) obj, bytes, mode); \
}

#define SWAPSHORT(x) ((jshort) (((x) << 8) | (((x) >> 8) & 0xff)))
#define SWAPINT(x)   ((jint) ((SWAPSHORT((jshort) (x)) << 16) | \
                            (SWAPSHORT((jshort) ((x) >> 16)) & 0xffff)))
#define SWAPLONG(x)  ((jlong) (((jlong) SWAPINT((jint) (x)) << 32) | \
                              ((jlong) SWAPINT((jint) ((x) >> 32)) & 0xffffffff)))


#ifdef __cplusplus
extern "C" {
#endif


JNIEXPORT void JNICALL
Java_mmap_impl_Native_copySwapFromShortArray(JNIEnv* env, jobject,
  jobject src,
  jlong srcPos,
  jlong dstAddr,
  jlong length) {

    jbyte* bytes;
    size_t size;

    jshort* dstShort = (jshort*) jlong_to_ptr(dstAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, src);

        jshort* srcShort = (jshort*) (bytes + srcPos);
        jshort* endShort = srcShort + (size / sizeof(jshort));
        while (srcShort < endShort) {
          jshort tmpShort = *srcShort++;
          *dstShort++ = SWAPSHORT(tmpShort);
        }

        RELEASECRITICAL(bytes, env, src, JNI_ABORT);

        length -= size;
        dstAddr += size;
        srcPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copySwapToShortArray(JNIEnv* env, jobject,
  jlong srcAddr,
  jobject dst,
  jlong dstPos,
  jlong length) {

    jbyte* bytes;
    size_t size;

    jshort* srcShort = (jshort*) jlong_to_ptr(srcAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, dst);

        jshort* dstShort = (jshort*) (bytes + dstPos);
        jshort* endShort = srcShort + (size / sizeof(jshort));
        while (srcShort < endShort) {
            jshort tmpShort = *srcShort++;
            *dstShort++ = SWAPSHORT(tmpShort);
        }

        RELEASECRITICAL(bytes, env, dst, 0);

        length -= size;
        srcAddr += size;
        dstPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copySwapFromIntArray(JNIEnv* env, jobject,
  jobject src,
  jlong srcPos,
  jlong dstAddr,
  jlong length) {

    jbyte* bytes;
    size_t size;

    jint* dstInt = (jint*) jlong_to_ptr(dstAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, src);

        jint* srcInt = (jint*) (bytes + srcPos);
        jint* endInt = srcInt + (size / sizeof(jint));
        while (srcInt < endInt) {
            jint tmpInt = *srcInt++;
            *dstInt++ = SWAPINT(tmpInt);
        }

        RELEASECRITICAL(bytes, env, src, JNI_ABORT);

        length -= size;
        dstAddr += size;
        srcPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copySwapToIntArray(JNIEnv* env, jobject,
  jlong srcAddr,
  jobject dst,
  jlong dstPos,
  jlong length) {

    jbyte* bytes;
    size_t size;

    jint* srcInt = (jint*) jlong_to_ptr(srcAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, dst);

        jint* dstInt = (jint*) (bytes + dstPos);
        jint* endInt = srcInt + (size / sizeof(jint));
        while (srcInt < endInt) {
            jint tmpInt = *srcInt++;
            *dstInt++ = SWAPINT(tmpInt);
        }

        RELEASECRITICAL(bytes, env, dst, 0);

        length -= size;
        srcAddr += size;
        dstPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copySwapFromLongArray(JNIEnv* env, jobject,
  jobject src,
  jlong srcPos,
  jlong dstAddr,
  jlong length) {

    jbyte* bytes;
    size_t size;

    jlong* dstLong = (jlong*) jlong_to_ptr(dstAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, src);

        jlong* srcLong = (jlong*) (bytes + srcPos);
        jlong* endLong = srcLong + (size / sizeof(jlong));
        while (srcLong < endLong) {
            jlong tmpLong = *srcLong++;
            *dstLong++ = SWAPLONG(tmpLong);
        }

        RELEASECRITICAL(bytes, env, src, JNI_ABORT);

        length -= size;
        dstAddr += size;
        srcPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copySwapToLongArray(JNIEnv* env, jobject,
  jlong srcAddr,
  jobject dst,
  jlong dstPos,
  jlong length) {

    jbyte* bytes;
    size_t size;

    jlong* srcLong = (jlong*) jlong_to_ptr(srcAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, dst);

        jlong* dstLong = (jlong*) (bytes + dstPos);
        jlong* endLong = srcLong + (size / sizeof(jlong));
        while (srcLong < endLong) {
            jlong tmpLong = *srcLong++;
            *dstLong++ = SWAPLONG(tmpLong);
        }

        RELEASECRITICAL(bytes, env, dst, 0);

        length -= size;
        srcAddr += size;
        dstPos += size;
    }
}

#ifdef __cplusplus
}
#endif // #ifdef __cplusplus

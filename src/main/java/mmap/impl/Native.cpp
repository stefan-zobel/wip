
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
Java_mmap_impl_Native_copyFromShortArray(JNIEnv* env, jobject,
  jobject src,
  jlong srcPos,
  jlong dstAddr,
  jlong length) {

    jbyte* bytes;
    size_t size;
    jshort *srcShort, *dstShort, *endShort;
    jshort tmpShort;

    dstShort = (jshort*) jlong_to_ptr(dstAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, src);

        srcShort = (jshort*) (bytes + srcPos);
        endShort = srcShort + (size / sizeof(jshort));
        while (srcShort < endShort) {
          tmpShort = *srcShort++;
          *dstShort++ = SWAPSHORT(tmpShort);
        }

        RELEASECRITICAL(bytes, env, src, JNI_ABORT);

        length -= size;
        dstAddr += size;
        srcPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copyToShortArray(JNIEnv* env, jobject,
  jlong srcAddr,
  jobject dst,
  jlong dstPos,
  jlong length) {

    jbyte* bytes;
    size_t size;
    jshort *srcShort, *dstShort, *endShort;
    jshort tmpShort;

    srcShort = (jshort*) jlong_to_ptr(srcAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, dst);

        dstShort = (jshort*) (bytes + dstPos);
        endShort = srcShort + (size / sizeof(jshort));
        while (srcShort < endShort) {
            tmpShort = *srcShort++;
            *dstShort++ = SWAPSHORT(tmpShort);
        }

        RELEASECRITICAL(bytes, env, dst, 0);

        length -= size;
        srcAddr += size;
        dstPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copyFromIntArray(JNIEnv* env, jobject,
  jobject src,
  jlong srcPos,
  jlong dstAddr,
  jlong length) {

    jbyte* bytes;
    size_t size;
    jint *srcInt, *dstInt, *endInt;
    jint tmpInt;

    dstInt = (jint*) jlong_to_ptr(dstAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, src);

        srcInt = (jint*) (bytes + srcPos);
        endInt = srcInt + (size / sizeof(jint));
        while (srcInt < endInt) {
            tmpInt = *srcInt++;
            *dstInt++ = SWAPINT(tmpInt);
        }

        RELEASECRITICAL(bytes, env, src, JNI_ABORT);

        length -= size;
        dstAddr += size;
        srcPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copyToIntArray(JNIEnv* env, jobject,
  jlong srcAddr,
  jobject dst,
  jlong dstPos,
  jlong length) {

    jbyte* bytes;
    size_t size;
    jint *srcInt, *dstInt, *endInt;
    jint tmpInt;

    srcInt = (jint*) jlong_to_ptr(srcAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, dst);

        dstInt = (jint*) (bytes + dstPos);
        endInt = srcInt + (size / sizeof(jint));
        while (srcInt < endInt) {
            tmpInt = *srcInt++;
            *dstInt++ = SWAPINT(tmpInt);
        }

        RELEASECRITICAL(bytes, env, dst, 0);

        length -= size;
        srcAddr += size;
        dstPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copyFromLongArray(JNIEnv* env, jobject,
  jobject src,
  jlong srcPos,
  jlong dstAddr,
  jlong length) {

    jbyte* bytes;
    size_t size;
    jlong *srcLong, *dstLong, *endLong;
    jlong tmpLong;

    dstLong = (jlong*) jlong_to_ptr(dstAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, src);

        srcLong = (jlong*) (bytes + srcPos);
        endLong = srcLong + (size / sizeof(jlong));
        while (srcLong < endLong) {
            tmpLong = *srcLong++;
            *dstLong++ = SWAPLONG(tmpLong);
        }

        RELEASECRITICAL(bytes, env, src, JNI_ABORT);

        length -= size;
        dstAddr += size;
        srcPos += size;
    }
}

JNIEXPORT void JNICALL
Java_mmap_impl_Native_copyToLongArray(JNIEnv* env, jobject,
  jlong srcAddr,
  jobject dst,
  jlong dstPos,
  jlong length) {

    jbyte* bytes;
    size_t size;
    jlong *srcLong, *dstLong, *endLong;
    jlong tmpLong;

    srcLong = (jlong*) jlong_to_ptr(srcAddr);

    while (length > 0) {

        if (length > MBYTE) {
            size = MBYTE;
        } else {
            size = (size_t) length;
        }

        GETCRITICAL(bytes, env, dst);

        dstLong = (jlong*) (bytes + dstPos);
        endLong = srcLong + (size / sizeof(jlong));
        while (srcLong < endLong) {
            tmpLong = *srcLong++;
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

/* DO NOT EDIT THIS FILE - it is machine generated */
#include "jni.h"
/* Header for class fly2cam_FlyCamera */

#ifndef _Included_fly2cam_FlyCamera
#define _Included_fly2cam_FlyCamera
#ifdef __cplusplus
extern "C" {
#endif
#undef fly2cam_FlyCamera_FrameRate_15
#define fly2cam_FlyCamera_FrameRate_15 3L
#undef fly2cam_FlyCamera_FrameRate_30
#define fly2cam_FlyCamera_FrameRate_30 4L
/*
 * Class:     fly2cam_FlyCamera
 * Method:    Connect
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_fly2cam_FlyCamera_Connect
  (JNIEnv *, jobject, jint);

/*
 * Class:     fly2cam_FlyCamera
 * Method:    NextFrame
 * Signature: ([B)Z
 */
JNIEXPORT jboolean JNICALL Java_fly2cam_FlyCamera_NextFrame
  (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     fly2cam_FlyCamera
 * Method:    Finish
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_fly2cam_FlyCamera_Finish
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif

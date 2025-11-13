#include <jni.h>
#include <opencv2/opencv.hpp>

using namespace cv;

static Mat rgbaBytesToMat(jbyte* data, int width, int height) {
    // data is RGBA (8UC4)
    Mat mat(height, width, CV_8UC4, (uchar*)data);
    return mat.clone();
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_flamassignment_MainActivity_processFrame(JNIEnv *env, jobject thiz,
                                                         jbyteArray inputArray, jint width, jint height) {
    jbyte *inBytes = env->GetByteArrayElements(inputArray, NULL);
    int inLen = env->GetArrayLength(inputArray);

    // Convert RGBA bytes -> Mat
    Mat rgba = rgbaBytesToMat(inBytes, width, height);

    // Convert to gray
    Mat gray;
    cvtColor(rgba, gray, COLOR_RGBA2GRAY);

    // Apply Canny edge detector
    Mat edges;
    Canny(gray, edges, 80, 180);

    // Return single-channel bytes (rows*cols)
    int outLen = width * height;
    jbyteArray outArray = env->NewByteArray(outLen);
    env->SetByteArrayRegion(outArray, 0, outLen, (jbyte*)edges.data);

    env->ReleaseByteArrayElements(inputArray, inBytes, 0);
    return outArray;
}

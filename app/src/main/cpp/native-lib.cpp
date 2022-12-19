#include <jni.h>
#include "mecab_api/api.h"

class Initializer{
public:
    AssetJNI* asjni;
    OpenJtalk* openJtalk;
    void init_openjtalk(JNIEnv* _env, jobject _obj, jobject _assetManager){
        asjni = new AssetJNI(_env, _obj, _assetManager);
        openJtalk = new OpenJtalk("open_jtalk_dic_utf_8-1.11", asjni);
    }

    ~Initializer();
};

Initializer::~Initializer() {
    free(asjni);
    free(openJtalk);
}

Initializer openJInit;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_moereng_MainActivity_InitOpenJtalk(JNIEnv *env, jobject thiz,
                                                    jobject asset_manager) {
    openJInit.init_openjtalk(env, thiz, asset_manager);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_moereng_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";

    return env->NewStringUTF(hello.c_str());
}


// 生成label并转换为java列表
extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_moereng_utils_Cleaner_extract_1labels(JNIEnv *env, jobject thiz, jstring text) {
    char* ctext = (char*)env->GetStringUTFChars(text, nullptr);
    // 转换编码
    string stext(ctext);
    wstring wtext = utf8_decode(stext);
    jclass array_list_class = env->FindClass("java/util/ArrayList");
    jmethodID array_list_constructor = env->GetMethodID(array_list_class, "<init>","()V");
    jobject array_list = env->NewObject(array_list_class, array_list_constructor);
    jmethodID array_list_add = env->GetMethodID(array_list_class, "add","(Ljava/lang/Object;)Z");
    if (openJInit.openJtalk){
        auto features = openJInit.openJtalk->run_frontend(wtext);
        auto labels = openJInit.openJtalk->make_label(features);

        // vector到列表
        for (const wstring& label: labels){
            jstring str = env->NewStringUTF(utf8_encode(label).c_str());
            env->CallBooleanMethod(array_list, array_list_add, str);
            env->DeleteLocalRef(str);
        }
    }
    return array_list;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_moereng_MainActivity_DestroyOpenJtalk(JNIEnv *env, jobject thiz) {
    delete &openJInit;
}
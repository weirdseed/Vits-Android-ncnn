#include <jni.h>
#include "mecab_api/api.h"
#include "vits/SynthesizerTrn.h"

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

static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_moereng_MainActivity_InitOpenJtalk(JNIEnv *env, jobject thiz,
                                                    jobject asset_manager) {
    openJInit.init_openjtalk(env, thiz, asset_manager);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_moereng_MainActivity_words_1split_1cpp(JNIEnv *env, jobject thiz, jstring text,
                                                        jobject asset_manager) {
    char* ctext = (char*)env->GetStringUTFChars(text, nullptr);
    string stext(ctext);
    auto* assetJni = new AssetJNI(env, thiz, asset_manager);
    string res = openJInit.openJtalk->words_split("open_jtalk_dic_utf_8-1.11", stext.c_str(), assetJni);
    delete assetJni;
    return env->NewStringUTF(res.c_str());
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

SynthesizerTrn net_g;
static Nets* nets;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_moereng_Vits_init_1vits(JNIEnv *env, jobject thiz, jobject asset_manager,
                                         jstring path) {
    nets = new Nets();
    const char *_path = env->GetStringUTFChars(path, nullptr);
    auto *assetJni = new AssetJNI(env, thiz, asset_manager);
    Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
    opt.use_packing_layout = true;

    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;
    if (net_g.init(_path, assetJni, nets, opt)) return true;
    free(assetJni);
    freenets(nets);
    return false;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_moereng_Vits_forward(JNIEnv *env, jobject thiz, jintArray x, jboolean vulkan, jint sid, jfloat noise_scale, jfloat length_scale) {
    int* x_ = env->GetIntArrayElements(x, nullptr);
    jsize x_size = env->GetArrayLength(x);
    Mat data(x_size, 1);
    for (int i = 0; i < data.c; i++){
        float* p = data.channel(i);
        for (int j = 0; j < x_size; j++){
            p[j] = (float)x_[j];
        }
    }
    if (vulkan) LOGI("vulkan on");
    else LOGI("vulkan off");
    auto start = get_current_time();
    auto output = SynthesizerTrn::forward(data, nets, vulkan,sid,false, noise_scale, 0.8, length_scale);
    auto end = get_current_time();
    LOGI("time cost: %f ms", end-start);
    jfloatArray res = env->NewFloatArray(output.h * output.w);
    env->SetFloatArrayRegion(res, 0, output.w * output.h, output);
    return res;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_moereng_MainActivity_testgpu(JNIEnv *env, jobject thiz) {
    int n = get_gpu_count();
    if (n != 0) return JNI_TRUE;
    return JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_moereng_Vits_destroy(JNIEnv *env, jobject thiz) {
    freenets(nets);
}

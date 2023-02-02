#include <datareader.h>
#include "SynthesizerTrn.h"
#include "custom_layer.h"
#include "../mecab_api/api.h"

DEFINE_LAYER_CREATOR(expand_as)

DEFINE_LAYER_CREATOR(flip)

DEFINE_LAYER_CREATOR(Transpose)

DEFINE_LAYER_CREATOR(PRQTransform)

DEFINE_LAYER_CREATOR(ResidualReverse)

DEFINE_LAYER_CREATOR(Embedding)

DEFINE_LAYER_CREATOR(SequenceMask)

DEFINE_LAYER_CREATOR(Attention)

DEFINE_LAYER_CREATOR(ExpandDim)

DEFINE_LAYER_CREATOR(SamePadding)

DEFINE_LAYER_CREATOR(ReduceDim)

DEFINE_LAYER_CREATOR(ZerosLike)

DEFINE_LAYER_CREATOR(RandnLike)

bool SynthesizerTrn::load_model(const std::string &folder, bool multi, Net &net, const Option &opt,
                                const string name) {
    LOGI("loading %s...\n", name.c_str());
    net.register_custom_layer("Tensor.expand_as", expand_as_layer_creator);
    net.register_custom_layer("modules.Transpose", Transpose_layer_creator);
    net.register_custom_layer("Embedding", Embedding_layer_creator);
    net.register_custom_layer("modules.SequenceMask", SequenceMask_layer_creator);
    net.register_custom_layer("attentions.Attention", Attention_layer_creator);
    net.register_custom_layer("attentions.ExpandDim", ExpandDim_layer_creator);
    net.register_custom_layer("attentions.SamePadding", SamePadding_layer_creator);
    net.register_custom_layer("torch.flip", flip_layer_creator);
    net.register_custom_layer("modules.ResidualReverse", ResidualReverse_layer_creator);
    net.register_custom_layer("modules.ReduceDims", ReduceDim_layer_creator);
    net.register_custom_layer("modules.PRQTransform", PRQTransform_layer_creator);
    net.register_custom_layer("torch.zeros_like", ZerosLike_layer_creator);
    net.register_custom_layer("modules.RandnLike", RandnLike_layer_creator);

    net.opt = opt;
    std::string bin_path = join_path(folder, name + ".ncnn.bin");
    std::string param_path = name + ".ncnn.param";
    if (multi) param_path = "multi/" + param_path;
    else param_path = "single/" + param_path;
    bool param_success = !net.load_param(assetManager, param_path.c_str());
    bool bin_success = !net.load_model(bin_path.c_str());
    if (param_success && bin_success) {
        LOGI("%s loaded!", name.c_str());
        return true;
    }
    if (!param_success) LOGE("param load fail");
    if (!bin_success) LOGE("bin load fail");
    return false;
}

bool SynthesizerTrn::load_weight(const std::string &folder, const std::string &name, const int w,
                                 Mat &weight, const int n) {
    LOGI("loading %s...\n", "text embedding");
    std::string path = join_path(folder, name + ".bin");
    FILE *fp = fopen(path.c_str(), "rb");
    if (fp != nullptr) {
        fseek(fp, 0, SEEK_END);
        auto file_size = ftell(fp);
        auto emb_length = file_size / sizeof(float);
        if (emb_length % w != 0) return false;
        int h = int(emb_length / w);
        if (n != -1 && h != n) return false;
        fseek(fp, 0, SEEK_SET);
        weight.create(w, h);
        fread(weight, sizeof(float), emb_length, fp);
        fclose(fp);
        LOGE("text embedding loaded!");
        return true;
    }
    LOGE("text embedding load fail");
    return false;
}

bool SynthesizerTrn::init(const std::string &model_folder, bool voice_convert, bool multi,
                          const int n_vocab,
                          AssetJNI *assetJni, Nets *nets, const Option &opt) {
    assetManager = AAssetManager_fromJava(assetJni->env, assetJni->assetManager);
    if (voice_convert) {
        if (load_weight(model_folder, "emb_t", 192, nets->emb_t, n_vocab) &&
            load_weight(model_folder, "emb_g", 256, nets->emb_g_weight, -1) &&
            load_model(model_folder, multi, nets->enc_q, opt, "enc_q") &&
            load_model(model_folder, multi, nets->dec_net, opt, "dec") &&
            load_model(model_folder, multi, nets->flow, opt, "flow") &&
            load_model(model_folder, multi, nets->flow_reverse, opt, "flow.reverse"))
            return true;
    } else if (multi) {
        if (load_weight(model_folder, "emb_t", 192, nets->emb_t, n_vocab) &&
            load_weight(model_folder, "emb_g", 256, nets->emb_g_weight, -1) &&
            load_model(model_folder, multi, nets->enc_p, opt, "enc_p") &&
            load_model(model_folder, multi, nets->enc_q, opt, "enc_q") &&
            load_model(model_folder, multi, nets->dec_net, opt, "dec") &&
            load_model(model_folder, multi, nets->flow, opt, "flow") &&
            load_model(model_folder, multi, nets->flow_reverse, opt, "flow.reverse") &&
            load_model(model_folder, multi, nets->dp, opt, "dp"))
            return true;
    } else {
        if (load_weight(model_folder, "emb_t", 192, nets->emb_t, n_vocab) &&
            load_model(model_folder, multi, nets->enc_p, opt, "enc_p") &&
            load_model(model_folder, multi, nets->dec_net, opt, "dec") &&
            load_model(model_folder, multi, nets->flow_reverse, opt, "flow.reverse") &&
            load_model(model_folder, multi, nets->dp, opt, "dp"))
            return true;
    }

    return false;
}

std::vector<Mat>
SynthesizerTrn::enc_p_forward(const Mat &x, const Mat &weight, const Net &enc_p,
                              bool vulkan, const int num_threads) {
    Mat length(1);
    length[0] = x.w;
    Extractor ex = enc_p.create_extractor();
    ex.set_num_threads(num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", length);
    ex.input("in2", weight);
    Mat out0, out1, out2, out3;
    ex.extract("out0", out0);
    ex.extract("out1", out1);
    ex.extract("out2", out2);
    ex.extract("out3", out3);
    std::vector<Mat> outputs{
            out0, out1, out2, out3
    };
    return outputs;
}

std::vector<Mat>
SynthesizerTrn::enc_q_forward(const Mat &x, const Mat &g, const Net &enc_q, bool vulkan,
                              const int num_threads) {
    Mat length(1);
    length[0] = x.w;
    Extractor ex = enc_q.create_extractor();
    ex.set_num_threads(num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", length);
    ex.input("in2", g);
    Mat out0, out1;
    ex.extract("out0", out0);
    ex.extract("out1", out1);
    return std::vector<Mat>{out0, out1};
}

Mat SynthesizerTrn::emb_g_forward(int sid, const Mat &weight,
                                  bool vulkan, const int num_threads, const Option &opt) {
    Mat sid_mat(1);
    sid_mat[0] = (float) sid;
    Mat out = embedding(sid_mat, weight, opt);
    return out;
}

Mat SynthesizerTrn::dp_forward(const Mat &x, const Mat &x_mask, const Mat &z, const Mat &g,
                               float noise_scale,
                               const Net &dp, bool vulkan, const int num_threads) {
    Mat out;
    Extractor ex = dp.create_extractor();
    ex.set_num_threads(num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    ex.input("in2", z);

    Mat noise;
    noise.create_like(z);
    noise.fill(noise_scale);

    if (!g.empty()) {
        ex.input("in3", noise);
        ex.input("in4", g);
    } else {
        ex.input("in3", noise);
    }
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::flow_reverse_forward(const Mat &x, const Mat &x_mask, const Mat &g,
                                         const Net &flow_reverse,
                                         bool vulkan, const int num_threads) {
    Extractor ex = flow_reverse.create_extractor();
    ex.set_num_threads(num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    if (!g.empty()) ex.input("in2", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::flow_forward(const Mat &x, const Mat &x_mask, const Mat &g, const Net &flow,
                                 bool vulkan, const int num_threads) {
    Extractor ex = flow.create_extractor();
    ex.set_num_threads(num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    ex.input("in2", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::dec_forward(const Mat &x, const Mat &g, const Net &dec_net, bool vulkan,
                                const int num_threads) {
    Extractor ex = dec_net.create_extractor();
    ex.set_num_threads(num_threads);
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    if (!g.empty()) ex.input("in1", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

SynthesizerTrn::SynthesizerTrn() = default;

// c++ implementation of SynthesizerTrn
Mat SynthesizerTrn::forward(const Mat &data, Nets *nets, int num_threads, bool vulkan, bool multi,
                            int sid, float noise_scale, float noise_scale_w, float length_scale) {
    LOGI("processing...\n");
    Option opt;
    opt.num_threads = num_threads;
    // enc_p
    auto enc_p_out = enc_p_forward(data, nets->emb_t, nets->enc_p, vulkan, num_threads);
    Mat x = enc_p_out[0];
    Mat m_p = enc_p_out[1];
    Mat logs_p = enc_p_out[2];
    Mat x_mask = enc_p_out[3];

    Mat g;
    if (multi){
        g = reducedims(mattranspose(emb_g_forward(sid, nets->emb_g_weight, vulkan, num_threads, opt), opt));
    }

    Mat z = randn(x.w, 2, opt, 1);

    Mat logw = dp_forward(x, x_mask, z, g, noise_scale_w, nets->dp, vulkan, num_threads);

    Mat w = product(matproduct(matexp(logw, opt), x_mask, opt), length_scale, opt);

    Mat w_ceil = ceil(w, opt);

    Mat summed = sum(w_ceil, opt);

    if (summed[0] < 1) summed[0] = 1;

    Mat y_mask = sequence_mask(summed, opt, summed[0]);

    y_mask = mattranspose(y_mask, opt);
    y_mask = reducedims(y_mask);

    Mat attn_mask = matmul(y_mask, x_mask, opt);
    attn_mask = reducedims(attn_mask);

    Mat attn = generate_path(w_ceil, attn_mask, opt);

    m_p = matmul(attn, reducedims(mattranspose(m_p, opt)), opt);
    m_p = reducedims(mattranspose(m_p, opt));

    logs_p = matmul(attn, reducedims(mattranspose(logs_p, opt)), opt);
    logs_p = reducedims(mattranspose(logs_p, opt));

    Mat m_p_rand = randn(m_p.w, m_p.h, opt);

    Mat z_p = matplus(m_p,
                      product(matproduct(m_p_rand, matexp(logs_p, opt), opt), noise_scale, opt),
                      opt);

    z = flow_reverse_forward(expanddims(z_p), mattranspose(expanddims(y_mask), opt), expanddims(g),
                             nets->flow_reverse, vulkan, num_threads);

    y_mask = mattranspose(y_mask, opt);

    y_mask = expand(y_mask, z.w, z.h, opt);

    Mat o = dec_forward(reducedims(matproduct(z, y_mask, opt)), expanddims(g), nets->dec_net,
                        vulkan, num_threads);
    LOGI("finished!\n");
    return o;
}

Mat SynthesizerTrn::voice_convert(const Mat &audio, int raw_sid, int target_sid,
                                  Nets *net, int num_threads, bool vulkan) {
    Option opt;
    opt.num_threads = num_threads;
    LOGI("start converting...\n");
    // stft transform
    auto spec = stft(audio, 1024, 256, 1024, opt)[0];
    spec = matsqrt(Plus(matpow(spec, 2, opt), 1e-6, opt), opt);

    // voice conversion
    auto g_src = mattranspose(emb_g_forward(raw_sid, net->emb_g_weight, vulkan, num_threads, opt), opt);
    auto g_tgt = mattranspose(emb_g_forward(target_sid, net->emb_g_weight, vulkan, num_threads, opt), opt);
    auto enc_q_out = enc_q_forward(spec, g_src, net->enc_q, vulkan, num_threads);
    auto z = expanddims(enc_q_out[0]);
    auto y_mask = enc_q_out[1];
    auto z_p = flow_forward(z, y_mask, g_src, net->flow, vulkan, num_threads);
    auto z_hat = flow_reverse_forward(z_p, y_mask, g_tgt, net->flow_reverse, vulkan, num_threads);
    y_mask = expand(y_mask, z_hat.w, z_hat.h, opt);
    auto o_hat = dec_forward(reducedims(matproduct(z_hat, y_mask, opt)), g_tgt, net->dec_net,
                             vulkan, num_threads);
    LOGI("voice converted!\n");
    return o_hat;
}


SynthesizerTrn::~SynthesizerTrn() = default;

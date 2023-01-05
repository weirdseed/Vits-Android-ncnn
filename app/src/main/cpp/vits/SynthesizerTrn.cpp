#include <datareader.h>
#include "SynthesizerTrn.h"
#include "custom_layer.h"
#include "../mecab_api/api.h"

DEFINE_LAYER_CREATOR(expand_as);

DEFINE_LAYER_CREATOR(flip);

DEFINE_LAYER_CREATOR(Transpose);

DEFINE_LAYER_CREATOR(PRQTransform);

DEFINE_LAYER_CREATOR(CouplingOut);

DEFINE_LAYER_CREATOR(Embedding);

DEFINE_LAYER_CREATOR(SequenceMask);

DEFINE_LAYER_CREATOR(Attention);

DEFINE_LAYER_CREATOR(ExpandDim);

DEFINE_LAYER_CREATOR(SamePadding);

DEFINE_LAYER_CREATOR(ReduceDim);

static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;

bool SynthesizerTrn::load_model(const std::string &folder, Net& net, const Option &opt,
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
    net.register_custom_layer("modules.CouplingOut", CouplingOut_layer_creator);
    net.register_custom_layer("modules.ReduceDims", ReduceDim_layer_creator);
    net.register_custom_layer("modules.PRQTransform", PRQTransform_layer_creator);
    net.register_custom_layer("Embedding", Embedding_layer_creator);

    std::string bin_path;
    if (folder.find_last_of('/') || folder.find_last_of('\\')) {
        bin_path += folder + name + ".ncnn.bin";
    } else {
        bin_path += folder + "/" + name + ".ncnn.bin";
    }
    bool param_success = !net.load_param(assetManager, (name + ".ncnn.param").c_str());
    bool bin_success = !net.load_model(bin_path.c_str());

    if (param_success && bin_success) {
        LOGI("%s loaded!", name.c_str());
        return true;
    }
    if (!param_success) LOGE("param load fail");
    if (!bin_success) LOGE("bin load fail");
    return false;
}

std::vector<Mat> SynthesizerTrn::enc_p_forward(const Mat &x, const Net& enc_p) {
    Mat length(1);
    length[0] = x.w;
    Extractor ex = enc_p.create_extractor();
    ex.input("in0", x);
    ex.input("in1", length);
    Mat out0, out1, out2, out3, test;
    ex.extract("2", test);
    ex.extract("out0", out0);
    ex.extract("out1", out1);
    ex.extract("out2", out2);
    ex.extract("out3", out3);
    std::vector<Mat> outputs{
            out0, out1, out2, out3
    };
    return outputs;
}

Mat SynthesizerTrn::emb_g_forward(int sid, const Net& emb_g, bool vulkan) {
    Mat sid_mat(1);
    sid_mat[0] = (float) sid;
    Mat out;
    auto ex = emb_g.create_extractor();
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", sid_mat);
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::dp_forward(const Mat &x, const Mat &x_mask, const Mat &z, const Mat &g,
                               const Net& dp, bool vulkan) {
    Mat out;
    auto ex = dp.create_extractor();
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    ex.input("in2", z);
    ex.input("in3", g);
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::flow_forward(const Mat &x, const Mat &x_mask, const Mat &g, const Net& flow,
                                 bool vulkan) {
    auto ex = flow.create_extractor();
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", x_mask);
    ex.input("in2", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

Mat SynthesizerTrn::dec_forward(const Mat &x, const Mat &g, const Net& dec_net, bool vulkan) {
    auto ex = dec_net.create_extractor();
    ex.set_vulkan_compute(vulkan);
    ex.input("in0", x);
    ex.input("in1", g);
    Mat out;
    ex.extract("out0", out);
    return out;
}

bool SynthesizerTrn::init(const std::string &model_folder, AssetJNI *assetJni, Nets* nets,
                          const Option &opt) {
    assetManager = AAssetManager_fromJava(assetJni->env, assetJni->assetManager);
    if (load_model(model_folder, nets->enc_p, opt, "enc_p") &&
        load_model(model_folder, nets->dec_net, opt, "dec") &&
        load_model(model_folder, nets->flow, opt, "flow") &&
        load_model(model_folder, nets->dp, opt, "dp") &&
        load_model(model_folder, nets->emb_g, opt, "emb_g"))
        return true;
    return false;
}

SynthesizerTrn::SynthesizerTrn() {

}

Mat SynthesizerTrn::forward(const Mat &data, Nets* nets, bool vulkan, int sid, bool voice_convrt,
                        float noise_scale, float noise_scale_w, float length_scale) {
    LOGI("processing...\n");
    if (voice_convrt) return Mat();
    else {
        Option opt;
        opt.num_threads = 4;
        // enc_p
        auto enc_p_out = enc_p_forward(data, nets->enc_p);
        Mat x = reducedims(enc_p_out[0]);
        Mat m_p = enc_p_out[1];
        Mat logs_p = enc_p_out[2];
        Mat x_mask = reducedims(enc_p_out[3]);

        Mat g = mattranspose(emb_g_forward(sid, nets->emb_g, vulkan), opt);

        g = reducedims(g);

        Mat z = randn(x.w, 2, opt);

        Mat logw = dp_forward(x, x_mask, z, g, nets->dp, vulkan);

        Mat w = product(matproduct(matexp(logw, opt), x_mask, opt), length_scale, opt);

        Mat w_ceil = ceil(w, opt);

        Mat summed = sum(w_ceil, opt);

        if (summed[0] < 1) summed[0] = 1;

        Mat y_length = summed.clone();
        Mat y_mask = sequence_mask(y_length, opt, y_length[0]);

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

        z = flow_forward(expanddims(z_p), mattranspose(expanddims(y_mask), opt), expanddims(g),
                         nets->flow, vulkan);

        y_mask = mattranspose(y_mask, opt);

        y_mask = expand(y_mask, z.w, z.h, opt);

        Mat o = dec_forward(reducedims(matproduct(z, y_mask, opt)), expanddims(g), nets->dec_net,
                            vulkan);
        LOGI("finished!\n");
        return o;
//        return Mat();
    }
    return Mat();
}

SynthesizerTrn::~SynthesizerTrn() {

}

void freenets(Nets* nets){
    free(nets);
}







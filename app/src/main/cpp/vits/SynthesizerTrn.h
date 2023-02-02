#ifndef SYNTHESIZERTRN_H
#define SYNTHESIZERTRN_H

#include "utils.h"
#include "../asset_manager_api/manager.h"

struct Nets {
    Mat emb_t;
    Mat emb_g_weight;
    Net enc_p;
    Net enc_q;
    Net dec_net;
    Net flow_reverse;
    Net flow;
    Net dp;
};

class SynthesizerTrn {
private:

    AAssetManager *assetManager{};

    static bool
    load_weight(const std::string &folder, const std::string &name, const int w, Mat &weight,
                const int n);

    bool load_model(const std::string &folder, bool multi, Net &net, const Option &opt,
                    const string name);

    static std::vector<Mat>
    enc_p_forward(const Mat &x, const Mat &weight, const Net &enc_p, bool vulkan,
                  const int num_threads);

    static std::vector<Mat>
    enc_q_forward(const Mat &x, const Mat &g, const Net &enc_q, bool vulkan, const int num_threads);

    static Mat
    emb_g_forward(int sid, const Mat &weight, bool vulkan, const int num_threads,
                  const Option &opt);

    static Mat
    dp_forward(const Mat &x, const Mat &x_mask, const Mat &z, const Mat &g, float noise_scale,
               const Net &dp,
               bool vulkan, const int num_threads);

    static Mat
    flow_reverse_forward(const Mat &x, const Mat &x_mask, const Mat &g, const Net &flow_reverse,
                         bool vulkan, const int num_threads);

    static Mat
    flow_forward(const Mat &x, const Mat &x_mask, const Mat &g, const Net &flow, bool vulkan,
                 const int num_threads);

    static Mat
    dec_forward(const Mat &x, const Mat &g, const Net &dec, bool vulkan, const int num_threads);

public:

    bool init(const std::string &model_folder, bool voice_convert, bool multi, const int n_vocab,
              AssetJNI *assetJni, Nets *nets, const Option &opt);

    SynthesizerTrn();

    static Mat
    forward(const Mat &x, Nets *nets, int num_threads, bool vulkan = false, bool multi = false,
            int sid = 0, float noise_scale = .667, float noise_scale_w = 0.8,
            float length_scale = 1);

    static Mat voice_convert(const Mat &x, int raw_sid, int target_sid,
                             Nets *net, int num_threads, bool vulkan = false);

    ~SynthesizerTrn();
};

#endif
#pragma once
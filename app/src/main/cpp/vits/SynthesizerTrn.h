#ifndef SYNTHESIZERTRN_H
#define SYNTHESIZERTRN_H

#include "utils.h"
#include "../asset_manager_api/manager.h"

struct Nets{
    Net enc_p;
    Net dec_net;
    Net flow;
    Net dp;
    Net emb_g;
};

class SynthesizerTrn {
private:

    AAssetManager* assetManager{};

    bool load_model(const std::string& folder, Net& net, const Option& opt, const string name);

    static std::vector<Mat> enc_p_forward(const Mat& x, const Net& enc_p, bool vulkan);
    static Mat emb_g_forward(int sid, const Net& emb_g, bool vulkan);
    static Mat dp_forward(const Mat& x, const Mat& x_mask, const Mat& z, const Mat& g, const Net& dp, bool vulkan);
    static Mat flow_forward(const Mat& x, const Mat& x_mask, const Mat& g, const Net& flow, bool vulkan);
    static Mat dec_forward(const Mat& x, const Mat& g, const Net& dec, bool vulkan);

public:

    bool init(const std::string& model_folder, AssetJNI* assetJni, Nets* nets, const Option& opt);
    SynthesizerTrn();
    static Mat forward(const Mat& x, Nets* nets, bool vulkan = false, int sid = 0, bool voice_convrt = false, float noise_scale = .667, float noise_scale_w = 0.8, float length_scale = 1);
    ~SynthesizerTrn();
};

void freenets(Nets* nets);

#endif
#pragma once
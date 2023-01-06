#ifndef CUSTOM_LAYER_H
#define CUSTOM_LAYER_H

#include <iostream>
#include <vector>
#include <numeric>
#include "utils.h"


class expand_as : public Layer {
public:
    expand_as() {
        one_blob_only = false;
        support_inplace = false;
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &from = bottom_blobs[0]; // h=192, w=1
        const Mat &to = bottom_blobs[1]; // h_t=192, w_t=100

        int w = from.w;
        int h = from.h;
        int w_t = to.w;
        int h_t = to.h;

        Mat &top_blob = top_blobs[0];
        top_blob.create_like(to);
        if (top_blob.empty()) return -100;

        if (w == w_t && h == 1) {
#pragma omp parallel for num_threads(opt.num_threads)
            for (int i = 0; i < to.c; i++) {
                const float *from_ptr = from.channel(i);
                float *out_ptr = top_blob.channel(i);
                for (int j = 0; j < h_t; j++) {
                    memcpy(out_ptr, from_ptr, w_t * sizeof(float));
                    out_ptr += w_t;
                }
            }
            return 0;
        }

        if (w == 1 && h == h_t) {
#pragma omp parallel for num_threads(opt.num_threads)
            for (int i = 0; i < to.c; i++) {
                const float *from_ptr = from.channel(i);
                float *out_ptr = top_blob.channel(i);
                for (int j = 0; j < w_t; j++) {
                    for (int k = 0; k < h_t; k++) {
                        int id = j + w_t * k;
                        out_ptr[id] = from_ptr[k];
                    }
                }
            }
            return 0;
        }
        return 0;
    }
};

class flip : public Layer {
public:
    flip() {
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &bottom_blob = bottom_blobs[0];
        Mat &top_blob = top_blobs[0];
        top_blob.create_like(bottom_blob);
        if (top_blob.empty()) return -100;
        int c = bottom_blob.c;
        int w = bottom_blob.w;
        int h = bottom_blob.h;

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < c; i++) {
            const float *ptr = bottom_blob.channel(i);
            ptr += w * h;
            float *out_ptr = top_blob.channel(i);
            for (int j = 0; j < h; j++) {
                memcpy(out_ptr, ptr - w, w * sizeof(float));
                out_ptr += w;
                ptr -= w;
            }
        }
        return 0;
    }
};

class Transpose : public Layer {
public:
    Transpose() {
        one_blob_only = true;
    }

    virtual int forward(const Mat &bottom_blob, Mat &top_blob, const Option &opt) const {
        top_blob = mattranspose(bottom_blob, opt);
        if (top_blob.empty()) return -100;
        return 0;
    }
};

class PRQTransform : public Layer {
public:
    PRQTransform() {
        one_blob_only = false;
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &inputs = bottom_blobs[1];
        const Mat &unnormalized_widths = bottom_blobs[2];
        const Mat &unnormalized_heights = bottom_blobs[3];
        const Mat &unnormalized_derivatives_ = bottom_blobs[0];

        Mat &top_blob = top_blobs[0];

        float left = -5.0;
        float right = 5.0;
        float bottom = -5.0;
        float top = 5.0;
        float min_bin_width = 1e-3;
        float min_bin_height = 1e-3;
        float min_derivative = 1e-3;
        float num_bins = unnormalized_widths.w;

        float constant = log(exp(1 - min_derivative) - 1);
        Mat unnormalized_derivatives = unnormalized_derivatives_.clone();
        set_column_value(unnormalized_derivatives, 0, constant, opt);
        set_column_value(unnormalized_derivatives, -1, constant, opt);

        Mat widths = softmax(unnormalized_widths, opt);
        if (widths.empty()) return -100;

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < widths.c; i++) {
            float *ptr = widths.channel(i);
            for (int j = 0; j < widths.w * widths.h; j++) {
                ptr[j] = (1 - min_bin_width * num_bins) * ptr[j] + min_bin_width;
            }
        }

        Mat cumwidths = cumsum(widths, opt);

        //cumwidths = pad(cumwidths, opt);
        cumwidths = pad(cumwidths, 0, 0, 1, 0, 0, opt);

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < cumwidths.c; i++) {
            float *ptr = cumwidths.channel(i);
            for (int j = 0; j < cumwidths.w * cumwidths.h; j++) {
                ptr[j] = (right - left) * ptr[j] + left;
            }
        }

        set_column_value(cumwidths, 0, left, opt);
        set_column_value(cumwidths, -1, right, opt);

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < widths.c; i++) {
            float *widths_ptr = widths.channel(i);
            float *cumwidths_ptr = cumwidths.channel(i);
            for (int j = 0; j < widths.h; j++) {
                for (int k = 0; k < widths.w; k++) {
                    widths_ptr[k] = cumwidths_ptr[k + 1] - cumwidths_ptr[k];
                }
                widths_ptr += widths.w;
                cumwidths_ptr += cumwidths.w;
            }
        }

        Mat derivatives = softplus(unnormalized_derivatives, opt);

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < derivatives.c; i++) {
            float *d_ptr = derivatives.channel(i);
            for (int j = 0; j < derivatives.w * derivatives.h; j++) {
                d_ptr[j] += min_derivative;
            }
        }

        Mat heights = softmax(unnormalized_heights, opt);
#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < heights.c; i++) {
            float *h_ptr = heights.channel(i);
            for (int j = 0; j < heights.w * heights.h; j++) {
                h_ptr[j] = min_bin_height + (1. - min_bin_height * num_bins) * h_ptr[j];
            }
        }
        Mat cumheights = cumsum(heights, opt);
        //cumheights = pad(cumheights, opt);
        cumheights = pad(cumheights, 0, 0, 1, 0, 0, opt);
#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < cumheights.c; i++) {
            float *cum_h_ptr = cumheights.channel(i);
            for (int j = 0; j < cumheights.h * cumheights.w; j++) {
                cum_h_ptr[j] = (top - bottom) * cum_h_ptr[j] + bottom;
            }
        }
        set_column_value(cumheights, 0, bottom, opt);
        set_column_value(cumheights, -1, top, opt);

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < heights.c; i++) {
            float *heights_ptr = heights.channel(i);
            float *cumheights_ptr = cumheights.channel(i);
            for (int j = 0; j < heights.h; j++) {
                for (int k = 0; k < heights.w; k++) {
                    heights_ptr[k] = cumheights_ptr[k + 1] - cumheights_ptr[k];
                }
                heights_ptr += heights.w;
                cumheights_ptr += cumheights.w;
            }
        }
        Mat bin_idx = searchsorted(cumheights, inputs, opt);

        Mat input_cumwidths = gather(cumwidths, bin_idx, opt);

        Mat input_bin_widths = gather(widths, bin_idx, opt);

        Mat input_cumheights = gather(cumheights, bin_idx, opt);

        Mat delta = matdiv(heights, widths, opt);

        Mat input_delta = gather(delta, bin_idx, opt);

        Mat input_derivatives = gather(derivatives, bin_idx, opt);

        Mat derivatives_plus_one;
        derivatives_plus_one.create(derivatives.w - 1, derivatives.h, 1);

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < derivatives.c; i++) {
            float *d_ptr = derivatives.channel(i);
            float *d_pl_ptr = derivatives_plus_one.channel(i);
            for (int j = 0; j < derivatives.h; j++) {
                for (int k = 0; k < derivatives.w - 1; k++) {
                    d_pl_ptr[k] = d_ptr[k + 1];
                }
                d_ptr += derivatives.w;
                d_pl_ptr += derivatives_plus_one.w;
            }
        }
        Mat input_derivatives_plus_one = gather(derivatives_plus_one, bin_idx, opt);

        Mat input_heights = gather(heights, bin_idx, opt);

        Mat inputs_ = inputs.clone();

        Mat a1 = matminus(inputs_, input_cumheights, opt);
        Mat a2 = matplus(input_derivatives, input_derivatives_plus_one, opt);
        Mat a3 = product(input_delta, 2, opt);
        Mat a4 = matminus(a2, a3, opt);
        Mat a5 = matproduct(a1, a4, opt);

        Mat a6 = matminus(input_delta, input_derivatives, opt);
        Mat a7 = matproduct(input_heights, a6, opt);

        Mat a = matplus(a5, a7, opt);

        Mat b1 = matproduct(input_heights, input_derivatives, opt);

        Mat b2 = matminus(inputs_, input_cumheights, opt);

        Mat b3 = matplus(input_derivatives, input_derivatives_plus_one, opt);
        Mat b4 = product(input_delta, 2, opt);

        Mat b5 = matminus(b3, b4, opt);

        Mat b6 = matproduct(b2, b5, opt);

        Mat b = matminus(b1, b6, opt);

        Mat c1 = matminus(input_cumheights, inputs_, opt);
        Mat c = matproduct(input_delta, c1, opt);

        Mat discriminant1 = matpow(b, 2, opt);

        Mat discriminant2 = matproduct(a, c, opt);
        Mat discriminant3 = product(discriminant2, 4, opt);

        Mat discriminant = matminus(discriminant1, discriminant3, opt);

        Mat root1 = product(c, -2, opt);

        Mat root2 = matsqrt(discriminant, opt);
        Mat root3 = matplus(b, root2, opt);

        Mat root = matdiv(root1, root3, opt);

        Mat outputs1 = matproduct(root, input_bin_widths, opt);
        top_blob = matplus(outputs1, input_cumwidths, opt);
        if (top_blob.empty()) return -100;

        return 0;
    }
};

class CouplingOut : public Layer {
public:
    CouplingOut() {
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &x0 = bottom_blobs[3];
        const Mat &x1 = bottom_blobs[0];
        const Mat &stats = bottom_blobs[1];
        const Mat &x_mask = bottom_blobs[2];

        Mat &top_blob = top_blobs[0];

        int w = x1.w;
        int h = x1.h;
        int c = x1.c;
        top_blob.create(w, 2 * h, c);
        if (top_blob.empty()) return -100;

        Mat x = matminus(x1, stats, opt);
        Mat x2 = matproduct(x, x_mask, opt);

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < c; i++) {
            float *out_ptr = top_blob.channel(i);
            const float *ptr0 = x0.channel(i);
            float *ptr2 = x2.channel(i);
            for (int i = 0; i < 2 * h; i++) {
                if (i < h) {
                    memcpy(out_ptr, ptr0, w * sizeof(float));
                    ptr0 += w;
                } else {
                    memcpy(out_ptr, ptr2, w * sizeof(float));
                    ptr2 += w;
                }
                out_ptr += w;
            }
        }
        return 0;
    }
};

class Embedding : public Layer {
private:
    // param
    int num_embeddings = 1;
    int embedding_dim = 1;
    int bias_term = 0;

    int weight_data_size = 0;

    // model
    Mat weight_data;
    Mat bias_data;

public:
    Embedding() {
        one_blob_only = true;
    }

    virtual int load_param(const ParamDict &pd) {
        num_embeddings = pd.get(0, 0);
        embedding_dim = pd.get(1, 0);
        bias_term = pd.get(2, 0);
        weight_data_size = pd.get(3, 0);
        return 0;
    }

    virtual int load_model(const ModelBin &mb) {
        weight_data = mb.load(weight_data_size, 0);
        if (weight_data.empty())
            return -100;
        weight_data = weight_data.reshape(num_embeddings, embedding_dim);
        return 0;
    }

    virtual int forward(const Mat &bottom_blob, Mat &top_blob, const Option &opt) const {
        int size = bottom_blob.total();
        top_blob.create(weight_data.w, size);
        if (top_blob.empty()) return -100;

#pragma omp parallel for num_threads(opt.num_threads)
        for (int i = 0; i < size; i++) {
            float word_index = ((const float *) bottom_blob)[i];
            const float *weight_row = weight_data.row((int) word_index);
            float *out_row = top_blob.row(i);
            memcpy(out_row, weight_row, weight_data.w * sizeof(float));
        }
        return 0;
    }
};

class SequenceMask : public Layer {
public:
    SequenceMask() {
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &x = bottom_blobs[0];
        const Mat &x_length = bottom_blobs[1];

        Mat &top_blob = top_blobs[0];
        top_blob = sequence_mask(x_length, opt);

        return 0;
    }
};

class Attention : public Layer {
private:
    int n_heads = 2;
    int k_channels = 96;
    int window_size = 4;
public:
    Attention() {
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &query_ = bottom_blobs[4];
        const Mat &key_ = bottom_blobs[3];
        const Mat &value_ = bottom_blobs[5];
        const Mat &attn_mask = bottom_blobs[2];
        const Mat &emb_rel_k = bottom_blobs[0];
        const Mat &emb_rel_v = bottom_blobs[1];

        Mat attn_mask_t = mattranspose(attn_mask, opt);

        Mat mask = matmul(attn_mask_t, attn_mask, opt);

        int t_t = query_.w;
        int t_s = key_.w;
        int d = key_.h;

        Mat query = query_.reshape(t_t, k_channels, n_heads);
        query = mattranspose(query, opt);

        Mat key = key_.reshape(t_t, k_channels, n_heads);
        key = mattranspose(key, opt);

        Mat value = value_.reshape(t_t, k_channels, n_heads);
        value = mattranspose(value, opt);

        Mat scores = matmul(div(query, sqrt(k_channels), opt), mattranspose(key, opt), opt);

        Mat key_relative_embeddings = _get_relative_embeddings(emb_rel_k, t_s, window_size, opt);

        Mat rel_logits = _matmul_with_relative_keys(div(query, sqrt(k_channels), opt),
                                                    key_relative_embeddings, opt);

        Mat scores_local = _relative_position_to_absolute_position(rel_logits, opt);

        scores = matplus(scores, scores_local, opt);

        mask_fill(scores, mask, "=", 0, -1e4, opt);

        Mat p_attn = softmax(scores, opt);

        //dropout(p_attn);
        Mat output = matmul(p_attn, value, opt);
        Mat relative_weights = _absolute_position_to_relative_position(p_attn, opt);
        Mat value_relative_embeddings = _get_relative_embeddings(emb_rel_v, t_s, window_size, opt);
        output = matplus(output,
                         _matmul_with_relative_values(relative_weights, value_relative_embeddings,
                                                      opt), opt);
        output = mattranspose(output, opt);
        Mat &top_blob = top_blobs[0];
        top_blob = output.reshape(t_t, d);
        return 0;
    }


};

class ExpandDim : public Layer {
public:
    ExpandDim() {
    }

    virtual int forward(const std::vector<Mat> &bottom_blobs, std::vector<Mat> &top_blobs,
                        const Option &opt) const {
        const Mat &x = bottom_blobs[0];
        const Mat &y = bottom_blobs[1];

        Mat &top_blob = top_blobs[0];
        top_blob = x.reshape(x.w, x.h, 1);

        return 0;
    }
};

class ReduceDim : public Layer {
public:
    ReduceDim() {
        one_blob_only = true;
    }

    virtual int forward(const Mat &bottom_blob, Mat &top_blob, const Option &opt) const {

        top_blob = reducedims(bottom_blob);
        if (top_blob.empty()) return -100;
        return 0;
    }
};

class SamePadding : public Layer {
private:
    int kernel_size = 3;
public:
    SamePadding() {
        one_blob_only = true;
    }

    virtual int forward(const Mat &bottom_blob, Mat &top_blob, const Option &opt) const {
        int pad_l = (kernel_size - 1) / 2;
        int pad_r = kernel_size / 2;
        Mat padded = pad(bottom_blob, 0, 0, pad_l, pad_r, 0, opt);
        Mat reduced_padded = padded.reshape(padded.w, padded.h);
        top_blob = reduced_padded.clone();
        return 0;
    }
};


#endif
#pragma once
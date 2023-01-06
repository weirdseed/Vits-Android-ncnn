#ifndef UTILS_H
#define UTILS_H
#include <vector>
#include <math.h>
#include <numeric>
#include <iostream>
#include <string>
#include <iomanip>
#include <random>


#include "layer.h"
#include "net.h"
#include "benchmark.h"
#include "cpu.h"

using namespace ncnn;

void pretty_print(const ncnn::Mat& m, const Option& opt, const char* name = "");

Mat softmax(const Mat& blob, const Option& opt, int axis=-1); // 按行求softmax

Mat cumsum(const Mat& blob, const Option& opt);

Mat pad(const Mat& blob, int pad_top, int pad_bottom, int pad_left, int pad_right, float pad_value, const Option& opt); // 横向右部填充

Mat Slice(const Mat& blob, int top, int bottom, int left, int right,  const Option& opt);

Mat reducedims(const Mat& m);

Mat expanddims(const Mat& m);

void set_column_value(Mat& blob, int column, float value, const Option& opt);

Mat softplus(const Mat& blob, const Option& opt);

Mat searchsorted(Mat& bin_locations, const Mat& inputs, const Option& opt);

Mat gather(Mat& blob, Mat& index, const Option& opt);

Mat matplus(const Mat& m1, const Mat& m2, const Option& opt);

Mat matminus(const Mat& m1, const Mat& m2, const Option& opt);

Mat matdiv(const Mat& m1, const Mat& m2, const Option& opt);

Mat matproduct(const Mat& m1, const Mat& m2, const Option& opt);

Mat product(const Mat& m, float value, const Option& opt);

Mat matpow(const Mat& m, float value, const Option& opt);

Mat matexp(const Mat& m, const Option& opt);

Mat ceil(const Mat& m, const Option& opt);

Mat sum(const Mat& m, const Option& opt);

Mat div(const Mat& m, float value, const Option& opt);

Mat matsqrt(const Mat& m, const Option& opt);

float matmax(const Mat& m, const Option& opt);

Mat expand(const Mat& m, int w, int h, const Option& opt);

Mat randn(int w, int h, const Option& opt, int c = 0);

Mat sequence_mask(const Mat& length,  const Option& opt, float max_length_ = 0);

Mat generate_path(const Mat& duration, const Mat mask, const Option& opt);

Mat mattranspose(const Mat& m, const Option& opt);

Mat matmul(const Mat& m1, const Mat& m2, const Option& opt);

void mask_fill(Mat& m, const Mat& mask, const char* condition, float condition_value, float value, const Option& opt);

Mat _get_relative_embeddings(const Mat& relative_embeddings, int length, int window_size, const Option& opt);

Mat _matmul_with_relative_keys(const Mat& x, const Mat& y, const Option& opt);

Mat _relative_position_to_absolute_position(const Mat& x, const Option& opt);

Mat _absolute_position_to_relative_position(const Mat& x, const Option& opt);

Mat _matmul_with_relative_values(const Mat& x, const Mat& y, const Option& opt);
#endif
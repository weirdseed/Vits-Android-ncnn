#include "utils.h"

void pretty_print(const ncnn::Mat& m, const Option& opt, const char* name)
{
    if (strlen(name) > 0) {
        std::cout << name << ":\n";
    }
    if (m.dims == 1)
        std::cout << "shape is: " << "(" << m.w << ")" << std::endl;
    if (m.dims == 2)
        std::cout << "shape is:" << "(" << m.h << "x" << m.w << ")" << std::endl;
    if (m.dims == 3)
        std::cout << "shape is:" << "(" <<m.c << "x" << m.h << "x" << m.w << ")" << std::endl;
    if (m.dims == 4)
        std::cout << "shape is:" << "(" << m.c << "x" << m.c << "x" << m.h << "x" << m.w << ")" << std::endl;
    std::cout << std::setiosflags(std::ios::fixed);
    std::cout << std::setprecision(4);
    if (m.h * m.w * m.c <= 300) {
#pragma omp parallel for num_threads(opt.num_threads)
        for (int q = 0; q < m.c; q++) {
            if (m.c > 1) std::cout << "\n\nchannel " << q + 1 << ":" << std::endl;
            const float* ptr = m.channel(q);
            for (int y = 0; y < m.h; y++) {
                for (int x = 0; x < m.w; x++) {
                    std::cout << ptr[x] << "\t";
                }
                std::cout << "\n";
                ptr += m.w;
            }
        }
        std::cout << "\n------------------------\n";
        return;
    }
#pragma omp parallel for num_threads(opt.num_threads)
    for (int q = 0; q < m.c; q++)
    {
        if (m.c > 1) std::cout << "\n\nchannel " << q+1 << ":" << std::endl;
        const float* ptr = m.channel(q);
        if (m.h > 10) {
            for (int y = 0; y < 3; y++) {
                if (m.w <= 20) {
                    for (int x = 0; x < m.w; x++) {
                        std::cout  << ptr[x] << "\t";
                    }
                }
                else {
                    for (int x = 0; x < 3; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                    std::cout << "...\t";
                    for (int x = m.w - 3; x < m.w; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                }
                ptr += m.w;
                std::cout << "\n";
            }
            std::cout << "...\n";
            ptr += m.w * (m.h - 6);
            for (int y = 0; y < 3; y++) {
                if (m.w <= 20) {
                    for (int x = 0; x < m.w; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                }
                else {
                    for (int x = 0; x < 3; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                    std::cout << "...\t";
                    for (int x = m.w - 3; x < m.w; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                }
                ptr += m.w;
                std::cout << "\n";
            }

        }
        else {
            for (int y = 0; y < m.h; y++) {
                if (m.w <= 20) {
                    for (int x = 0; x < m.w; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                }
                else {
                    for (int x = 0; x < 3; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                    std::cout << "...\t";
                    for (int x = m.w - 3; x < m.w; x++) {
                        std::cout << ptr[x] << "\t";
                    }
                }
                std::cout << "\n";
                ptr += m.w;
            }
        }

    }
    std::cout << "\n------------------------\n";

}

Mat softmax(const Mat& m, const Option& opt, int axis) {
	// value = exp( value - global max value )
	// sum all value
	// value = value / sum
	Mat blob = m.clone();
	int dims = blob.dims;
	size_t elemsize = blob.elemsize;
	int positive_axis = axis < 0 ? dims + axis : axis;

	if (dims == 1) // positive_axis == 0
	{
		int w = blob.w;

		float* ptr = blob;

		float flt_max = -FLT_MAX;
		for (int i = 0; i < w; i++)
		{
			flt_max = std::max(flt_max, ptr[i]);
		}

		float sum = 0.f;
		for (int i = 0; i < w; i++)
		{
			ptr[i] = static_cast<float>(exp(ptr[i] - flt_max));
			sum += ptr[i];
		}

		for (int i = 0; i < w; i++)
		{
			ptr[i] /= sum;
		}
	}

	if (dims == 2 && positive_axis == 0)
	{
		int w = blob.w;
		int h = blob.h;

		Mat max;
		max.create(w, elemsize, opt.workspace_allocator);
		if (max.empty())
			return Mat();
		max.fill(-FLT_MAX);

		for (int i = 0; i < h; i++)
		{
			const float* ptr = blob.row(i);
			for (int j = 0; j < w; j++)
			{
				max[j] = std::max(max[j], ptr[j]);
			}
		}

		Mat sum;
		sum.create(w, elemsize, opt.workspace_allocator);
		if (sum.empty())
			return Mat();
		sum.fill(0.f);

		for (int i = 0; i < h; i++)
		{
			float* ptr = blob.row(i);
			for (int j = 0; j < w; j++)
			{
				ptr[j] = static_cast<float>(exp(ptr[j] - max[j]));
				sum[j] += ptr[j];
			}
		}

		for (int i = 0; i < h; i++)
		{
			float* ptr = blob.row(i);
			for (int j = 0; j < w; j++)
			{
				ptr[j] /= sum[j];
			}
		}
	}

	if (dims == 2 && positive_axis == 1)
	{
		int w = blob.w;
		int h = blob.h;

		for (int i = 0; i < h; i++)
		{
			float* ptr = blob.row(i);
			float m = -FLT_MAX;
			for (int j = 0; j < w; j++)
			{
				m = std::max(m, ptr[j]);
			}

			float s = 0.f;
			for (int j = 0; j < w; j++)
			{
				ptr[j] = static_cast<float>(exp(ptr[j] - m));
				s += ptr[j];
			}

			for (int j = 0; j < w; j++)
			{
				ptr[j] /= s;
			}
		}
	}

	if (dims == 3 && positive_axis == 0)
	{
		int w = blob.w;
		int h = blob.h;
		int channels = blob.c;
		int size = w * h;

		Mat max;
		max.create(w, h, elemsize, opt.workspace_allocator);
		if (max.empty())
			return Mat();
		max.fill(-FLT_MAX);
		for (int q = 0; q < channels; q++)
		{
			const float* ptr = blob.channel(q);

			for (int i = 0; i < size; i++)
			{
				max[i] = std::max(max[i], ptr[i]);
			}
		}

		Mat sum;
		sum.create(w, h, elemsize, opt.workspace_allocator);
		if (sum.empty())
			return Mat();
		sum.fill(0.f);
		for (int q = 0; q < channels; q++)
		{
			float* ptr = blob.channel(q);

			for (int i = 0; i < size; i++)
			{
				ptr[i] = static_cast<float>(exp(ptr[i] - max[i]));
				sum[i] += ptr[i];
			}
		}

#pragma omp parallel for num_threads(opt.num_threads)
		for (int q = 0; q < channels; q++)
		{
			float* ptr = blob.channel(q);

			for (int i = 0; i < size; i++)
			{
				ptr[i] /= sum[i];
			}
		}
	}

	if (dims == 3 && positive_axis == 1)
	{
		int w = blob.w;
		int h = blob.h;
		int channels = blob.c;

		Mat max;
		max.create(w, channels, elemsize, opt.workspace_allocator);
		if (max.empty())
			return Mat();
		max.fill(-FLT_MAX);
#pragma omp parallel for num_threads(opt.num_threads)
		for (int q = 0; q < channels; q++)
		{
			const float* ptr = blob.channel(q);
			float* maxptr = max.row(q);

			for (int i = 0; i < h; i++)
			{
				for (int j = 0; j < w; j++)
				{
					maxptr[j] = std::max(maxptr[j], ptr[j]);
				}

				ptr += w;
			}
		}

		Mat sum;
		sum.create(w, channels, elemsize, opt.workspace_allocator);
		if (sum.empty())
			return Mat();
		sum.fill(0.f);
#pragma omp parallel for num_threads(opt.num_threads)
		for (int q = 0; q < channels; q++)
		{
			float* ptr = blob.channel(q);
			float* maxptr = max.row(q);
			float* sumptr = sum.row(q);

			for (int i = 0; i < h; i++)
			{
				for (int j = 0; j < w; j++)
				{
					ptr[j] = static_cast<float>(exp(ptr[j] - maxptr[j]));
					sumptr[j] += ptr[j];
				}

				ptr += w;
			}
		}

#pragma omp parallel for num_threads(opt.num_threads)
		for (int q = 0; q < channels; q++)
		{
			float* ptr = blob.channel(q);
			float* sumptr = sum.row(q);

			for (int i = 0; i < h; i++)
			{
				for (int j = 0; j < w; j++)
				{
					ptr[j] /= sumptr[j];
				}

				ptr += w;
			}
		}
	}

	if (dims == 3 && positive_axis == 2)
	{
		int w = blob.w;
		int h = blob.h;
		int channels = blob.c;

#pragma omp parallel for num_threads(opt.num_threads)
		for (int q = 0; q < channels; q++)
		{
			float* ptr = blob.channel(q);

			for (int i = 0; i < h; i++)
			{
				float max = -FLT_MAX;
				for (int j = 0; j < w; j++)
				{
					max = std::max(max, ptr[j]);
				}

				float sum = 0.f;
				for (int j = 0; j < w; j++)
				{
					ptr[j] = static_cast<float>(exp(ptr[j] - max));
					sum += ptr[j];
				}

				for (int j = 0; j < w; j++)
				{
					ptr[j] /= sum;
				}

				ptr += w;
			}
		}
	}
	return blob;
}

Mat cumsum(const Mat& blob, const Option& opt)
{
	int w = blob.w; // ����
	int h = blob.h; // ����
	int c = blob.c; // ͨ����

	Mat res;
	res.create_like(blob);
	
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < c; i++)
	{
		const float* ptr = blob.channel(i);
		float* outptr = res.channel(i);
		for (int j = 0; j < h; j++)
		{
			float* tmp = new float[w];
			std::partial_sum(ptr, ptr + w, outptr);
			ptr = ptr + w;
			outptr = outptr + w;
		}
	}
	return res;
}

Mat pad(const Mat& blob, int pad_top, int pad_bottom, int pad_left, int pad_right, float pad_value, const Option& opt)
{
	Mat res;
	int pad_row = pad_left + pad_right;
	int pad_column = pad_top + pad_bottom;
	res.create(blob.w + pad_row, blob.h + pad_column, blob.c);
	res.fill(pad_value);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < blob.c; i++) {
		const float* ptr = blob.channel(i);
		float* out = res.channel(i);
		out += pad_top * res.w;
		for (int j = 0; j < blob.h; j++) {
			memcpy(out + pad_left, ptr, blob.w * sizeof(float));
			ptr += blob.w;
			out += res.w;
		}
	}
	return res;
}

Mat Slice(const Mat& blob, int top, int bottom, int left, int right, const Option& opt)
{
	Mat res;
	res.create(right - left, bottom - top, blob.c);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < blob.c; i++) {
		const float* ptr = blob.channel(i);
		float* out = res.channel(i);
		ptr += top * blob.w;
		for (int j = 0; j < bottom-top; j++) {
			memcpy(out, ptr + left, (right - left) * sizeof(float));
			out += res.w;
			ptr += blob.w;
		}
	}
	return res;
}

Mat reducedims(const Mat& m)
{
	if (m.dims == 1) return m;
	if (m.dims == 2) return m.reshape(m.w);
	if (m.dims == 3) {
		if (m.c > 1) return m;
		else return m.reshape(m.w, m.h);
	}
	if (m.dims == 4) {
		return m.reshape(m.w, m.h, m.c);
	}
	return m;
}

Mat expanddims(const Mat& m)
{
	if (m.dims == 1) return m.reshape(m.w, 1);
	if (m.dims == 2) return m.reshape(m.w, m.h, 1);
}

void set_column_value(Mat& blob, int column, float value, const Option& opt)
{
	int w = blob.w;
	int h = blob.h;
	int c = blob.c;
	if (column == -1) column = w - 1;
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < c; i++) {
		float* ptr = blob.channel(i);
		for (int j = 0; j < h; j++) {
			ptr[column] = value;
			ptr += w;
		}
	}
}

Mat softplus(const Mat& blob, const Option& opt)
{	
	Mat res;
	res.create_like(blob);

	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < blob.c; i++) {
		const float* ptr = blob.channel(i);
		float* res_ptr = res.channel(i);
		for (int j = 0; j < blob.w * blob.h; j++) {
			res_ptr[j] = log(exp(ptr[j]) + 1);
		}
	}
	return res;
}

Mat searchsorted(Mat& bin_locations, const Mat& inputs, const Option& opt)
{
	float eps = 1e-6;
	int h = bin_locations.h;
	int w = bin_locations.w;
	int c = bin_locations.c;
	// bin_locations���һ�м�eps
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < c; i++) {
		float* ptr = bin_locations.channel(i);
		for (int j = 0; j < h; j++) {
			float v = ptr[w - 1] + eps;
			ptr[w - 1] = v;
		}
		ptr += w;
	}
	// ����ge
	Mat inputs_ge;
	inputs_ge.create_like(bin_locations);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < c; i++) {
		float* ge_ptr = inputs_ge.channel(i);
		const float* inputs_ptr = inputs.channel(i);
		float* bin_ptr = bin_locations.channel(i);
		for (int j = 0; j < h; j++){
			for (int k = 0; k < w; k++) {
				if (inputs_ptr[j] >= bin_ptr[k]) ge_ptr[k] = 1;
				else ge_ptr[k] = 0;
			}
			ge_ptr += w;
			bin_ptr += w;
		}
	}
	Mat res;
	res.create_like(inputs); // 100x1
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < c; i++) {
		float* res_ptr = res.channel(i);
		float* ge_ptr = inputs_ge.channel(i);
		for (int j = 0; j < h; j++) {
			float value = 0;
			for (int k = 0; k < w; k++) {
				value += ge_ptr[k];
			}
			res_ptr[j] = value - 1;
			ge_ptr += w;
		}
	}
	return res;
}

Mat gather(Mat& blob, Mat& index, const Option& opt)
{
	Mat res;
	res.create_like(index);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < blob.c; i++) {
		const float* ptr = blob.channel(i);
		const float* idx_ptr = index.channel(i);
		float* outptr = res.channel(i);
		for (int j = 0; j < blob.h; j++) {
			int k = idx_ptr[j];
			outptr[j] = ptr[k];
			ptr += blob.w;
		}
	}
	return res;
}

Mat matplus(const Mat& m1, const Mat& m2, const Option& opt)
{
	Mat res;
	res.create_like(m1);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p1 = m1.channel(i);
		const float* p2 = m2.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = p1[j] + p2[j];
		}
	}
	return res;
}

Mat matminus(const Mat& m1, const Mat& m2, const Option& opt)
{
	Mat res;
	res.create_like(m1);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p1 = m1.channel(i);
		const float* p2 = m2.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = p1[j] - p2[j];
		}
	}
	return res;
}

Mat matdiv(const Mat& m1, const Mat& m2, const Option& opt)
{
	Mat res;
	res.create_like(m1);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p1 = m1.channel(i);
		const float* p2 = m2.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = p1[j] / p2[j];
		}
	}
	return res;
}

Mat matproduct(const Mat& m1, const Mat& m2, const Option& opt)
{
	Mat res;
	res.create_like(m1);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p1 = m1.channel(i);
		const float* p2 = m2.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = p1[j] * p2[j];
		}
	}
	return res;
}

Mat product(const Mat& m, float value, const Option& opt)
{
	Mat res;
	res.create_like(m);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = p[j] * value;
		}
	}
	return res;
}

Mat matpow(const Mat& m, float value, const Option& opt)
{
	Mat res;
	res.create_like(m);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = pow(p[j], value);
		}
	}
	return res;
}

Mat matexp(const Mat& m, const Option& opt)
{
	Mat res;
	res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = exp(p[j]);
		}
	}
	return res;
}

Mat ceil(const Mat& m, const Option& opt)
{
	Mat res;
	res.create_like(m);
#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = ceil(p[j]);
		}
	}
	return res;
}

Mat sum(const Mat& m, const Option& opt)
{
	Mat res;
	res.create(1);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		float summed = 0;
		for (int j = 0; j < m.w * m.h; j++) {
			summed += p[j];
		}
		res[0] = summed;
	}
	return res;
}

Mat div(const Mat& m, float value, const Option& opt)
{
	Mat res;
	res.create_like(m);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = p[j] / value;
		}
	}
	return res;
}

Mat matsqrt(const Mat& m, const Option& opt)
{
	Mat res;
	res.create_like(m);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			out[j] = sqrt(p[j]);
		}
	}
	return res;
}

float matmax(const Mat& m, const Option& opt) {
	if (m.empty()) return 0;
	if (m.w * m.h * m.c == 1) return m[0];
	float _max = m[0];
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < m.c; i++) {
		const float* p = m.channel(i);
		for (int j = 1; j < m.w * m.h; j++) {
			if (p[j] >= _max) _max = p[j];
		}
	}
	return _max;
}

Mat expand(const Mat& m, int w, int h, const Option& opt)
{	
	Mat res;
	if (m.dims > 2) res.create(w, h, m.c);
	else res.create(w, h);
#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < m.c; i++) {
		const float* p = m.channel(i);
		float* out = res.channel(i);
		if (m.w == 1 && h == m.h) {
			for (int j = 0; j < m.h; j++) {
				for (int k = 0; k < w; k++) {
					out[k] = p[j];
				}
				out += res.w;
			}
		}
		if (m.h == 1 && w == m.w) {
			for (int j = 0; j < h; j++) {
				memcpy(out, p, m.w * sizeof(float));
				out += res.w;
			}
		}
	}
	return res;
}

Mat randn(int w, int h, const Option& opt, int c)
{
	Mat res;
	if (c == 0) res.create(w, h);
	else res.create(w, h, c);
	std::mt19937 generator(std::time(nullptr));
	std::normal_distribution<float> distribution(0.0, 1.0);
#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < res.c; i++) {
		float* ptr = res.channel(i);
		for (int j = 0; j < res.w * res.h; j++) {
			float rand_float = distribution(generator);
			ptr[j] = rand_float;
		}
	}
	return res;
}

Mat sequence_mask(const Mat& length, const Option& opt, float max_length_)
{
	int max_length = 0;
	if (max_length_ == 0) {
		max_length = matmax(length, opt);
	}
	else {
		max_length = max_length_;
	}
	Mat x(max_length, 1);
	float* p = x.channel(0);
#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < max_length; i++) {
		p[i] = i;
	}
	Mat res(x.w,length.w);
#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < x.c; i++) {
		float* out = res.channel(i);
		float* p1 = x.channel(i);
		const float* p2 = length.channel(i);
		for (int j = 0; j < res.h; j++) {
			for (int k = 0; k < res.w; k++) {
				if (p1[k] < p2[j]) out[k] = 1;
				else out[k] = 0;
			}
			out += res.w;
		}
	}
	return res;
}

Mat generate_path(const Mat& duration, const Mat mask, const Option& opt)
{
	Mat cum_duration = cumsum(duration, opt);
	int t_y = mask.h;
	Mat path = sequence_mask(cum_duration, opt, t_y);
	Mat padded_path = pad(path, 1, 0, 0, 0, 0, opt);
	padded_path = Slice(padded_path, 0, padded_path.h - 1, 0, padded_path.w, opt);
	path = matminus(path, padded_path, opt);
	path = matproduct(reducedims(mattranspose(path, opt)), mask, opt);
	return path;
}

Mat mattranspose(const Mat& m, const Option& opt)
{
	int w = m.w;
	int h = m.h;
	int c = m.c;
	
	Mat res;
	res.create(h, w, c);

	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < c; i++) {
		const float* ptr = m.channel(i);
		float* out = res.channel(i);
		for (int j = 0; j < w; j++) {
			for (int k = 0; k < h; k++) {
				int index = j + k * w;
				out[k] = ptr[index];
			}
			out += h;
		}
	}
	return res;
}

Mat matmul(const Mat& m1, const Mat& m2, const Option& opt)
{
	Mat res;
	res.create(m2.w, m1.h, m1.c);

	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < m1.c; i++) {
		// get row1
		const float* p1 = m1.channel(i);
		const float* p2 = m2.channel(i);
		float* p = res.channel(i);
		for (int j = 0; j < m1.h; j++) {
			// iterate m2 columns
			for (int k = 0; k < m2.w; k++) {
				// sum m1 row and m2 columns
				float sum = 0;
				for (int n = 0; n < m2.h; n++) {
					sum += p1[n] * p2[k + n * m2.w];
				}
				p[0] = sum;
				p++;
			}
			p1 += m1.w;
		}
	}
	return res;
}

void mask_fill(Mat& m, const Mat& mask, const char* condition, float condition_value, float value, const Option& opt)
{
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < m.c; i++) {
		float* ptr = m.channel(i);
		const float* m_ptr = mask.channel(i);
		for (int j = 0; j < m.w * m.h; j++) {
			if (condition == "=" && m_ptr[i] == condition_value) {
				ptr[i] = value;
				continue;
			}
			if (condition == ">" && m_ptr[i] > condition_value) {
				ptr[i] = value;
				continue;
			}
			if (condition == "<" && m_ptr[i] < condition_value) {
				ptr[i] = value;
				continue;
			}
			if (condition == ">=" && m_ptr[i] >= condition_value) {
				ptr[i] = value;
				continue;
			}
			if (condition == "<=" && m_ptr[i] <= condition_value) {
				ptr[i] = value;
				continue;
			}
		}
	}
}

Mat _get_relative_embeddings(const Mat& relative_embeddings, int length, int window_size, const Option& opt)
{
	int pad_length = std::max(length - window_size - 1, 0);
	int slice_start_position = std::max(window_size + 1 - length, 0);
	int slice_end_position = slice_start_position + 2 * length - 1;
	Mat padded_relative_embeddings;
	if (pad_length > 0) {
		padded_relative_embeddings = pad(relative_embeddings, pad_length, pad_length, 0, 0, 0, opt);
	}
	else {
		padded_relative_embeddings = relative_embeddings.clone();
	}
	// slicing
	Mat used_relative_embeddings = Slice(padded_relative_embeddings, slice_start_position, slice_end_position, 0, padded_relative_embeddings.w, opt);
	return used_relative_embeddings;
}

Mat _matmul_with_relative_keys(const Mat& x, const Mat& y, const Option& opt)
{
	// concat
	Mat y_;
	y_.create(y.w, y.h, y.c * 2);
	const float* y_ptr = y.channel(0);
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < y_.c; i++) {
		float* out_ptr = y_.channel(i);
		memcpy(out_ptr, y_ptr, y.w * y.h * sizeof(float));
	}
	Mat y_t = mattranspose(y_, opt);
	return matmul(x, y_t, opt);
}

Mat _relative_position_to_absolute_position(const Mat& x, const Option& opt)
{
	int heads = x.c;
	int length = x.h;
	// padding
	Mat x_pad = pad(x, 0, 0, 0, 1, 0, opt);
	Mat x_flat = x_pad.reshape(length * 2 * length, heads, 1); // 2x20000
	// padding
	Mat x_flat_pad = pad(x_flat, 0,0,0,length - 1, 0, opt);
	// reshape
	x_flat_pad = x_flat_pad.reshape(2 * length - 1, length + 1, heads);
	// slice
	Mat x_final = Slice(x_flat_pad, 0, length, length - 1, x_flat_pad.w, opt);
	
	return x_final;
}

Mat _absolute_position_to_relative_position(const Mat& x, const Option& opt)
{
	int heads = x.c;
	int length = x.h;
	Mat x_pad = pad(x, 0, 0, 0, length - 1, 0, opt);
	Mat x_flat = x_pad.reshape(length * length + length * (length - 1), heads, 1);
	
	Mat x_flat_pad = pad(x_flat, 0, 0, length, 0, 0, opt);
	Mat x_final = x_flat_pad.reshape(2 * length, length, heads);
	x_final = Slice(x_final, 0, x_final.h, 1, x_final.w, opt);
	return x_final;
}

Mat _matmul_with_relative_values(const Mat& x, const Mat& y, const Option& opt)
{
	// concat
	Mat y_;
	y_.create(y.w, y.h, y.c * 2);
	const float* y_p = (const float*)y;
	#pragma omp parallel for num_threads(opt.num_threads)
	for (int i = 0; i < y_.c; i++) {
		float* p = y_.channel(i);
		memcpy(p, y_p, y.w * y.h * sizeof(float));
	}
	return matmul(x, y_, opt);
}
# vits日语模型（~~莲华~~）Android部署

# 介绍

本项目基于https://github.com/jaywalnut310/vits 训练的日语模型（采用游戏语音，在3221组数据从头训练了1000个epoch）

日语cleaners基于https://github.com/CjangCjengh/MoeGoe

日语分词和声调基于https://github.com/r9y9/open_jtalk 进行移植部署

# 更新
- 将pytorch模型转换为ncnn模型，缩小模型体积和安装包体积，实现推理加速

- 优化了一些细节，添加了参数调节

- 使用ncnn Vulkan，增加gpu推理加速

- 由于Android 11添加了文件权限机制，因此本项目现仅支持Android 11以下版本API<30，敬请谅解~~（有大佬能帮忙解决最好）~~

# 使用说明

1、确保安装好ndk并配置好相关环境

2、将编译好的ncnn库放在项目的/app/src/main/cpp/目录下并重命名为ncnn，或者自己在cmake文件修改路径，ncnn下载地址https://github.com/Tencent/ncnn/releases/tag/20221128

3、将模型文件（.bin）和配置文件（.json）下载之后解压放在Download文件夹下，点击模型文件加载模型(仅需加载任意一个.bin文件即可），点击加载配置加载以.json为后缀的配置文件

4、编译并运行项目

5、如果下载apk则仅需进行3即可

# 注意
字典下载解压到项目assets的同名文件夹下，模型和配置文件下载到本地后手动导入

字典链接:https://pan.baidu.com/s/1H33WODqiRvtaWPsjWE0XVw?pwd=uqo3 

~~模型链接:https://pan.baidu.com/s/1kBlWTQSKPn2TetUfCpA8kw?pwd=584k~~

由于更换推理框架，因此目前仅支持ncnn多人模型，为了以后能够兼容声线转换，因此模型替换为基于@CjangCjengh大佬的多人模型

ncnn模型和配置文件链接：https://pan.baidu.com/s/1951Qcg4TK6I_rjSUr2MnLw?pwd=gc3t

@CjangCjengh大佬项目地址：https://github.com/CjangCjengh/MoeGoe

本项目主要由c++实现，因此确保安装ndk和配置好环境

# 鸣谢

再次感谢CjangCjengh大佬的付出，以及@nihui群主提供这么棒的框架，以及各位热心群友帮忙解决模型转换的各种问题

**模型禁止商用，后果自付！！！**

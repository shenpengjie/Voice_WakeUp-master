package com.wakeup.voice.voice_wakeup;

/**
 * Created by lutianfei5 on 2018/8/24.
 */

import android.content.res.AssetManager;
import android.os.Trace;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;


public class MyTSF {
    private static final String MODEL_FILE = /*"google2_model.h5.pb";*/"cov_model.h5(1).pb"; //模型存放路径

    //数据的维度
    private int LENGTH = 0;
    private int INPUT_X = 39;
    private int INPUT_Y = 41;

    //模型中输入变量的名称
    private static final String inputName = "conv2d_1_input";
    //用于存储的模型输入数据
    private float[] inputs = null;

    //模型中输出变量的名称
    private static final String outputName = "output_node0";
    //用于存储模型的输出数据
    private float[] outputs;

    private TensorFlowInferenceInterface inferenceInterface;

    static {
        //加载库文件
        System.loadLibrary("tensorflow_inference");
    }

    MyTSF(AssetManager assetManager) {
        //接口定义
        inferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_FILE);
    }

    private float[] change(double[][] x) {
        float[][] _x = new float[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                _x[i][j] = (float) x[i][j];
            }
        }
        return change(_x);
    }

    private float[] change(float x[][]) {
        float[] result = new float[x.length * x[0].length];
        int i = 0;
        for (float[] aX : x) {
            for (int n = 0; n < x[0].length; n++) {
                result[i++] = aX[n];
            }
        }
        return result;
    }

    public void input(float x[][]) {
        LENGTH = x.length;
        INPUT_X = 39;
        INPUT_Y = 41;
        inputs = change(x);
        outputs = new float[LENGTH * 5];
    }

    public void input(double x[][], int categral) {
        LENGTH = x.length;
        INPUT_X = 39;
        INPUT_Y = 41;
        inputs = change(x);
        outputs = new float[LENGTH * categral];
    }

    public void input(float[] x) {
        inputs = x;
        LENGTH = 1;
        INPUT_X = 39;
        INPUT_Y = 41;
    }

    public float[] getAddResult() {

        //将数据feed给tensorflow
        //Trace.beginSection("feed");
        inferenceInterface.feed(inputName, inputs, LENGTH, INPUT_X, INPUT_Y, 1);
        //Trace.endSection();

        //运行乘2的操作
        //Trace.beginSection("run");
        String[] outputNames = new String[]{outputName};
        inferenceInterface.run(outputNames);
        //Trace.endSection();

        //将输出存放到outputs中
        //Trace.beginSection("fetch");
        inferenceInterface.fetch(outputName, outputs);
        //Trace.endSection();

        return outputs;
    }


}
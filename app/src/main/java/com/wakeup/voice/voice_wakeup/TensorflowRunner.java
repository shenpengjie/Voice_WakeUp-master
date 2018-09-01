package com.wakeup.voice.voice_wakeup;

import android.content.Context;
import android.os.Looper;
import android.os.Handler;
import android.text.InputFilter;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.util.concurrent.locks.ReentrantLock;


public class TensorflowRunner {
    private static final String MODEL_FILE = "cov_model.h5.pb";
    private Context mContext;
    private TensorFlowInferenceInterface inferenceInterface;
    private Handler handler = new Handler(Looper.getMainLooper());
    private TensorflowRunnerListener tensorflowRunnerListener;
    private int LENGTH = 0;
    private final int INPUT_X = 39;
    private final int INPUT_Y = 41;
    private final int categoral = 5;
    private static final String inputName = "conv2d_1_input";
    private static final String outputName = "output_node0";
    private String[] outputNames = new String[]{outputName};
    private final ReentrantLock runnerLock = new ReentrantLock();

    public TensorflowRunner(Context context, TensorflowRunnerListener listener) {
        mContext = context;
        tensorflowRunnerListener = listener;
        inferenceInterface = new TensorFlowInferenceInterface(mContext.getAssets(), MODEL_FILE);
    }

    private float[] change(float[][] input) {
        float[] result = new float[input.length * input[0].length];
        int i = 0;
        for (float[] aX : input) {
            for (int n = 0; n < input[0].length; n++) {
                result[i++] = aX[n];
            }
        }
        return result;
    }

    public void add(final double[][] input) {
        float[][] res = new float[input.length][input[0].length];
        for (int i = 0; i < input.length; i++) {
            for (int j = 0; j < input[0].length; j++) {
                res[i][j] = (float) input[i][j];
            }
        }
        add(change(res), input.length);
    }

    public void add(final float[] inputs, final int len) {
        //new Thread(new Runnable() {
//            @Override
//            public void run() {
//                runnerLock.lock();
        LENGTH = len;
        final float[] outputs = new float[LENGTH * categoral];
        inferenceInterface.feed(inputName, inputs, LENGTH, INPUT_X, INPUT_Y, 1);
        inferenceInterface.run(outputNames);
        inferenceInterface.fetch(outputName, outputs);
        handler.post(new Runnable() {
            @Override
            public void run() {
                tensorflowRunnerListener.callback(outputs, len);
            }
        });
//        runnerLock.unlock();
//            }
        //}).start();
    }

    public static interface TensorflowRunnerListener {
        void callback(float[] data, int len);
    }
}

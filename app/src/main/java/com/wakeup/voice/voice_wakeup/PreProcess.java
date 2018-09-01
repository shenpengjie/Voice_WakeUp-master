package com.wakeup.voice.voice_wakeup;

import android.content.Context;

import Jama.Matrix;

import java.io.*;
import java.util.Objects;

import static java.lang.Math.pow;

public class PreProcess {
    private Context context;
    //    private static double CHANGE = Math.pow(2, 15);
    private double absMax;
    private double[] wavData;
    private double[][] pspec;
    private boolean appEnergy = true;
    private int ndcts = 13;
    private int nfilters = 26;
    private int ceplifter = 22;
    private double[][] coeff = new double[ndcts][nfilters];
    private double[] lifterWindow = new double[nfilters];
    private int numcep = 13;
    private MFCC mfcc;

    /**
     * 读取wav文件，读取梅尔滤波器组
     * 梅尔滤波器组文件格式为：
     * 第一行是Mel-filterbank的行数row
     * 第二行是Mel-filterbank的列数line
     * 接下来row*line行，每行一个数据
     *
     * @param data
     * @param melBankPath
     */
    public PreProcess(Context context, double[] data, String melBankPath) {
        this.context = context;
        wavData = data;
        double[][] melBank = readMelBank(melBankPath);
        mfcc = new MFCC();
        mfcc.setMelBank(melBank);
        DCT_2();//求DCT-2系数
        LiftWindow();//倒谱提升窗系数
    }

    public void setWavData(Context context, String path) {
        WaveFileReader wav = new WaveFileReader(context, path);
        int[] data = wav.getData()[0];
        wavData = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            wavData[i] = data[i] / 32768.;
        }
    }

    public void setWavData(double[] data) {
        this.wavData = data;
    }

    public double[][] getLogFBank() {
        double[][] fBank = getFBank();
        for (int i = 0; i < fBank.length; i++) {
            for (int j = 0; j < fBank[0].length; j++) {
                fBank[i][j] = Math.log(fBank[i][j]);
            }
        }
        return fBank;
    }

    public double[][] getFBank() {
        int dwSoundLen = wavData.length;// 采样点个数
//        common(wavData,dwSoundLen);

        SignalProcess signalProcess = new SignalProcess();
//        signalProcess.WaveEndtest(wavData,dwSoundLen);
        double fpData[] = signalProcess.audPreEmphasize(wavData, dwSoundLen);//预加重
        double[][] frames = signalProcess.aunEnframe(fpData, dwSoundLen);//分帧
        //hamming

        FFT fft = new FFT(512);
        double[][] magnitude = fft.calculateFFTMagnitude(frames);//计算幅度谱magnitude spectrum
        pspec = fft.calculateFFTPowerFromMag(magnitude);//计算功率谱power spectrum

        double[][] feat = mfcc.fbEnergies(pspec);

        //确保矩阵中无0
        for (int i = 0; i < feat.length; i++) {
            for (int j = 0; j < feat[0].length; j++) {
                if (feat[i][j] == 0) {
                    feat[i][j] = Double.MIN_VALUE;//取得double最小可表示的非零数
                } else
                    feat[i][j] = feat[i][j];
            }
        }

        return feat;
    }

    public double[][] getMFCC() {
        double[][] logFbank = getLogFBank();

        double[][] out = new double[logFbank.length][nfilters];
        double[][] m_out = new double[logFbank.length][numcep];

        for (int i = 0; i < logFbank.length; i++) {
            for (int k = 0; k < ndcts; k++) {
                for (int n = 0; n < nfilters; n++) {
                    out[i][k] += coeff[k][n] * logFbank[i][n];
//                    out[i][k] += logFbank[i][n] * Math.cos(Math.PI * k * (2 * n + 1) / (2 * nfilters));
                }
                double f;//scaling factor
                if (k == 0)
                    f = Math.sqrt(1. / (nfilters));
                else
                    f = Math.sqrt(2. / (nfilters));
                out[i][k] *= f;
            }
        }

        //取前13个参数
        for (int i = 0; i < logFbank.length; i++) {
            for (int j = 0; j < numcep; j++) {
                m_out[i][j] = out[i][j];
            }
        }

        for (int i = 0; i < logFbank.length; i++) {
            for (int j = 0; j < numcep; j++) {
                m_out[i][j] *= lifterWindow[j];
            }
        }

        if (appEnergy) {
            AppendEnergy(m_out);
        }

        return m_out;
    }

    /**
     * 标准DCT-2变换
     * N-1
     * y[k] = 2* sum x[n]*cos(pi*k*(2n+1)/(2*N)), 0 <= k < N.
     * n=0
     */
    public void DCT_2() {
        for (int i = 0; i < ndcts; ++i) {
            for (int j = 0; j < nfilters; ++j) {
                coeff[i][j] = Math.cos((2 * j + 1) * i * Math.PI / (2 * nfilters));
            }
        }
    }

    /**
     * 倒谱提升窗归一化
     */
    private void LiftWindow() {
        double max_value = 0.0f;
        for (int i = 0; i < nfilters; i++) {
            lifterWindow[i] = 1 + 0.5 * ceplifter * Math.sin(Math.PI * i / ceplifter);
            if (lifterWindow[i] > max_value) {
                max_value = lifterWindow[i];
            }
        }
    }

    private double[] getEnergy() {
        double[] energy = new double[pspec.length];
        for (int i = 0; i < pspec.length; i++) {
            for (int j = 0; j < pspec[0].length; j++) {
                energy[i] += pspec[i][j];
            }
        }
        return energy;
    }

    private void AppendEnergy(double[][] feat) {
        double[] energy = getEnergy();
        for (int i = 0; i < feat.length; i++) {
            feat[i][0] = Math.log(energy[i]);
        }
    }

    private double[] arrange(int a, int b) {
        if (a == b)
            return null;
        if (a > b) {
            int temp = a;
            a = b;
            b = temp;
        }
        int len = b - a;
        double[] d = new double[len];
        for (int i = 0; i < len; i++) {
            d[i] = (double) (a + i);
        }
        return d;
    }


    /**
     * 计算差分
     *
     * @param feat
     * @param N    阶数
     * @return
     */
    public double[][] getDelta(double[][] feat, int N) {
        if (N < 1)
            return null;
        int numFrames = feat.length;
        int sum = 0;
        for (int i = 1; i < N + 1; i++) {
            sum += i * i;
        }
        int denominator = 2 * sum;
        //pad 将feat第一维扩充成前+N，后+N 即N+feat.leng+N维；第二维不扩充； 同numpy的 numpy.pad(feat, ((N, N), (0, 0)), mode='edge')
        double[][] padded1 = new double[N + N + feat.length][feat[0].length];
        for (int i = 0; i < N + N + feat.length; i++) {
            if (i < N)
                System.arraycopy(feat[0], 0, padded1[i], 0, feat[0].length);
            else if (i < N + feat.length)
                System.arraycopy(feat[i - N], 0, padded1[i], 0, feat[0].length);
            else
                System.arraycopy(feat[feat.length - 1], 0, padded1[i], 0, feat[0].length);
        }
        Matrix padded = new Matrix(padded1);
        double[][] deltaFeat = new double[feat.length][feat[0].length];
        for (int i = 0; i < numFrames; i++) {
            Matrix A = new Matrix(Objects.requireNonNull(arrange(-N, N + 1)), 1);
            Matrix C = padded.getMatrix(i, i + 2 * N, 0, padded.getColumnDimension() - 1);
            Matrix B = A.times(padded.getMatrix(i, i + 2 * N, 0, padded.getColumnDimension() - 1));
            B = B.times((double) 1 / denominator);
            double[][] tempB = B.getArray();
            System.arraycopy(tempB[0], 0, deltaFeat[i], 0, deltaFeat[0].length);
        }
        return deltaFeat;

    }

    /**
     * 拼接三个数组 三个数据的shape要一致
     *
     * @param data1
     * @param data2
     * @param data3
     * @return
     */
    public double[][][] stack(double[][] data1, double[][] data2, double[][] data3) {
        Matrix matrix1 = new Matrix(data1);
        Matrix matrix2 = new Matrix(data2);
        Matrix matrix3 = new Matrix(data3);
        data1 = matrix1.transpose().getArray();
        data2 = matrix2.transpose().getArray();
        data3 = matrix3.transpose().getArray();
        double[][][] res = new double[data1.length][data1[0].length][3];

        for (int i = 0; i < data1.length; i++) {
            for (int j = 0; j < data1[0].length; j++) {
                for (int k = 0; k < 3; k++) {//3组数据
                    if (k == 0)
                        res[i][j][k] = data1[i][j];
                    if (k == 1)
                        res[i][j][k] = data2[i][j];
                    if (k == 2)
                        res[i][j][k] = data3[i][j];
                }
            }
        }
        return res;
    }

    private void common(double[] data, int dwSoundLen) {
        for (int i = 0; i < dwSoundLen; i++) {
            data[i] = data[i] / absMax;
        }
    }

    public double[][] readMelBank(String path) {
        double[][] bank;
        try {
            InputStream fis = context.getResources().getAssets().open(path);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            double[] temp;
            String s;

            s = br.readLine();
            int shape1 = Integer.parseInt(s);
            s = br.readLine();
            int shape2 = Integer.parseInt(s);

            bank = new double[shape1][shape2];
            temp = new double[shape1 * shape2];
            int i = 0;
            while ((s = br.readLine()) != null) {
                double d = Double.parseDouble(s);
                temp[i] = d;
                i++;
            }
            if (i != shape1 * shape2)
                System.err.println("melBank:shape1*shape2 != data size");
            for (i = 0; i < shape1; i++) {
                System.arraycopy(temp, shape2 * i, bank[i], 0, shape2);
            }
            return bank;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public double[] getWavData() {
        return wavData;
    }

    public double[][] normalize(double x[][]) {
        for (int i = 0; i < x.length; i++) {
            float sum = 0;
            for (int j = 0; j < x[0].length; j++)
                sum += x[i][j] * x[i][j];
            sum = (float) pow(sum, 0.5);
            for (int k = 0; k < x[0].length; k++)
                x[i][k] = x[i][k] / sum;
        }
        return x;
    }

    public double[][] get_martix(double[][] source, int npast, int nfutrue) {
        double[][] _source = source;
        double[][] past;
        double[][] future;
        for (int i = 0; i < npast; i++) {
            past = get_past(source, i + 1);
            _source = concatenate(past, _source);
        }
        for (int i = 0; i < nfutrue; i++) {
            future = get_future(source, i + 1);
            _source = concatenate(_source, future);
        }
        return _source;
    }

    public double[][] get_past(double[][] source, int delta) {
        double[][] result = new double[source.length][source[0].length];
        int k = 0;
        for (int i = delta; i < source.length; i++) {
            for (int j = 0; j < source[0].length; j++)
                result[i][j] = source[k][j];
            k++;
        }
        return result;
    }

    public double[][] get_future(double[][] source, int delta) {
        double result[][] = new double[source.length][source[0].length];
        int k = delta;
        for (int i = 0; i < source.length - delta; i++) {
            for (int j = 0; j < source[0].length; j++)
                result[i][j] = source[k][j];
            k++;
        }
        return result;
    }

    public double[][] concatenate(double[][] pas,double[][] cur,double[][] fut){
        return concatenate(concatenate(pas,cur),fut);
    }

    public double[][] concatenate(double first[][], double second[][]) {
        int sec_length = second.length;
        ;
        int second_length = second[0].length;

        double[][] result = new double[first.length][first[0].length + second_length];
        for (int i = 0; i < first.length; i++)
            for (int j = 0; j < first[0].length; j++) {
                result[i][j] = first[i][j];
            }

        for (int i = 0; i < sec_length; i++) {
            int k = 0;
            for (int j = first[0].length; j < second_length + first[0].length; j++, k++) {
                result[i][j] = second[i][k];
            }
        }
        return result;
    }

}

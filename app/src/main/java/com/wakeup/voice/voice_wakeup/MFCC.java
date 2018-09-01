package com.wakeup.voice.voice_wakeup;

import Jama.Matrix;

public class MFCC {
    public static int LEN_PF_MFCC = 256;
    public static int SIZE_X_X_MFCC = 32;
    public static int SIZE_X_Y_MFCC = 140;
    public static int FS_MFCC = 8000;//HZ频率
    public static int N_MFCC = 256;//FFT的长度
    public static int SIZE_DCT_X_MFCC = 13;//dct系数二维数组x大小
    public static int SIZE_DCT_Y_MFCC = 25;//dct系数二维数组y大小
    public static double FH_MFCC = 0.5;//high frequency
    public static double FL_MFCC = 0;//low frequency
    public double buf[][];
    public double melBank[][];//mel滤波器组系数
    public int Pos;
    public static int P_MFCC = 26;//mel滤波器组个数 nfilt
    public static double pi = 3.1415926536;// 定义pi
    public double hamming[];//汉明窗
    public double working[];
    public double workingi[];
    public double dctcoef[][];//dct系数
    public double stren_win[];//归一化倒谱提升窗口
    public double result[];
    public int index1;
    public float m[][];

    public MFCC() {
    }

    public void setMelBank(double[][] melBank) {
        this.melBank = melBank;
    }

    public double[][] fbEnergies(double[][] pspec){
        Matrix A = new Matrix(pspec);
        Matrix B = new Matrix(melBank);
        B = B.transpose();

        Matrix C = A.times(B);

        return C.getArray();
    }

    /***************************
     *mel滤波器组系数
     *函数名: melbank(void)
     *功能：求mel滤波器组系数，并且对其进行归一化,主要参数P_MFCC，LEN_PF_MFCC，FS_MFCC，FH_MFCC，FL_MFCC
     *
     *************************/
    public void melbank() {
        double f0, fn2, lr;
        int b1, b2, b3, b4, k2, k3, k4, mn, mx;
        double bl[] = new double[5];
        double pf[] = new double[LEN_PF_MFCC];
        double fp[] = new double[LEN_PF_MFCC];
        double pm[] = new double[LEN_PF_MFCC];
        double v[] = new double[LEN_PF_MFCC];
        int r[] = new int[LEN_PF_MFCC];
        int c[] = new int[LEN_PF_MFCC];
        int i, j;
        melBank = new double[SIZE_X_X_MFCC][SIZE_X_Y_MFCC];
        f0 = (double) 700 / (double) FS_MFCC;
        fn2 = Math.floor((double) N_MFCC / 2);
        lr = Math.log((f0 + FH_MFCC) / (f0 + FL_MFCC)) / (P_MFCC + 1.0);
        bl[1] = N_MFCC * ((f0 + FL_MFCC) * Math.exp(0 * lr) - f0);
        bl[2] = N_MFCC * ((f0 + FL_MFCC) * Math.exp(1 * lr) - f0);
        bl[3] = N_MFCC * ((f0 + FL_MFCC) * Math.exp(P_MFCC * lr) - f0);
        bl[4] = N_MFCC * ((f0 + FL_MFCC) * Math.exp((P_MFCC + 1) * lr) - f0);
        b2 = (int) Math.ceil(bl[2]);
        b3 = (int) Math.floor(bl[3]);
        b1 = (int) Math.floor(bl[1]) + 1;
        b4 = (int) Math.min(fn2, Math.ceil(bl[4])) - 1;
        k2 = b2 - b1 + 1;
        k3 = b3 - b1 + 1;
        k4 = b4 - b1 + 1;
        mn = b1 + 1;
        mx = b4 + 1;
        for (i = 1, j = b1; j <= b4; i++, j++) {
            pf[i] = Math.log(((double) f0 + (double) i / (double) N_MFCC) / (f0 + FL_MFCC)) / lr;
            fp[i] = Math.floor(pf[i]);
            pm[i] = pf[i] - fp[i];
        }
        for (i = 1, j = k2; j <= k4; i++, j++) {
            r[i] = (int) fp[j];
            c[i] = j;
            v[i] = 2 * (1 - pm[j]);
        }
        for (j = 1; j <= k3; j++, i++) {
            r[i] = 1 + (int) fp[j];
            c[i] = j;
            v[i] = 2 * pm[j];
        }
        for (j = 1; j < i; j++) {
            v[j] = 1 - 0.92 / 1.08 * Math.cos(v[j] * pi / 2);
            melBank[r[j]][c[j] + mn - 1] = v[j];
        }

        //melBank=melBank/max(melBank(:));
        double buf = 0;
        for (i = 1; i <= 24; i++)
            for (j = 1; j <= 129; j++) {
                if (melBank[i][j] > buf) buf = melBank[i][j];
            }

        for (i = 1; i <= 24; i++) {
            for (j = 1; j <= 129; j++) {
                melBank[i][j] = melBank[i][j] / buf;
            }
        }
        System.out.println("s");

    }

    /***************************
     *离散余弦变换DCT,ok
     *函数名: dct(void)
     *功能：求DCT系数
     *************************/
    public void dct() {
        dctcoef = new double[SIZE_DCT_X_MFCC][SIZE_DCT_Y_MFCC];
        for (int k = 1; k <= 12; k++) {
            for (int n = 0; n <= 23; n++) {
                dctcoef[k][n + 1] = Math.cos((double) (2 * n + 1) * k * pi / (double) (2 * 24));
            }
        }


    }


    /***************************
     *归一化倒谱提升窗口,ok
     *函数名: cal_stren_win(void)
     *功能：
     *************************/
    public void cal_stren_win() {
        stren_win = new double[13];
        double b = 0.0;
        for (int i = 1; i <= 12; i++) {
            stren_win[i] = 1 + 6 * Math.sin(pi * (double) i / (double) 12);
            if (b < stren_win[i]) {
                b = stren_win[i];
            }
        }
        for (int i = 1; i <= 12; i++) //归一化
        {
            stren_win[i] = stren_win[i] / b;
        }


    }

    /***************************
     *汉明窗
     *函数名: calcu_hamming(void)
     *功能：
     *************************/
    public void calcu_hamming() {
        hamming = new double[257];
        for (int i = 1; i <= 256; i++) {
            hamming[i] = 0.54 - 0.46 * Math.cos(2 * pi * (i - 1) / (256 - 1));
        }
    }

}

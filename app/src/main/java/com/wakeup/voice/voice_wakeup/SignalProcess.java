package com.wakeup.voice.voice_wakeup;

public class SignalProcess {
    //    public static double WIN_LEN = 0.025;//win_len * sample rate = 帧长
//    public static double WIN_STEP = 0.01;//win_step * saple rate = 帧移
//    public static double SAMPLE_RATE = 16000;//默认为16000
    private static double PI1 = 3.1415926536;// 定义pi
    private static int FRM_LEN = 400;// 定义帧长
    private static int FRM_SFT = 160;// 定义帧移
    private static double PRE_EMPHASIS = 0.97;//定义预加重系数
    private static int bits = 16;//处理的wav位数为2字节
    private int FrmNum;// 总共多少帧
    private AudFrame audFrame[];// 帧数组
    private float fltHamm[];// Hamming窗系数
    private double fltSteThresh[];     //双门限短时能量阈值[0]高[1]低
    private double dwZcrThresh[];  //双门限过零率阈值[0]高[1]低
    private static int MAX_SLIENCE_LEN = 8;//最大静音长度
    private static int MIN_WORD_LEN = 15;//最小语音长度
    private int WavStart;//语音起始点
    private int WavEnd;//语音结束点
//    private int dwSoundLen;

    public void WaveEndtest(double[] wavData, int dwSoundLen) {
//        this.dwSoundLen = dwSoundLen;
        double[] dpData = audPreEmphasize(wavData, dwSoundLen);
        aunEnframe(dpData, dwSoundLen);//分帧
        Hamming();//求hamming系数
        AudHamming();//加窗
        AudSte();//求解短时能量
        AudZcr();//求解过零率
        AudNoiseEstimate();    //估计噪声阈值
        AudVadEstimate();//端点检测
        System.out.println(audFrame.length);
        System.out.println(WavStart);
        System.out.println(WavEnd);
    }

    public void test2(double[] wavData, int dwSoundLen) {
        double[] dpData = audPreEmphasize(wavData, dwSoundLen);
        double[][] frames = aunEnframe(dpData, dwSoundLen);//分帧
        int perF = 150;
        int numF = dwSoundLen/perF;
        double[][] fdata = new double[numF][perF];
        for (int i=0;i<numF;i++){
            System.arraycopy(dpData, i * perF, fdata[i], 0, perF);
        }
        for (int i = 0; i < numF; i++) {
            double db = dbFS(fdata[i]);
            System.out.println("frames[" + i + "] db*10 = " + db*10);
        }
    }

    /***********************************
     * 适配
     * 预加重 函数名：audPreEmphasize() 功能：对所有采样点进行预处理
     *  % 预加重滤波器
     * xx=double(x);
     * xx=filter([1 -0.9375],1,xx);
     ************************************/
    public double[] audPreEmphasize(double[] data, int dwSoundLen) {
        double fpData[];// 预加重后的采样点
        fpData = new double[dwSoundLen];
        fpData[0] = data[0];
        for (int i = 1; i < dwSoundLen; i++) {
            fpData[i] = (double) (data[i]) - (double) (data[i - 1]) * PRE_EMPHASIS;
        }
        return fpData;
    }

    /***********************************
     * 近似适配（只差最后一帧）
     * 最后的采样点不够一帧时 会丢弃这一帧
     * 分帧 函数名：AudEnframe() 功能：给每一帧的fltFrame[FRM_LEN]赋采样点的值，个数是帧长
     * fpData是预加重后的数据
     ************************************/
    public double[][] aunEnframe(double data[], int dwSoundLen) {

        double frames[][];//帧数组 一行一帧
        //除法会抹去小数部分 只舍不入 故最后一帧可能会被丢弃
        FrmNum = (dwSoundLen - (FRM_LEN - FRM_SFT)) / FRM_SFT;
        frames = new double[FrmNum][FRM_LEN];
        audFrame = new AudFrame[FrmNum];
        for (int i = 0; i < FrmNum; i++) {
            audFrame[i] = new AudFrame();
        }

        int x = 0;// 每一帧的起始点
        for (int i = 0; i < FrmNum; i++) {
            audFrame[i].fltFrame = new double[FRM_LEN];
            for (int j = 0; j < FRM_LEN; j++) {
                audFrame[i].fltFrame[j] = data[x + j];
                frames[i][j] = data[x + j];
            }
            x += FRM_SFT;
        }
        return frames;
    }

    /**
     * 获取一段语音的分贝
     * using amplitude not power
     *
     * @param data 语音数据
     * @return
     */
    private double dbFS(double[] data) {
        double[] bigger = data;
        for (int i = 0; i < data.length; i++) {
            bigger[i] *= 1000;
        }
        double mse = MSE(data);
        if (mse == 0)
            System.err.println("dbFS mse error");

        double max_possible_val = Math.pow(2, 16) / 2;
//        System.out.println("max_possible_val = " + max_possible_val);
        return 20 * Math.log(10) / Math.log(mse/max_possible_val);
    }

    /**
     * 计算均方差
     */
    private double MSE(double[] data) {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i] * data[i];
        }
        return Math.sqrt(sum / data.length);
    }

    /***********************************
     * 汉明窗系数 函数名：Hamming()
     * 功能：求汉明窗系数，输入的是每一帧的帧长，要用到PI。这个数组是固定值，只有帧长决定
     ************************************/
    public void Hamming() {
        fltHamm = new float[FRM_LEN];
        for (int i = 0; i < FRM_LEN; i++) {
            // 汉明窗函数为W(n,a) = (1-a) -αcos(2*PI*n/(N-1))
            // 0≦n≦N-1,a一般取0.46
            // 此处取0.46
            // 使音频波段平滑sin（）
            fltHamm[i] = fltHamm[i] = (float) (0.54 - 0.46 * Math.cos((2 * i * PI1) / (FRM_LEN - 1)));
        }
    }

    /***********************************
     * 加窗 函数名：AudHamming()
     * 功能：输入的是每一帧的帧长，需要利用到求得的汉明窗系数，具体是每个采样点的值乘以汉明窗系数，再把结果赋予fltFrame[]
     ************************************/
    public void AudHamming() {
        for (int i = 0; i < FrmNum; i++) {
            // 加窗
            for (int j = 0; i < FRM_LEN; i++) {
                // 保存语音信号中各帧对应的汉明窗系数
                audFrame[i].fltFrame[j] *= fltHamm[j];
            }
        }
    }

    /***********************************
     * 每一帧短时能量 函数名：AudSte()
     * 功能：求每一帧的短时能量，即将所有这一帧的所有样点值相加，fpFrmSnd是帧第一个样
     ************************************/

    public void AudSte() {
        for (int i = 0; i < FrmNum; i++) {
            double fltShortEnergy = 0;
            for (int j = 0; j < FRM_LEN; j++) {
                fltShortEnergy += Math.abs(audFrame[i].fltFrame[j]);
            }
            audFrame[i].fltSte = fltShortEnergy;
        }


    }

    /**
     * 一帧的过零率
     * 函数名：AudZcr(fltSound *fpFrmSnd, DWORD FrmLen,fltSound ZcrThresh)
     * 功能：求解一帧的过零率，fpFrmSnd帧第一个采样点地址，FrmLen帧长，ZcrThresh过零率阀值
     * fltZcrVadThresh 过零率 阀值 0.02
     */
    public void AudZcr() {
        double fltZcrVadThresh = 0.02;
        for (int i = 0; i < FrmNum; i++) {
            int dwZcrRate = 0;
            for (int j = 0; j < FRM_LEN - 1; j++)//智明师兄后面有带绝对值，j-1
            {
                if ((audFrame[i].fltFrame[j] * audFrame[i].fltFrame[j + 1] < 0) && ((audFrame[i].fltFrame[j] - audFrame[i].fltFrame[j + 1]) > fltZcrVadThresh))
                    dwZcrRate++;
            }
            audFrame[i].dwZcr = dwZcrRate;
        }


    }


    /**********************************
     *估计噪声阀值
     *函数名： AudNoiseEstimate（）
     *功能：计算双门限阀值
     ***********************************/

    public void AudNoiseEstimate() {
        fltSteThresh = new double[2];//双门限短时能量阈值[0]高[1]低
        dwZcrThresh = new double[2];//双门限过零率阈值[0]高[1]低
//		int ZcrThresh = 0;	//过零率阈值
//		double StrThresh =0.0;	//短时能量阈值
//		int NoiseFrmLen = 0;
//		for(int i = 0; i < FrmNum; i++)
//		{
//			ZcrThresh += audFrame[i].dwZcr;
//			StrThresh += audFrame[i].fltSte;
//			NoiseFrmLen++;
//		}
//		dwZcrThresh[0] = (double)(ZcrThresh) / NoiseFrmLen;
//		dwZcrThresh[1] = (double)(ZcrThresh) / NoiseFrmLen*2.5;
//		fltSteThresh[0] = (double)StrThresh / NoiseFrmLen*0.7;
//		fltSteThresh[1] = (double)(StrThresh / NoiseFrmLen)*0.5;//*0.95;
        dwZcrThresh[0] = 10;
        dwZcrThresh[1] = 5;
        fltSteThresh[0] = 10;
        fltSteThresh[1] = 2;
        double maxSte = 0;
        for (int i = 0; i < FrmNum; i++) {
            if (maxSte < audFrame[i].fltSte)
                maxSte = audFrame[i].fltSte;
        }

        fltSteThresh[0] = fltSteThresh[0] < (maxSte / 4) ? fltSteThresh[0] : (maxSte / 4);
        fltSteThresh[1] = fltSteThresh[1] < (maxSte / 8) ? fltSteThresh[1] : (maxSte / 8);


    }


    /***************************
     *端点检测
     *函数名: AudVadEstimate(void)
     *功能：端点检测，需要用到估计阀值的函数，最后得出有效起始点和有效截止点
     *************************/

    public void AudVadEstimate() {
        //Extract Threshold
        double ZcrLow = dwZcrThresh[1];
        double ZcrHigh = dwZcrThresh[0];
        double AmpLow = fltSteThresh[1];
        double AmpHigh = fltSteThresh[0];
        WavStart = 0;
        WavEnd = 0;
        int status = 0;
        int count = 0;
        int silence = 0;

        for (int i = 0; i < FrmNum; i++) {
            switch (status) {
                case 0:
                case 1:
                    if ((audFrame[i].fltSte) > AmpHigh) {
                        WavStart = (i - count - 1) > 1 ? (i - count - 1) : 1;
                        status = 2;
                        silence = 0;
                        count = count + 1;
                    } else if ((audFrame[i].fltSte) > AmpLow || (audFrame[i].dwZcr) > ZcrLow) {
                        status = 1;
                        count = count + 1;
                    } else {
                        status = 0;
                        count = 0;
                    }
                    break;

                case 2: //Speech Section

                    if ((audFrame[i].fltSte > AmpLow) || (audFrame[i].dwZcr > ZcrLow)) {
                        count = count + 1;
                        //WavEnd=i-Silence;
                    } else {
                        silence = silence + 1;
                        if (silence < MAX_SLIENCE_LEN) {
                            count = count + 1;
                        } else if (count < MIN_WORD_LEN) {
                            status = 0;
                            silence = 0;
                            count = 0;
                        } else {
                            status = 3;
                        }
                    }
                    break;
                default:
                    break;
            }
            //更新语音帧
        }
        count = count - silence / 2;
        WavEnd = WavStart + count - 1;
    }
}

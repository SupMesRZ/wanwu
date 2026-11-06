本文介绍如何使用阿里云智能语音服务提供的Java SDK，包括SDK的安装方法及SDK代码示例。

## **前提条件**

-   获取鉴权需要的Appkey以及Token。具体操作，请参见[管理项目](https://help.aliyun.com/zh/isi/getting-started/manage-projects)和[通过SDK获取Token](https://help.aliyun.com/zh/isi/getting-started/obtain-an-access-token)。

-   如果通过SDK方式接入，需要下载安装SDK。具体操作，请参见[快速开始](https://help.aliyun.com/zh/isi/developer-reference/stream-input-tts-sdk-quick-start)。


## **下载安装**

从Maven服务器下载最新版本的SDK。

```
<dependency>
    <groupId>com.alibaba.nls</groupId>
    <artifactId>nls-sdk-tts</artifactId>
    <version>2.2.19</version>
</dependency>
<dependency>
    <groupId>com.alibaba.nls</groupId>
    <artifactId>nls-sdk-common</artifactId>
    <version>2.2.19</version>
</dependency>
```

**重要**

Java SDK 从 **2.1.7** 版本开始（含2.1.7），语音合成SDK（包括实时长文本语音合成）**SpeechSynthesizer**的**waitForComplete**接口的超时时间单位从**秒**变更为**毫秒**。

## **SDK调用注意事项**

-   `NlsClient`使用Netty框架，NlsClient对象的创建会消耗一定时间和资源，一经创建可以重复使用。建议调用程序将`NlsClient`的创建和关闭与程序本身的生命周期相结合。

-   `StreamInputTts`对象不可重复使用，一个语音合成任务对应一个`StreamInputTts`对象。例如，N个文本要进行N次语音合成任务，创建N个`StreamInputTts`对象。

-   `StreamInputTtsListener`对象和`StreamInputTts`对象是一一对应的，不能将一个`StreamInputTtsListener`对象设置到多个`StreamInputTts`对象中，否则不能将各语音合成任务区分开。

-   Java SDK依赖Netty网络库，如果您的应用依赖Netty，其版本需更新至4.1.17.Final及以上。


## **关键接口**

`StreamInputTts`是流式文本语音合成的主类，提供了下面的关键接口：

-   **startStreamInputTts**：与服务端进行websocket建连操作，并完成回调、参数设置等操作

    ```
    /**
     * 开始语音转写：发送语音转写请求，同步接收服务端确认
     * @throws Exception
     */
    public void startSteamInputTTS()
    ```

-   **sendStreamInputTts**：以流式的方式发送文本

    ```
    /**
     * 以流式的方式发送文本
     * @throws Exception
     * @param text：从大模型当中生成的流式文本
     */
    public void sendStreamInputTts(String text);
    ```

-   **stopStreamInputTts**：以阻塞的方式停止流式文本语音合成，并与服务端断开websocket连接

    ```
    /**
     * 结束合成任务，通知服务端流入文本数据发送完毕，阻塞等待服务端处理完成，并返回所有合成音频。阻塞超时可以通过start接口中的complete_waiting_ms设置
     */
    public void stopStreamInputTts()
    ```

-   **addCustomedParam**：设置自定义请求参数，用于设置语音服务的高级属性或新功能

    ```
    public void addCustomedParam(String key, Object value)
    ```

    需要使用该方法设置的参数有：

    -   enable\_aigc\_tag

    -   aigc\_propagator

    -   aigc\_propagate\_id


`StreamInputTtsListener`是回调类，包含下面的回调函数：

-   事件回调函数：用于响应回调事件

    ```
    /**
     * 服务端检测到了一句话的开始
     * @param response
     */
    abstract public void onSentenceBegin(
        StreamInputSpeechSynthesizerResponse response);
    
    /**
     * 服务端检测到了一句话的结束，并返回这句话的起止位置与所有时间戳
     * @param response
     */
    abstract public void onSentenceEnd(
        StreamInputSpeechSynthesizerResponse response);
    /**
     * 合成结束
     * @param response
     */
    abstract public void onSynthesisComplete(
        StreamInputSpeechSynthesizerResponse response);
    /**
     * 失败处理
     * @param response
     */
    abstract public void onFail(StreamInputSpeechSynthesizerResponse response);
    /**
     * 增量在response=>payload中返回时间戳
     * @param response
     */
    abstract public void onSentenceSynthesis(
        StreamInputSpeechSynthesizerResponse response);
    ```

-   数据回调函数：用于语音合成数据返回

    ```
    /**
    * 接收到语音合成音频数据流
    * @param message 二进制音频数据
    */
    abstract public void onAudioData(ByteBuffer message);
    ```


## **调用示例**

以下Java代码示例模拟了流式文本输入，请求语音合成，并使用扬声器进行音频播放的全过程。如您想将合成的音频保存到本地，请在`onAudioData`方法中将接收到的二进制音频流以追加模式保存到同一个文件中。

**重要**

代码运行前需要替换your-appkey以及your-token。

```
package org.example;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.StreamInputTts;
import com.alibaba.nls.client.protocol.tts.StreamInputTtsListener;
import com.alibaba.nls.client.protocol.tts.StreamInputTtsResponse;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


class PlaybackRunnable implements Runnable {
    // 设置音频格式，请根据实际自身设备，合成音频参数和平台选择配置
    // 这里选择24k、16bit、单通道，建议客户根据选用的模型采样率情况和自身设备兼容性选择其他采样率和格式
    private AudioFormat af;

    private DataLine.Info info;

    private SourceDataLine targetSource;

    private AtomicBoolean runFlag;

    private ConcurrentLinkedQueue<ByteBuffer> queue;

    public PlaybackRunnable(int sample_rate) {
        af = new AudioFormat(sample_rate, 16, 1, true, false);
        info = new DataLine.Info(SourceDataLine.class, af);
        targetSource = null;
        runFlag = new AtomicBoolean(true);
        queue = new ConcurrentLinkedQueue<>();
    }

    // 准备播放器
    public void prepare() throws LineUnavailableException {
        targetSource = (SourceDataLine) AudioSystem.getLine(info);
        targetSource.open(af, 4096);
        targetSource.start();
    }

    public void put(ByteBuffer buffer) {
        queue.add(buffer);
    }

    // 停止播放
    public void stop() {
        runFlag.set(false);
    }

    @Override
    public void run() {
        if (targetSource == null) {
            return;
        }
        while (runFlag.get()) {
            if (queue.isEmpty()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                continue;
            }
            ByteBuffer buffer = queue.poll();
            if (buffer == null) {
                continue;
            }
            byte[] data = buffer.array();
            targetSource.write(data, 0, data.length);
        }
        // 将缓存全部播放完
        if (!queue.isEmpty()) {
            ByteBuffer buffer = null;
            while ((buffer = queue.poll()) != null) {
                byte[] data = buffer.array();
                targetSource.write(data, 0, data.length);
            }
        }
        // 释放播放器
        targetSource.drain();
        targetSource.stop();
        targetSource.close();
    }
}


public class StreamInputTtsPlayableDemo {
    private static long startTime;
    NlsClient client;
    private String appKey;

    public StreamInputTtsPlayableDemo(String appKey, String token, String url) {
        this.appKey = appKey;
        //创建NlsClient实例应用全局创建一个即可。生命周期可和整个应用保持一致，默认服务地址为阿里云线上服务地址。
        if (url.isEmpty()) {
            client = new NlsClient(token);
        } else {
            client = new NlsClient(url, token);
        }
    }

    private static StreamInputTtsListener getSynthesizerListener(final PlaybackRunnable audioPlayer) {
        StreamInputTtsListener listener = null;
        try {
            listener = new StreamInputTtsListener() {
                private boolean firstRecvBinary = true;

                //流式文本语音合成开始
                @Override
                public void onSynthesisStart(StreamInputTtsResponse response) {
                    System.out.println("name: " + response.getName() +
                            ", status: " + response.getStatus());
                }

                //服务端检测到了一句话的开始
                @Override
                public void onSentenceBegin(StreamInputTtsResponse response) {
                    System.out.println("name: " + response.getName() +
                            ", status: " + response.getStatus());
                    System.out.println("Sentence Begin");
                }

                //服务端检测到了一句话的结束，获得这句话的起止位置和所有时间戳
                @Override
                public void onSentenceEnd(StreamInputTtsResponse response) {
                    System.out.println("name: " + response.getName() +
                            ", status: " + response.getStatus() + ", subtitles: " + response.getObject("subtitles"));

                }

                //流式文本语音合成结束
                @Override
                public void onSynthesisComplete(StreamInputTtsResponse response) {
                    // 调用onSynthesisComplete时，表示所有TTS数据已经接收完成，所有文本都已经合成音频并返回。
                    System.out.println("name: " + response.getName() + ", status: " + response.getStatus());
                    audioPlayer.stop();
                }

                //收到语音合成的语音二进制数据
                @Override
                public void onAudioData(ByteBuffer message) {
                    if (firstRecvBinary) {
                        // 此处计算首包语音流的延迟，收到第一包语音流时，即可以进行语音播放，以提升响应速度（特别是实时交互场景下）。
                        firstRecvBinary = false;
                        long now = System.currentTimeMillis();
                        System.out.println("tts first latency : " + (now - StreamInputTtsPlayableDemo.startTime) + " ms");
                    }
                    byte[] bytesArray = new byte[message.remaining()];
                    message.get(bytesArray, 0, bytesArray.length);
                    System.out.println("recv audio bytes:" + bytesArray.length);
                    audioPlayer.put(ByteBuffer.wrap(bytesArray));
                }

                //收到语音合成的增量音频时间戳
                @Override
                public void onSentenceSynthesis(StreamInputTtsResponse response) {
                    System.out.println("name: " + response.getName() +
                            ", status: " + response.getStatus() + ", subtitles: " + response.getObject("subtitles"));
                }

                @Override
                public void onFail(StreamInputTtsResponse response) {
                    // task_id是调用方和服务端通信的唯一标识，当遇到问题时，需要提供此task_id以便排查。
                    System.out.println(
                            "session_id: " + getStreamInputTts().getCurrentSessionId() +
                                    ", task_id: " + response.getTaskId() +
                                    //状态码
                                    ", status: " + response.getStatus() +
                                    //错误信息
                                    ", status_text: " + response.getStatusText());
                    audioPlayer.stop();
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listener;
    }

    public static void main(String[] args) throws Exception {
        String appKey = "your-appkey";
        String token = "your-token";
        // url取默认值
        String url = "wss://nls-gateway-cn-beijing.aliyuncs.com/ws/v1";
        String[] textArray = {"流式文本语音合成SDK，", "可以将输入的文本", "合成为语音二进制数据，",
                "相比于非流式语音合成，", "流式合成的优势在于实时性", "更强。用户在输入文本的同时",
                "可以听到接近同步的语音输出，", "极大地提升了交互体验，", "减少了用户等待时间。",
                "适用于调用大规模", "语言模型（LLM），以", "流式输入文本的方式", "进行语音合成的场景。"};
        StreamInputTtsPlayableDemo demo = new StreamInputTtsPlayableDemo(appKey, token, url);
        demo.process(textArray);
        demo.shutdown();
    }

    public void process(String[] textArray) throws InterruptedException {
        StreamInputTts synthesizer = null;
        PlaybackRunnable playbackRunnable = new PlaybackRunnable(24000);
        try {
            playbackRunnable.prepare();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        Thread playbackThread = new Thread(playbackRunnable);
        // 启动播放线程
        playbackThread.start();
        try {
            //创建实例，建立连接。
            synthesizer = new StreamInputTts(client, getSynthesizerListener(playbackRunnable));
            synthesizer.setAppKey(appKey);
            //设置返回音频的编码格式。支持PCM, WAV, MP3, OPUS
            synthesizer.setFormat(OutputFormatEnum.PCM);
            //设置返回音频的采样率。
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_24K);
            synthesizer.setVoice("longxiaochun");
            //音量，范围是0~100，可选，默认50。
            synthesizer.setVolume(50);
            //语调，范围是-500~500，可选，默认是0。
            synthesizer.setPitchRate(0);
            //语速，范围是-500~500，默认是0。
            synthesizer.setSpeechRate(0);
            //此方法将以上参数设置序列化为JSON发送给服务端，并等待服务端确认。
            long start = System.currentTimeMillis();
            synthesizer.startStreamInputTts();
            // synthesizer.setBitRate(64); 码率，仅对OPUS编码生效。
            System.out.println("tts start latency " + (System.currentTimeMillis() - start) + " ms");
            StreamInputTtsPlayableDemo.startTime = System.currentTimeMillis();
            //设置连续两次发送文本的最小时间间隔（毫秒），如果当前调用send时距离上次调用时间小于此值，则会阻塞并等待直到满足条件再发送文本
            synthesizer.setMinSendIntervalMS(100);
            for (String text : textArray) {
                //发送流式文本数据。
                synthesizer.sendStreamInputTts(text);
            }
            //通知服务端流式文本数据发送完毕，阻塞等待服务端处理完成。
            synthesizer.stopStreamInputTts();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭连接
            if (null != synthesizer) {
                synthesizer.close();
                playbackThread.join();
            }
        }
    }

    public void shutdown() {
        client.shutdown();
    }
}
```

## **常见SDK错误码**

更多错误码详情参见[错误码查询](https://help.aliyun.com/zh/isi/support/error-codes)。
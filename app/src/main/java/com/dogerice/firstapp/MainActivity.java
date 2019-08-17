package com.dogerice.firstapp;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dogerice.firstapp.utils.ZipUtils;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;


import butterknife.BindView;
import butterknife.ButterKnife;


import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import butterknife.BindView;

public class MainActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback {
    private static final String TAG = "MainActivity";

    @BindView(R.id.camera_view)
    public View mTextureView;
    @BindView(R.id.socketInfoView)
    public TextView mSocketInfoView;
    @BindView(R.id.dataInfoView)
    public TextView mDataInfoView;

    //    private static final String picPath = UVCCameraHelper.ROOT_PATH+System.currentTimeMillis()+UVCCameraHelper.SUFFIX_JPEG;
    //private static final String recordPath = "MainActivity";

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private int port = 16886;

    private WebSocket mWebSocket;
    private MyWebSocketServer myWebSocketServer = new MyWebSocketServer(port);

    private void startServer() {
        myWebSocketServer.start();
    }

    private boolean isRequest;
    private boolean isPreview;

    public UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        // 插入USB设备
        @Override
        public void onAttachDev(UsbDevice device) {
            if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
                showShortMsg("未检测到USB摄像头设备");
                return;
            }
            // 请求打开摄像头
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    int tDevIndex = -1;
                    List<UsbDevice> devList = mCameraHelper.getUsbDeviceList();
                    for (UsbDevice dev : devList) {
                        tDevIndex++;
                        if (dev.getVendorId() == 11205 && dev.getProductId() == 1281) {
                            Log.d(TAG, "getBodySensorDevIndex:" + tDevIndex);
                            break;
                        }
                    }
                    mCameraHelper.requestPermission(tDevIndex);

                }
            }
        }


        // 拔出USB设备
        @Override
        public void onDettachDev(UsbDevice device) {
            if (isRequest) {
                // 关闭摄像头
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + "已拨出");
            }
        }


        // 连接USB设备成功
        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("连接失败，请检查分辨率参数是否正确");
                isPreview = false;
            } else {
                showShortMsg("connecting");
                isPreview = true;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                            Log.d(TAG, "opened");
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }


        // 与USB设备断开连接
        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("连接断开");
        }
    };

    private class MyWebSocketServer extends WebSocketServer {

        public MyWebSocketServer(int port) {
            super(new InetSocketAddress(port));
        }

        @Override
        public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
            mWebSocket = webSocket;
            Log.d(TAG, "onOpen");
            setSocketInfo("WebSocket  onOpen");



        }

        @Override
        public void onClose(WebSocket webSocket, int i, String s, boolean b) {
//            mWebSocket = null;
            Log.d(TAG, "onClose");
            setSocketInfo("WebSocket  onClose");
            mCameraHelper.stopPusher();
            //FileUtils.releaseFile();

        }

        @Override
        public void onMessage(WebSocket webSocket, String s) {
            Log.d(TAG, "onMessage   ");
            setDataInfo("WebSocket  onMessage: " + s);
            switch (s){
                case "startRecord":
                    startRecord();
            }
        }

        @Override
        public void onError(WebSocket webSocket, Exception e) {
            Log.d(TAG, "onError");
            setSocketInfo("WebSocket  onError: " + e);
            new MyWebSocketServer(port);
            mCameraHelper.stopPusher();
        }

        @Override
        public void onStart() {
            Log.d(TAG, "onStart");
            setSocketInfo("WebSocket  onStart");
        }
    }

    private Handler mHandler = new Handler();

    private void setSocketInfo(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSocketInfoView.setText(message);
            }
        });
    }
    private void setDataInfo(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataInfoView.setText(message);
            }
        });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "main_activity onCreate");

        ButterKnife.bind(this);

        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
//        mCameraHelper.setDefaultPreviewSize(1280,720);
        mCameraHelper.setDefaultPreviewSize(640,480);

        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
//        FileUtils.createfile("/storage/emulated/0/uvcCamRec/test23333");
        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
//                Log.d(TAG, "onPreviewResult—数据大小:" + nv21Yuv.length + "B");
//                byte[] comBytes = ZipUtils.compress(nv21Yuv);
//                Log.d(TAG, "onPreviewResult—压缩1后大小:" + comBytes.length + "B");
//                byte[] decomBytes = ZipUtils.decompress(comBytes);
//                Log.d(TAG, "onPreviewResult—解压后大小:" + decomBytes.length + "B");
//                Log.d(TAG, "onPreviewResult—压缩2后大小:" + ZipUtils.gZip(nv21Yuv).length + "B");
//                GZIPInputStream gis  = new GZIPInputStream(new FileInputStream("/storage"))
//                Log.d(TAG, "onPreviewResult-发送数据大小:" + nv21Yuv.length + "字节");
//                Log.d(TAG, "onPreviewResult-发送数据:" + Arrays.toString(nv21Yuv));
//                FileUtils.putFileStream(nv21Yuv, 0, nv21Yuv.length);
            }
        });

        startServer();

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
        if (null != mWebSocket) {
//            mWebSocket.close();
        }
        //mWebSocket.close();
    }

    public void clickHandler(View source) {
        Log.d(TAG, "click");
        //获取UI界面中id为R.id.show的文本框
        TextView tv = (TextView) findViewById(R.id.show);
        //改变文本框的内容
        tv.setText("Main-UsbDevCount:" + mCameraHelper.getUsbDeviceCount()+" supportRes:"+mCameraHelper.getSupportedPreviewSizes());
        showShortMsg(mCameraHelper.getUsbDeviceList() + "");
    }

    public void clickCaptureHandler(View source) {
        showShortMsg("clickCapture");
        String picPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + "uvcCamPic/";
        String picFile = picPath + System.currentTimeMillis() + UVCCameraHelper.SUFFIX_JPEG;
        File picDir = new File(picPath);
        if (!picDir.exists()) {
            picDir.mkdir();
        }
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("抓拍异常，摄像头未开启");
            return;
        }
        mCameraHelper.capturePicture(picFile, new AbstractUVCCameraHandler.OnCaptureListener() {
            @Override
            public void onCaptureResult(String path) {
                Log.i(TAG, "保存路径：" + path);
            }
        });
    }

    public void clickRecordHandler(View source) {
        showShortMsg("clickRecording");
        String recPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + "uvcCamRec/";
//        String recFile = recPath + System.currentTimeMillis();
        String recFile = recPath + "record";
        File picDir = new File(recPath);
        if (!picDir.exists()) {
            picDir.mkdir();
        }
//        startRecord();
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("摄像头开启失败");
            return;
        }
        if (!mCameraHelper.isPushing()) {
//            FileUtils.createfile(recPath + "test.h264");
            // if you want to record,please create RecordParams like this
            RecordParams params = new RecordParams();
            params.setRecordPath(recFile);
            params.setRecordDuration(0);  // 设置为0，不分割保存
            params.setAutoSave(false);
            params.setVoiceClose(true);
            /*            params.setVoiceClose(mSwitchVoice.isChecked());    // is close voice*/
            mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                @Override
                public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                    Log.d(TAG, "onEncodeResult回调");
                    // type = 1,h264 video stream
                    Log.d(TAG,"encode大小："+data.length+" "+timestamp+" "+type);
                    if (type == 1) {
                        Log.d(TAG, "onEncodeResult回调h264-初始数据大小:" + data.length + "字节");
                        //Log.d(TAG, "onEncodeResult回调-发送数据:" + Arrays.toString(data));
//                        FileUtils.putFileStream(data, offset, length);
//                        saveByteData(data,0,data.length);
                        if (null != mWebSocket && !mWebSocket.isClosed()) {
                            byte[] comBytes = ZipUtils.compress(data);
                            Log.d(TAG, "发送压缩数据长度" + comBytes.length);
                            setDataInfo(System.currentTimeMillis() + "h264 原始大小：" + data.length + "压缩：" + comBytes.length);
                            try {
                                mWebSocket.send(comBytes);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        } else {
                            setDataInfo(System.currentTimeMillis() + "h264 原始数据大小：" + data.length);
                        }
                    }
                    // type = 0,aac audio stream
                    if (type == 0) {
                        Log.d(TAG, "onEncodeResult回调acc-初始数据大小:" + data.length + "字节");
                    }


                }

                @Override
                public void onRecordResult(final String videoPath) {
                    Log.d(TAG, "onRecordResult回调—videoPath = " + videoPath);
/*                    Thread sendThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sendFile(videoPath);
                        }
                    });
                    sendThread.start();*/
                }
            });
            // if you only want to push stream,please call like this
            // mCameraHelper.startPusher(listener);
            showShortMsg("start record...");
        } else {
            FileUtils.releaseFile();
            mCameraHelper.stopPusher();
            showShortMsg("stop record...");
        }
    }

    private void startRecord(){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"startRecord");
                String recPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                        + File.separator + "uvcCamRec/";

                String recFile = recPath + System.currentTimeMillis();
                File picDir = new File(recPath);
                if (!picDir.exists()) {
                    picDir.mkdir();
                }
//        startRecord();
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("摄像头开启失败");
                    return;
                }
                if (!mCameraHelper.isPushing()) {
//            FileUtils.createfile(recPath + "test.h264");
                    // if you want to record,please create RecordParams like this
                    RecordParams params = new RecordParams();
                    params.setRecordPath(recFile);
                    params.setRecordDuration(0);  // 设置为0，不分割保存
                    params.setAutoSave(false);
                    params.setVoiceClose(true);
                    /*            params.setVoiceClose(mSwitchVoice.isChecked());    // is close voice*/
                    mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                        @Override
                        public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                            Log.d(TAG, "onEncodeResult回调");
                            // type = 1,h264 video stream
                            Log.d(TAG,"encode大小："+data.length+" "+timestamp+" "+type);
                            if (type == 1) {
                                Log.d(TAG, "onEncodeResult回调h264-初始数据大小:" + data.length + "字节");
                                //Log.d(TAG, "onEncodeResult回调-发送数据:" + Arrays.toString(data));
//                        FileUtils.putFileStream(data, offset, length);
//                        saveByteData(data,0,data.length);
                                if (null != mWebSocket && !mWebSocket.isClosed()) {
                                    byte[] comBytes = ZipUtils.compress(data);
                                    Log.d(TAG, "发送压缩数据长度" + comBytes.length);
                                    setDataInfo(System.currentTimeMillis() + "h264 原始大小：" + data.length + "压缩：" + comBytes.length);
                                    try {
                                        mWebSocket.send(comBytes);
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                } else {
                                    setDataInfo(System.currentTimeMillis() + "h264 原始数据大小：" + data.length);
                                }
                            }
                            // type = 0,aac audio stream
                            if (type == 0) {
                                Log.d(TAG, "onEncodeResult回调acc-初始数据大小:" + data.length + "字节");
                            }


                        }

                        @Override
                        public void onRecordResult(final String videoPath) {
                            Log.d(TAG, "onRecordResult回调—videoPath = " + videoPath);
/*                    Thread sendThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sendFile(videoPath);
                        }
                    });
                    sendThread.start();*/
                        }
                    });
                    // if you only want to push stream,please call like this
                    // mCameraHelper.startPusher(listener);
                    showShortMsg("start record...");
                } else {
                    FileUtils.releaseFile();
                    mCameraHelper.stopPusher();
                    showShortMsg("stop record...");
                }
            }
        });

    }

    public void sendFile(String filePath) {
        try {
            FileInputStream fileInput = new FileInputStream(filePath);
            int size = -1;
            byte[] buffer = new byte[1024];
            while ((size = fileInput.read(buffer, 0, 1024)) != -1) {
                mWebSocket.send(buffer);
            }
            fileInput.close();
//            mWebSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void saveByteData(final byte[] bytes, final int off, final int len) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    FileUtils.putFileStream(bytes, off, len);
                } catch (Exception e) {

                }

            }
        });
    }

    public void clickStopHandler(View source) {
        showShortMsg("clickStopRecordiing");
        FileUtils.releaseFile();
        mCameraHelper.stopPusher();
    }

    public void clickSendHandler(View source) {
        if (null != mWebSocket) {
            Log.d(TAG, "clickSend");
            mWebSocket.send("1234");
        }
    }

    public void showShortMsg(String shortMsg) {
        Toast.makeText(this, shortMsg, Toast.LENGTH_SHORT).show();
        Log.d(TAG, shortMsg);
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface cameraViewInterface, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface cameraViewInterface, Surface surface, int i, int i1) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface cameraViewInterface, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }


}

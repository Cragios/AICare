package namb.com.aicare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ai.api.AIConfiguration;
import ai.api.AIDataService;
import ai.api.AIServiceException;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Fulfillment;
import ai.api.model.Result;
import ai.kitt.snowboy.SnowboyDetect;

import static java.lang.Thread.sleep;
import static namb.com.aicare.ImageUtils.argmax;

public class EyeTestActivity extends AppCompatActivity {

    // Start button
    private Button button;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;

    // Display text
    private TextView outputTextView;

    // Hotword
    private boolean shouldDetect;
    private SnowboyDetect snowboyDetect;
    static {
        System.loadLibrary("snowboy-detect-android");
    }

    // ASR
    private SpeechRecognizer speechRecognizer;
    MediaPlayer mediaPlayer;

    // TTS
    private TextToSpeech textToSpeech;

    // NLU
    private AIDataService aiDataService;

    // Storage
    private StorageReference mStorageRef;

    // Camera
    private Button captureButton;
    private TextureView cameraPreview;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    private File file;
    private static final int REQUEST_CAMERA_PERMISSION  = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private CountDownTimer countDownTimer;

    // Tensorflow
    //// PATH TO OUR MODEL FILE AND NAMES OF THE INPUT AND OUTPUT NODES
    private String MODEL_PATH = "file:///android_asset/glasses.pb";
    private String INPUT_NAME = "input_1";
    private String OUTPUT_NAME = "output_1";
    private TensorFlowInferenceInterface tf;
    //// ARRAY TO HOLD THE PREDICTIONS AND FLOAT VALUES TO HOLD THE IMAGE DATA
    float[] PREDICTIONS = new float[1000];
    private float[] floatValues;
    private int[] INPUT_SIZE = {224,224,3};
    //// VIEWS
    ImageView imageView;
    TextView resultView;
    Snackbar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eye_test);

        //setupASR();
        //setupTTS();
        //setupNLU();
        setupStorage();
        setupTensorFlow();
        setupCamera();
        setupHotword();
        //startHotword();
        setupButton();
        setupIOViews();
    }

    // Button
    private void setupButton() {
        button = findViewById(R.id.start_eye_test_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Threadings.runInMainThread(EyeTestActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        button.setVisibility(View.GONE);
                        resultView.setVisibility(View.GONE);

                        cameraPreview.setVisibility(View.VISIBLE);
                        captureButton.setVisibility(View.VISIBLE);
                    }
                });
                //startASR();
            }
        });
    }
    // Button

    // Speech IOViews
    private void setupIOViews() {
        outputTextView = findViewById(R.id.output_text_view);
    }
    // Speech IOViews

    // Hotword
    private void setupHotword() {
        shouldDetect = false;
        SnowboyUtils.copyAssets(this);

        // Setup Model File
        File snowboyDirectory = SnowboyUtils.getSnowboyDirectory();
        File model = new File(snowboyDirectory, "alexa.umdl");
        File common = new File(snowboyDirectory, "common.res");

        // Set Sensitivity
        snowboyDetect = new SnowboyDetect(common.getAbsolutePath(), model.getAbsolutePath());
        snowboyDetect.setSensitivity("0.60");
        snowboyDetect.applyFrontend(true);
    }

    private void startHotword() {
        Threadings.runInMainThread(this, new Runnable() {
            @Override
            public void run() {
                shouldDetect = true;
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

                int bufferSize = 3200;
                byte[] audioBuffer = new byte[bufferSize];
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e("hotword", "audio record fail to initialize");
                    return;
                }

                audioRecord.startRecording();
                Log.d("hotword", "start listening to hotword");

                while (shouldDetect) {
                    audioRecord.read(audioBuffer, 0, audioBuffer.length);

                    short[] shortArray = new short[audioBuffer.length / 2];
                    ByteBuffer.wrap(audioBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray);

                    int result = snowboyDetect.runDetection(shortArray, shortArray.length);
                    if (result > 0) {
                        Log.d("hotword", "detected");
                        shouldDetect = false;
                    }
                }

                audioRecord.stop();
                audioRecord.release();
                Log.d("hotword", "stop listening to hotword");

                // Actions after hotword is detected
                startASR();
            }
        });
    }
    //Hotword

    // ASR
    private void setupASR() {
        mediaPlayer = MediaPlayer.create(EyeTestActivity.this, R.raw.asr_sound);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                // Ignore
            }

            @Override
            public void onBeginningOfSpeech() {
                // Ignore
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Ignore
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Ignore
            }

            @Override
            public void onEndOfSpeech() {
                // Ignore
            }

            @Override
            public void onError(int error) {
                Log.e("asr", "Error: " + Integer.toString(error));
            }

            @Override
            public void onResults(Bundle results) {
                List<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (texts == null || texts.isEmpty()) {
                    startTTS("Please try again");
                    //inputTextView.setText("Please try again");
                } else {
                    String text = texts.get(0);

                    //inputTextView.setText(text);
                    startNLU(text);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Ignore
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Ignore
            }
        });
    }

    private void startASR() {
        Threadings.runInMainThread(this, new Runnable() {
            @Override
            public void run() {
                outputTextView.setText("");
                speechRecognizer.cancel();
                mediaPlayer.start();
                // Set Language
                final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en");
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

                // Stop hotword detection in case it is still running
                shouldDetect = false;

                // Start ASR
                speechRecognizer.startListening(recognizerIntent);
            }
        });
    }
    // ASR

    // TTS
    private void setupTTS() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                textToSpeech.setLanguage(Locale.ENGLISH);
                textToSpeech.setSpeechRate(1.0f);
            }
        });
    }

    private void startTTS(final String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        Threadings.runInMainThread(this, new Runnable() {
            @Override
            public void run() {
                outputTextView.setText(text);
            }
        });
    }
    // TTS

    // NLU
    private void setupNLU() {
        String clientAccessToken = "8ed1f973181c42f3b0fd10ff12d5929d";

        AIConfiguration aiConfiguration = new AIConfiguration(clientAccessToken,
                AIConfiguration.SupportedLanguages.English);
        aiDataService = new AIDataService(aiConfiguration);
    }

    private void startNLU(final String text) {
        Threadings.runInBackgroundThread( new Runnable() {
            @Override
            public void run() {
                try {
                    AIRequest aiRequest = new AIRequest();
                    aiRequest.setQuery(text);

                    AIResponse aiResponse = aiDataService.request(aiRequest);
                    Result result = aiResponse.getResult();
                    Fulfillment fulfillment = result.getFulfillment();
                    String speech = fulfillment.getSpeech();

                    NLU(speech);
                } catch (AIServiceException e) {
                    Log.e("nlu", e.getMessage(), e);
                    startHotword();
                }
            }
        });
    }

    public void NLU(String speech) {
        if (speech.equals("Ok, conducting eye test. Position your eyes in front of the camera.")) {
            startTTS(speech);
            try {
                sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Threadings.runInMainThread(this, new Runnable() {
                @Override
                public void run() {
                    button.setVisibility(View.GONE);
                    captureImage();
                }
            });
        }
        startHotword();
    }
    // NLU

    // Storage
    private void setupStorage() {
        mStorageRef = FirebaseStorage.getInstance().getReference();
    }
    // Storage

    // Camera
    private void setupCamera() {
        cameraPreview = findViewById(R.id.camera_preview);
        captureButton = findViewById(R.id.capture_button);

        assert cameraPreview != null;
        cameraPreview.setSurfaceTextureListener(textureListener);

        assert captureButton != null;
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(EyeTestActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void captureImage() {
        if (cameraDevice == null) {
            return;
        }
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            //assert manager != null;
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            // Capture image with custom size
            int width = 255;
            int height = 255;
            if (jpegSizes != null && jpegSizes.length > 0) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<Surface>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(cameraPreview.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Check orientation based on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            file = new File(Environment.getExternalStorageDirectory() + File.separator + UUID.randomUUID().toString() + ".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    Threadings.runInMainThread(EyeTestActivity.this, new Runnable() {
                        @Override
                        public void run() {
                            button.setVisibility(View.VISIBLE);
                            resultView.setVisibility(View.VISIBLE);

                            cameraPreview.setVisibility(View.GONE);
                            captureButton.setVisibility(View.GONE);
                        }
                    });
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);

                        // AI
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Threadings.runInMainThread(EyeTestActivity.this, new Runnable() {
                            @Override
                            public void run() {
                                progressBar.show();
                                predict(bitmap);
                            }
                        });
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }

                    // Add to storage
                    Uri storageFile = Uri.fromFile(file);
                    StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
                    StorageReference imageReference = mStorageRef.child("images/"+ storageFile.getLastPathSegment());
                    UploadTask uploadTask = imageReference.putFile(storageFile, metadata);
                    uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                            Toast.makeText(EyeTestActivity.this, "Upload is " + (int)progress + "% done", Toast.LENGTH_SHORT).show();
                            // Use sessionUri to resume download but idk how :(
                            Uri sessionUri = taskSnapshot.getUploadSessionUri();
                        }
                    }).addOnPausedListener(new OnPausedListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                            Toast.makeText(EyeTestActivity.this, "Upload is paused", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            Toast.makeText(EyeTestActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Toast.makeText(EyeTestActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();

                        }
                    });

                    // Add to gallery
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(file);
                    mediaScanIntent.setData(contentUri);
                    EyeTestActivity.this.sendBroadcast(mediaScanIntent);

                }
            };

            reader.setOnImageAvailableListener(readerListener,mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(EyeTestActivity.this, "Saved " + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = cameraPreview.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // The camera is already closed
                    if (cameraDevice == null) {
                        return;
                    }
                    // When the session is ready, start displaying the preview
                    cameraCaptureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(EyeTestActivity.this, "Configuration changed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(EyeTestActivity.this, new String[] {
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (cameraDevice == null) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    /*//@Override
    public void onRequestPermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(EyeTestActivity.this, "Sorry, you cannot use the app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }*/
    // Camera

    // Check image
    private void setupTensorFlow() {
        tf = new TensorFlowInferenceInterface(getAssets(),MODEL_PATH);

        imageView = findViewById(R.id.image_view);
        resultView = findViewById(R.id.prediction);

        progressBar = Snackbar.make(imageView,"PROCESSING IMAGE", Snackbar.LENGTH_INDEFINITE);
    }

    // TODO: do something
    private void checkImage(String label) {
        if (label.equals("glasses")) {
            startTTS("You have failed the myopia test. Please read out the letters for further testing.");
            Threadings.runInMainThread(this, new Runnable() {
                @Override
                public void run() {
                    try {
                        sleep(7000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } else if (label.equals("no glasses")) {
            startTTS("You have passed the myopia test.");
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void predict(final Bitmap bitmap){
        //Runs inference in background thread
        new AsyncTask<Integer,Integer,Integer>(){

            @Override
            protected Integer doInBackground(Integer ...params){

                //Resize the image into 224 x 224
                Bitmap resized_image = ImageUtils.processBitmap(bitmap,224);

                //Normalize the pixels
                floatValues = ImageUtils.normalizeBitmap(resized_image,224,127.5f,255f);

                //Pass input into the tensorflow
                tf.feed(INPUT_NAME,floatValues,1,224,224,3);

                //compute predictions
                tf.run(new String[]{OUTPUT_NAME});

                //copy the output into the PREDICTIONS array
                tf.fetch(OUTPUT_NAME,PREDICTIONS);

                //Obtained highest prediction
                Object[] results = argmax(PREDICTIONS);

                int class_index = (Integer) results[0];
                float confidence = (Float) results[1];

                try{
                    final String conf = String.valueOf(confidence * 100).substring(0,5);

                    //Convert predicted class index into actual label name
                    final String label = ImageUtils.getLabel(getAssets().open("model_class.json"),class_index);

                    //Display result on UI
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            progressBar.dismiss();
                            resultView.setText(label + " : " + conf + "%");
                            Log.e("Values", Arrays.toString(floatValues));
                        }
                    });

                    //checkImage(label);
                }
                catch (Exception ignored){
                }
                return 0;
            }
        }.execute(0);
    }
    // Check image

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (cameraPreview.isAvailable()) {
            openCamera();
        } else {
            cameraPreview.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }
}

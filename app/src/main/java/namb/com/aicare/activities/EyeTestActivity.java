package namb.com.aicare.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ai.api.AIConfiguration;
import ai.api.AIDataService;
import ai.api.AIServiceException;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Fulfillment;
import ai.api.model.Result;
import ai.kitt.snowboy.SnowboyDetect;
import namb.com.aicare.R;
import namb.com.aicare.utils.ImageUtils;
import namb.com.aicare.utils.SnowboyUtils;
import namb.com.aicare.utils.Threadings;

import static java.lang.Thread.sleep;
import static namb.com.aicare.utils.ImageUtils.argmax;

public class EyeTestActivity extends AppCompatActivity {

    private static final String TAG = "EyeTestActivity";

    // Profile
    private FirebaseUser currentUser;
    private IdpResponse response;
    private String currentUserEmail;

    // Start button
    private Button startEyeTestButton;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;

    // Views
    private View rootView;
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
    private StorageReference storageReference;
    private DatabaseReference databaseReference;
    private Uri sessionUri;
    private boolean uploadInterrupted;
    private String predictionResult;

    private ProgressBar uploadProgressBar;
    private TextView uploadTextView;

    private boolean imageTaken;

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
    TextView predictionTextView;
    Snackbar predictionProgressBar;

    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable IdpResponse response) {
        return new Intent(context, EyeTestActivity.class)
                .putExtra(ExtraConstants.IDP_RESPONSE, response);
    }

    // Activity lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eye_test);

        setupProfile();
        setupViews();
        //setupASR();
        //setupTTS();
        //setupNLU();
        setupStorage();
        setupTensorFlow();
        setupHotword();
        //startHotword();
        setupButtons();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkSignedIn();

        Log.e("Resume", "True");
        // process image if one was just taken
        File imageFile = new File(getExternalFilesDir(null), "pic.jpg");
        if (imageTaken && imageFile.exists()) {
            processImage(imageFile);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    // Activity lifecycle

    private void checkSignedIn() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(LoginActivity.createIntent(getApplicationContext()));
            finish();
        }
    }

    // Setup general
    private void setupProfile() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        response = getIntent().getParcelableExtra(ExtraConstants.IDP_RESPONSE);
        currentUserEmail = currentUser.getEmail();
    }

    private void setupViews() {
        rootView = findViewById(R.id.root);
        outputTextView = findViewById(R.id.output_text_view);
    }

    private void setupButtons() {
        startEyeTestButton = findViewById(R.id.start_eye_test_button);
        startEyeTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageTaken = true;
                startActivity(new Intent(getApplicationContext(), CameraActivity.class));
                //startASR();
            }
        });
    }
    // Setup general

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
                    startEyeTestButton.setVisibility(View.GONE);
                    //captureImage();
                }
            });
        }
        startHotword();
    }
    // NLU

    // Storage
    private void setupStorage() {
        storageReference = FirebaseStorage.getInstance().getReference();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        sessionUri = null;

        uploadProgressBar = findViewById(R.id.upload_progress_bar);
        uploadTextView = findViewById(R.id.upload_text_view);

        uploadInterrupted = false;
        imageTaken = false;
    }

    private void processImage(File imageFile) {

        // ML the image and save to storage

        // AI
        final Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getPath());
        Threadings.runInMainThread(EyeTestActivity.this, new Runnable() {
            @Override
            public void run() {
                predictionProgressBar.show();
                predict(bitmap);
            }
        });

        // Add to storage
        Uri storageFile = Uri.fromFile(imageFile);
        StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
        final String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN).format(new Date());
        final String fileName = timeStamp.replaceAll("[^0-9]", "");
        final String filePath = currentUserEmail + "/" + fileName + ".jpg";
        final StorageReference imageReference = storageReference.child(filePath);
        final UploadTask uploadTask;
        if (uploadInterrupted && sessionUri != null) {
            uploadTask = imageReference.putFile(storageFile, metadata, sessionUri);
        } else {
            uploadTask = imageReference.putFile(storageFile, metadata);
        }
        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                final double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                uploadProgressBar.setProgress((int) progress);
                uploadTextView.setText((int) progress + "%");
                Log.e("Upload", (int)progress + "%");

                sessionUri = taskSnapshot.getUploadSessionUri();
            }
        }).addOnPausedListener(new OnPausedListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                uploadTextView.setText("Upload is paused");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                uploadInterrupted = true;
                uploadTextView.setText("Upload failed");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                uploadProgressBar.setProgress(0);
                databaseReference.child(currentUserEmail.replaceAll("[^a-zA-Z0-9]", "")).child(timeStamp).setValue(predictionResult);
                uploadInterrupted = false;
                uploadTextView.setText("Upload successful");
            }
        });

        // Delete image from memory
        boolean imageDeleted = imageFile.delete();

        // Change boolean at end of function in case code gets interrupted halfway?
        imageTaken = false;
    }
    // Storage

    // Check image
    private void setupTensorFlow() {
        tf = new TensorFlowInferenceInterface(getAssets(),MODEL_PATH);

        predictionTextView = findViewById(R.id.prediction_text_view);
        predictionResult = "no result";
        predictionProgressBar = Snackbar.make(rootView,"PROCESSING IMAGE", Snackbar.LENGTH_INDEFINITE);
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
                            predictionProgressBar.dismiss();
                            predictionResult = label + " : " + conf + "%";
                            predictionTextView.setText(predictionResult);
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

    // Snackbar
    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(rootView, errorMessageRes, Snackbar.LENGTH_LONG).show();
    }
    // Snackbar
}

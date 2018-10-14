package namb.com.aicare.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
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
import android.widget.ImageView;
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
    private String currentUserUid;

    // Start button
    private Button startEyeTestButton;
    private Button galleryButton;

    // Views
    private View rootView;
    private TextView predictionTextView;

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
    //// NAMES OF THE INPUT AND OUTPUT NODES
    private String INPUT_NAME = "input_1";
    private String OUTPUT_NAME = "output_1";
    private TensorFlowInferenceInterface tf;
    //// ARRAY TO HOLD THE PREDICTIONS AND FLOAT VALUES TO HOLD THE IMAGE DATA
    float[] PREDICTIONS = new float[1000];
    private float[] floatValues;
    private int[] INPUT_SIZE = {224,224,3};

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
        setupStorage();
        setupTensorFlow();
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
        currentUserUid = currentUser.getUid();
    }

    private void setupViews() {
        rootView = findViewById(R.id.root);
        predictionTextView = findViewById(R.id.prediction_text_view);
    }

    private void setupButtons() {
        startEyeTestButton = findViewById(R.id.start_eye_test_button);
        startEyeTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageTaken = true;
                startActivity(new Intent(getApplicationContext(), CameraActivity.class));

            }
        });

        galleryButton = findViewById(R.id.gallery_button);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), ImageActivity.class));
            }
        });
    }
    // Setup general

    // Storage
    private void setupStorage() {
        storageReference = FirebaseStorage.getInstance().getReference(currentUserUid);
        databaseReference = FirebaseDatabase.getInstance().getReference(currentUserUid);

        sessionUri = null;

        uploadProgressBar = findViewById(R.id.upload_progress_bar);
        uploadTextView = findViewById(R.id.upload_text_view);

        uploadInterrupted = false;
        imageTaken = false;
    }

    private void processImage(File imageFile) {

        // ML the image and save to storage

        // AI
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getPath());
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        int width, height;
        height = rotatedBitmap.getHeight();
        width = rotatedBitmap.getWidth();
        final Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(rotatedBitmap, 0, 0, paint);

        Threadings.runInMainThread(EyeTestActivity.this, new Runnable() {
            @Override
            public void run() {
                // Glasses Check
                //predict(rotatedBitmap, "model_class_glasses.json", "file:///android_asset/glasses.pb");
                // OCT Check
                predict(bmpGrayscale, "model_class_OCT.json", "file:///android_asset/OCT.pb");
            }
        });

        // Add to storage
        Uri uploadFile = Uri.fromFile(imageFile);
        StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
        final String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN).format(new Date());
        final String fileName = timeStamp.replaceAll("[^0-9]", "") + ".jpg";
        final StorageReference imageReference = storageReference.child(fileName);
        final UploadTask uploadTask;
        if (uploadInterrupted && sessionUri != null) {
            uploadTask = imageReference.putFile(uploadFile, metadata, sessionUri);
        } else {
            uploadTask = imageReference.putFile(uploadFile, metadata);
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
                databaseReference.child(timeStamp).setValue(predictionResult);
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
        predictionResult = "no result";
    }

    @SuppressLint("StaticFieldLeak")
    public void predict(final Bitmap bitmap, final String filename, String MODEL_PATH){
        tf = new TensorFlowInferenceInterface(getAssets(),MODEL_PATH);
        predictionTextView.setText(R.string.processing_image);
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
                    final String label = ImageUtils.getLabel(getAssets().open(filename),class_index);

                    //Display result on UI
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            predictionResult = label + " : " + conf + "%";
                            predictionTextView.setText(predictionResult);
                        }
                    });

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

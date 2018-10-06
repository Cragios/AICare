package namb.com.aicare.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import namb.com.aicare.R;
import namb.com.aicare.adapters.RecordsAdapter;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // View
    private View rootView;
    private TextView welcomeText;
    private TextView currRecordText;

    // Buttons
    private Button eyeTestButton;
    private Button recordsButton;
    private Button settingsButton;

    // Profile
    private FirebaseUser currentUser;
    private IdpResponse response;
    private String currentUserUid;

    // Records
    private DatabaseReference databaseRef;
    private List<DataSnapshot> uploads;
    private String status;

    // Ads
    private AdView adView;

    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable IdpResponse response) {
        return new Intent(context, HomeActivity.class)
                .putExtra(ExtraConstants.IDP_RESPONSE, response);
    }

    // Activity lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        setupViews();
        setupButtons();
        setupProfile();

        MobileAds.initialize(this, "ca-app-pub-6858433785606125~8993315005");
        adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
        Log.e(TAG, getApplicationContext().getPackageName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSignedIn();
        checkResults();
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
    private void setupViews() {
        rootView = findViewById(R.id.root);
        welcomeText = findViewById(R.id.welcome_text);
        currRecordText = findViewById(R.id.curr_record_text);
    }

    private void setupButtons() {
        eyeTestButton = findViewById(R.id.eye_test_button);
        eyeTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(EyeTestActivity.createIntent(getApplicationContext(), response));
            }
        });

        recordsButton = findViewById(R.id.records_button);
        recordsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(RecordsActivity.createIntent(getApplicationContext(), response));
            }
        });

        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(SettingsActivity.createIntent(getApplicationContext(), response));
            }
        });
    }

    private void setupProfile() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        response = getIntent().getParcelableExtra(ExtraConstants.IDP_RESPONSE);
        currentUserUid = currentUser.getUid();

        welcomeText.setText(
                TextUtils.isEmpty(currentUser.getDisplayName()) ? "No display name" : "Welcome " + currentUser.getDisplayName() + "!");
    }
    // Setup general

    // Check Results

    // Check Results
    private void checkResults() {
        databaseRef = FirebaseDatabase.getInstance().getReference(currentUserUid);
        uploads = new ArrayList<>();

        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    uploads.add(postSnapshot);
                }
                DataSnapshot snapshot = uploads.get(uploads.size()-1);

                status = String.valueOf(snapshot.getValue()).split(" ")[0];
                if (status.equals("NORMAL")) {
                    currRecordText.setText(snapshot.getKey().split(" ")[0] + ": " + status);
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(getApplicationContext(), databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    // Check Results

    // Snackbar
    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(rootView, errorMessageRes, Snackbar.LENGTH_LONG).show();
    }
    // Snackbar
}

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

import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import namb.com.aicare.R;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // Profile
    private FirebaseUser currentUser;
    private IdpResponse response;
    private TextView welcomeText;

    // View
    private View rootView;

    // Buttons
    private Button eyeTestButton;
    private Button recordsButton;
    private Button settingsButton;

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

        setupProfile();
        setupViews();
        setupButtons();

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

        welcomeText = findViewById(R.id.welcome_text);
        welcomeText.setText(
                TextUtils.isEmpty(currentUser.getDisplayName()) ? "No display name" : "Welcome " + currentUser.getDisplayName() + "!");
    }

    private void setupViews() {
        rootView = findViewById(R.id.root);
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
    // Setup general

    // Snackbar
    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(rootView, errorMessageRes, Snackbar.LENGTH_LONG).show();
    }
    // Snackbar
}

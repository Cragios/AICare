package namb.com.aicare.activities;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import namb.com.aicare.R;
import namb.com.aicare.activities.EyeTestActivity;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // View
    private View mRootView;

    // Buttons
    private Button eyeTestButton;
    private Button recordsButton;
    private Button signOutButton;

    // Profile
    FirebaseUser currentUser;
    IdpResponse response;
    private TextView welcomeText;

    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable IdpResponse response) {
        return new Intent().setClass(context, HomeActivity.class)
                .putExtra(ExtraConstants.IDP_RESPONSE, response);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        checkSignedIn();
        setupProfile();
        setupButtons();
    }

    private void checkSignedIn() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(LoginActivity.createIntent(this));
            finish();
        }
    }

    private void setupProfile() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        response = getIntent().getParcelableExtra(ExtraConstants.IDP_RESPONSE);

        welcomeText = findViewById(R.id.welcome_text);
        welcomeText.setText(
                TextUtils.isEmpty(currentUser.getDisplayName()) ? "No display name" : "Welcome " + currentUser.getDisplayName() + "!");
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

        signOutButton = findViewById(R.id.sign_out_button);
        signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AuthUI.getInstance()
                        .signOut(getApplicationContext())
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    startActivity(LoginActivity.createIntent(getApplicationContext()));
                                    finish();
                                } else {
                                    Log.w(TAG, "signOut:failure", task.getException());
                                    showSnackbar(R.string.sign_out_failed);
                                }
                            }
                        });
            }
        });
    }

    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(mRootView, errorMessageRes, Snackbar.LENGTH_LONG).show();
    }
}

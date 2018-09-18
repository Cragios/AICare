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
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Objects;

import namb.com.aicare.R;
import namb.com.aicare.adapters.RecordsAdapter;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // Profile
    private FirebaseUser currentUser;
    private IdpResponse response;
    private String currentUserName;
    private String currentUserEmail;

    private View rootView;

    private Button signOutButton;
    private Button deleteButton;

    // Storage
    private StorageReference storageReference;
    private DatabaseReference databaseReference;
    private Boolean delete;

    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable IdpResponse response) {
        return new Intent(context, SettingsActivity.class)
                .putExtra(ExtraConstants.IDP_RESPONSE, response);
    }

    // Activity lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupProfile();
        setupViews();
        setupStorage();
        setupButtons();
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
        currentUserName = currentUser.getDisplayName();
        currentUserEmail = currentUser.getEmail();
    }


    private void setupViews() {
        rootView = findViewById(R.id.root);
    }

    private void setupButtons() {
        signOutButton = findViewById(R.id.sign_out_button);
        signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        deleteButton = findViewById(R.id.delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Display warning screen
                delete();
            }
        });
    }
    // Setup general

    // TODO: Populate views
    private void showProfile() {
        //
    }
    // Populate views

    // Account functions
    private void signOut() {
        AuthUI.getInstance()
                .signOut(getApplicationContext())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            finish();
                        } else {
                            Log.w(TAG, "signOut:failure", task.getException());
                            showSnackbar(R.string.sign_out_failed);
                        }
                    }
                });
    }

    private void delete() {
        delete = true;
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    if (delete) {
                        storageReference.child(Objects.requireNonNull(postSnapshot.getKey()).replaceAll("[^a-zA-Z0-9]", "") + ".jpg").delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                databaseReference.removeValue();
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(getApplicationContext(), databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        delete = false;

        currentUser.delete()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User account deleted.");
                        }
                    }
                });
        signOut();
    }
    // Account functions

    // Storage
    private void setupStorage() {
        storageReference = FirebaseStorage.getInstance().getReference(currentUserEmail);
        databaseReference = FirebaseDatabase.getInstance().getReference(currentUserEmail.replaceAll("[^a-zA-Z ]", ""));
    }
    // Storage

    // Snackbar
    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(rootView, errorMessageRes, Snackbar.LENGTH_LONG).show();
    }
    // Snackbar
}

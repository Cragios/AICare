package namb.com.aicare.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import namb.com.aicare.adapters.RecordsAdapter;
import namb.com.aicare.R;

public class RecordsActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private RecordsAdapter mAdapter;

    private ProgressBar mProgressCircle;

    private FirebaseUser currentUser;
    private IdpResponse response;
    private String currentUserUid;

    private DatabaseReference mDatabaseRef;
    private List<DataSnapshot> mUploads;

    private Button scrollTopButton;

    @NonNull
    public static Intent createIntent(@NonNull Context context, @Nullable IdpResponse response) {
        return new Intent(context, RecordsActivity.class)
                .putExtra(ExtraConstants.IDP_RESPONSE, response);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        setupProfile();
        setupView();
        setupScrollTopButton();
    }

    // Profile
    private void setupProfile() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        response = getIntent().getParcelableExtra(ExtraConstants.IDP_RESPONSE);
        currentUserUid = currentUser.getUid();
    }
    //Profile

    // Recycler view
    private void setupView() {
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mProgressCircle = findViewById(R.id.progress_circle);

        mUploads = new ArrayList<>();

        mDatabaseRef = FirebaseDatabase.getInstance().getReference(currentUserUid);

        mDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    mUploads.add(postSnapshot);
                }

                mAdapter = new RecordsAdapter(RecordsActivity.this, mUploads, currentUserUid);

                mRecyclerView.setAdapter(mAdapter);
                mRecyclerView.smoothScrollToPosition(0);
                mProgressCircle.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(getApplicationContext(), databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                mProgressCircle.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void setupScrollTopButton() {
        scrollTopButton = findViewById(R.id.scroll_top_button);
        scrollTopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecyclerView.smoothScrollToPosition(0);
            }
        });
    }
    // Recycler view
}

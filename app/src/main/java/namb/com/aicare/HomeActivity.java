package namb.com.aicare;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class HomeActivity extends AppCompatActivity {

    // buttons
    private Button eye_test_button;
    private Button records_button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        setupButtons();
    }

    private void setupButtons() {
        eye_test_button = findViewById(R.id.eye_test_button);
        eye_test_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), EyeTestActivity.class));
                finish();
            }
        });

        records_button = findViewById(R.id.records_button);
        records_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Open records
            }
        });
    }
}

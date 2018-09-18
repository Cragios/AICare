package namb.com.aicare.activities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.util.ExtraConstants;

import java.util.ArrayList;

import namb.com.aicare.R;

public class PermissionActivity extends AppCompatActivity {
    private static final String TAG = PermissionActivity.class.getSimpleName();

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, PermissionActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check for permissions
        if (hasRequiredPermissions()) {
            Log.d(TAG, "Permissions checking is OK, proceeding to Main Activity");

            startActivity(LoginActivity.createIntent(getApplicationContext()));

            finish();
            return;
        }

        Log.e(TAG, "Do not have required permissions");

        try {
            requestPermissions();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not find package");
        }
    }

    private void requestPermissions() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = getPackageManager().getPackageInfo(
                getPackageName(),
                PackageManager.GET_PERMISSIONS
        );

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        if (packageInfo.requestedPermissions != null) {
            for (String permission : packageInfo.requestedPermissions) {
                if (!hasBeenGranted(permission)) {
                    permissionsToRequest.add(permission);
                    Log.d(TAG, "Requesting permission for " + permission);
                }
            }
        }

        int numberOfPermissions = permissionsToRequest.size();
        if (numberOfPermissions > 0) {
            String[] permissions = new String[numberOfPermissions];
            for (int i = 0; i < numberOfPermissions; i++) {
                permissions[i] = permissionsToRequest.get(i);
            }
            ActivityCompat.requestPermissions(this, permissions, 1234);
        }
    }

    private boolean hasBeenGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRequiredPermissions() {
        PackageInfo packageInfo = null;

        try {
            packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_PERMISSIONS
            );
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }

        if (packageInfo == null) {
            Log.e(TAG, "Null Package Info");
            return false;
        }

        boolean hasAllRequredPermissions = true;

        if (packageInfo.requestedPermissions != null) {
            for (String permission : packageInfo.requestedPermissions) {
                if (!hasBeenGranted(permission)) {
                    hasAllRequredPermissions = false;
                }
            }
        }

        return hasAllRequredPermissions;
    }
}

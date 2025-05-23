package net.juhonkoti.scantopaperless;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private EditText etUrl, etToken;
    private Button btnScan;
    private TextView textDebug;
    private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;
    private OkHttpClient httpClient = new OkHttpClient();

    private static final String PREFS_NAME = "ScanToPaperlessPrefs";
    private static final String KEY_URL = "url";
    private static final String KEY_TOKEN = "token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etUrl = findViewById(R.id.etUrl);
        etToken = findViewById(R.id.etToken);
        btnScan = findViewById(R.id.btnScan);
        textDebug = findViewById(R.id.textDebug);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_URL, "https://");
        etUrl.setText(savedUrl);
        String savedToken = prefs.getString(KEY_TOKEN, "");
        etToken.setText(savedToken);

        if (savedUrl.equals("https://") || savedToken.equals("") || true) {
            textDebug.setText("Welcome!\nSet your paperless-ngx hostname to the url field. Example value: \"https://paperless.foobar.com\".\n\n" +
                    "Also create an API token from the Paperless user menu from top-right in the \"Edit Profile\" dialog and set it to the token field.\"");
        }

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                saveSettings();
            }

            // Required methods (can be left empty)
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        };

        etUrl.addTextChangedListener(watcher);
        etToken.addTextChangedListener(watcher);

        // 1) Prepare ML Kit scanner launcher
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(50)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .build();  // 1

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);

        scannerLauncher = registerForActivityResult(
                new StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        GmsDocumentScanningResult scanResult =
                                GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                        // We requested PDF only, so this is non-null:
                        GmsDocumentScanningResult.Pdf pdf = scanResult.getPdf();
                        if (pdf != null) {
                            uploadPdf(pdf.getUri());
                        }
                    }
                }
        );

        // 2) Button kicks off ML Kit scan UI
        btnScan.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            String token = etToken.getText().toString().trim();
            if (url.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Enter both URL and token", Toast.LENGTH_SHORT).show();
                return;
            }
            // Launch scanner
            scanner.getStartScanIntent(this)
                    .addOnSuccessListener(
                            intentSender ->
                                    scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build())
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this,
                                    "Failed to start scanner: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    private void saveSettings() {

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String url = etUrl.getText().toString();
        String token = etToken.getText().toString();
        editor.putString(KEY_URL, url);
        editor.putString(KEY_TOKEN, token);
        Log.d(TAG, "saveSettings called: " + url + " and " + token);
        editor.commit();
    }

    // Read the PDF from its content URI and POST it
    private void uploadPdf(Uri pdfUri) {
        String url = etUrl.getText().toString().trim() + "/api/documents/post_document/";
        String token = etToken.getText().toString().trim();

        Log.d(TAG, "uploadPdf to " + url + " with token " + token);
        textDebug.setText("Starting upload...\n");
        try (InputStream in = getContentResolver().openInputStream(pdfUri)) {
            byte[] pdfBytes = new byte[in.available()];
            in.read(pdfBytes);

            // Create multipart form body
            RequestBody pdfRequestBody = RequestBody.create(pdfBytes, MediaType.parse("application/pdf"));
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("document", "file.pdf", pdfRequestBody) // "document" is the form field name
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Token " + token)
                    .post(requestBody)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    Log.d(TAG, "uploadPdf failure: " + e.getMessage());
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Upload failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
                }

                @Override public void onResponse(Call call, Response response) {
                    Log.d(TAG, "uploadPdf response code: " + response.code());
                    textDebug.append("response code: " + response.code() + "\n");
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "null";

                        textDebug.append("response body: " + responseBody + "\n");
                        Log.d(TAG, "uploadPdf response body: " + responseBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading response body", e);
                    }

                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Upload " + (response.isSuccessful() ? "succeeded" : "error: " + response.code()),
                                    Toast.LENGTH_LONG).show()
                    );
                }
            });

        } catch (IOException e) {
            Toast.makeText(this, "Error reading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

}
package com.yourcompany.unitycameraplugin; // 請確保這是您 AAR 模組的包名

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.unity3d.player.UnityPlayer; // Unity 提供的類，用於在 Android 中與 Unity 交互

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CameraGalleryPlugin {

    private static final String TAG = "CameraGalleryPlugin";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int PERMISSION_REQUEST_CODE = 3;

    private static String currentPhotoPath;
    private static Activity currentActivity;
    private static String unityGameObject; // 接收 Unity 訊息的 GameObject 名稱
    private static String unityCallbackMethod; // Unity 中的回調方法

    public static void initialize(Activity activity, String goName, String callbackMethod) {
        currentActivity = activity;
        unityGameObject = goName;
        unityCallbackMethod = callbackMethod;
        Log.d(TAG, "Plugin Initialized with GameObject: " + unityGameObject + ", Callback: " + unityCallbackMethod);
    }

    public static void takePicture() {
        Log.d(TAG, "takePicture called");
        if (currentActivity == null) {
            Log.e(TAG, "Activity not initialized.");
            return;
        }

        if (checkPermissions()) {
            dispatchTakePictureIntent();
        } else {
            requestPermissions();
        }
    }

    public static void pickImageFromGallery() {
        Log.d(TAG, "pickImageFromGallery called");
        if (currentActivity == null) {
            Log.e(TAG, "Activity not initialized.");
            return;
        }

        if (checkPermissions()) {
            dispatchPickImageIntent();
        } else {
            requestPermissions();
        }
    }

    private static boolean checkPermissions() {
        int cameraPermission = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.CAMERA);
        int writeStoragePermission = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readStoragePermission = ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.READ_EXTERNAL_STORAGE);

        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
               writeStoragePermission == PackageManager.PERMISSION_GRANTED &&
               readStoragePermission == PackageManager.PERMISSION_GRANTED;
    }

    private static void requestPermissions() {
        ActivityCompat.requestPermissions(currentActivity,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
    }

    // 從 Unity 呼叫此方法來處理權限請求結果
    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions granted.");
                // 可以再次嘗試開啟相機或相冊
            } else {
                Log.e(TAG, "Permissions denied.");
                sendUnityMessage("Permission Denied");
            }
        }
    }

    private static void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 確保有相機應用程式可以處理 Intent
        if (takePictureIntent.resolveActivity(currentActivity.getPackageManager()) != null) {
            // 建立用於儲存圖片的檔案
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Error creating image file: " + ex.getMessage());
            }
            // 只有成功建立檔案，才繼續
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(currentActivity,
                        currentActivity.getPackageName() + ".fileprovider", // 與 AndroidManifest.xml 中的 provider 保持一致
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                currentActivity.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private static void dispatchPickImageIntent() {
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageIntent.setType("image/*");
        currentActivity.startActivityForResult(pickImageIntent, REQUEST_PICK_IMAGE);
    }

    private static File createImageFile() throws IOException {
        // 建立一個帶有時間戳記的圖片檔名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = currentActivity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // 儲存檔案路徑以供未來使用
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // 從 Unity 呼叫此方法來處理 Activity 結果
    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Log.d(TAG, "Image captured successfully. Path: " + currentPhotoPath);
                // 將圖片轉換為 Base64 字符串並發送給 Unity
                String base64Image = encodeImageToBase64(currentPhotoPath);
                sendUnityMessage("CAMERA_SUCCESS:" + base64Image);
            } else if (requestCode == REQUEST_PICK_IMAGE) {
                if (data != null && data.getData() != null) {
                    Uri selectedImageUri = data.getData();
                    Log.d(TAG, "Image picked from gallery. URI: " + selectedImageUri.toString());
                    try {
                        String base64Image = encodeImageToBase64(selectedImageUri);
                        sendUnityMessage("GALLERY_SUCCESS:" + base64Image);
                    } catch (IOException e) {
                        Log.e(TAG, "Error encoding gallery image: " + e.getMessage());
                        sendUnityMessage("ERROR:Failed to load gallery image.");
                    }
                } else {
                    Log.e(TAG, "Gallery result data is null.");
                    sendUnityMessage("ERROR:No image selected from gallery.");
                }
            }
        } else {
            Log.d(TAG, "Activity result not OK. Request Code: " + requestCode + ", Result Code: " + resultCode);
            sendUnityMessage("CANCEL"); // 用戶取消了操作
        }
    }

    private static String encodeImageToBase64(String imagePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        return convertBitmapToBase64(bitmap);
    }

    private static String encodeImageToBase64(Uri imageUri) throws IOException {
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(currentActivity.getContentResolver(), imageUri);
        return convertBitmapToBase64(bitmap);
    }

    private static String convertBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null, cannot convert to Base64.");
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // 可以根據需要調整壓縮格式和品質
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private static void sendUnityMessage(String message) {
        if (unityGameObject != null && unityCallbackMethod != null) {
            UnityPlayer.UnitySendMessage(unityGameObject, unityCallbackMethod, message);
            Log.d(TAG, "Sending message to Unity: " + message);
        } else {
            Log.e(TAG, "Unity GameObject or Callback method not set.");
        }
    }
}
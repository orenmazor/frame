package com.earendilworks.photostream;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int REQUEST_READ_STORAGE = 42;
    private static final String PHOTO_DIR = "/storage/emulated/0/Pictures/Frame";
    private static final long PAN_MS = 20_000L;
    private static final long FADE_MS = 1_200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> photos = new ArrayList<>();
    private final Random random = new Random();

    private FrameLayout root;
    private ImageView imageA;
    private ImageView imageB;
    private TextView message;
    private TextView clockText;
    private TextView photoDateText;
    private boolean showingA = true;
    private int photoIndex = 0;
    private AnimatorSet animationA;
    private AnimatorSet animationB;

    private final Runnable nextSlide = new Runnable() {
        @Override public void run() {
            showNextPhoto();
            handler.postDelayed(this, PAN_MS);
        }
    };

    private final Runnable updateClock = new Runnable() {
        @Override public void run() {
            clockText.setText(new SimpleDateFormat("h:mm a", Locale.US).format(new Date()));
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        goFullscreen();
        buildUi();

        if (needsStoragePermission()) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_STORAGE);
        } else {
            startSlideshow();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        goFullscreen();
        handler.removeCallbacks(updateClock);
        updateClock.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(nextSlide);
        handler.removeCallbacks(updateClock);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSlideshow();
        } else {
            showMessage("Storage permission is needed to read\n" + PHOTO_DIR);
        }
    }

    private void goFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        imageA = makeImageView();
        imageB = makeImageView();
        imageB.setAlpha(0f);

        photoDateText = makeOverlayText(36f, Gravity.LEFT | Gravity.TOP);
        photoDateText.setBackgroundColor(Color.argb(140, 0, 0, 0));
        photoDateText.setPadding(32, 18, 32, 18);
        clockText = makeOverlayText(20f, Gravity.RIGHT | Gravity.BOTTOM);

        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(22f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(40, 40, 40, 40);
        message.setShadowLayer(8f, 2f, 2f, Color.BLACK);

        root.addView(imageA);
        root.addView(imageB);
        root.addView(photoDateText, overlayParams(Gravity.LEFT | Gravity.TOP));
        root.addView(clockText, overlayParams(Gravity.RIGHT | Gravity.BOTTOM));
        root.addView(message, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private TextView makeOverlayText(float size, int gravity) {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(gravity);
        view.setPadding(28, 20, 28, 20);
        view.setShadowLayer(12f, 3f, 3f, Color.BLACK);
        return view;
    }

    private FrameLayout.LayoutParams overlayParams(int gravity) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = gravity;
        params.setMargins(18, 18, 18, 18);
        return params;
    }

    private ImageView makeImageView() {
        ImageView view = new ImageView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setAdjustViewBounds(false);
        rootAddDefaults(view);
        return view;
    }

    private void rootAddDefaults(ImageView view) {
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    private void startSlideshow() {
        handler.removeCallbacks(nextSlide);
        loadPhotos();
        if (photos.isEmpty()) {
            showMessage("No photos found in\n" + PHOTO_DIR + "\n\nSupported: jpg, jpeg, png, webp, bmp, gif");
            return;
        }

        Collections.shuffle(photos);
        photoIndex = 0;
        message.setVisibility(View.GONE);
        showNextPhoto();
        handler.postDelayed(nextSlide, PAN_MS);
    }

    private void loadPhotos() {
        photos.clear();
        File dir = new File(PHOTO_DIR);
        collectPhotos(dir, photos);
        Collections.sort(photos, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath());
            }
        });
    }

    private void collectPhotos(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectPhotos(file, out);
            } else if (isPhoto(file)) {
                out.add(file);
            }
        }
    }

    private boolean isPhoto(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    private void showNextPhoto() {
        if (photos.isEmpty()) return;
        if (photoIndex >= photos.size()) {
            Collections.shuffle(photos);
            photoIndex = 0;
        }

        File photo = photos.get(photoIndex++);
        ImageView incoming = showingA ? imageB : imageA;
        ImageView outgoing = showingA ? imageA : imageB;

        Bitmap bitmap = decodeOrientedBitmap(photo);
        if (bitmap == null) {
            showNextPhoto();
            return;
        }

        cancelAnimations(incoming, outgoing);

        incoming.setImageBitmap(bitmap);
        configureImageViewForBitmap(incoming, bitmap);
        incoming.setAlpha(0f);
        photoDateText.setText(photoDateLabel(photo));

        incoming.bringToFront();
        photoDateText.bringToFront();
        clockText.bringToFront();
        message.bringToFront();

        AnimatorSet incomingAnimation = buildIncomingAnimation(incoming);
        if (incoming == imageA) {
            animationA = incomingAnimation;
        } else {
            animationB = incomingAnimation;
        }
        incomingAnimation.start();

        outgoing.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        showingA = !showingA;
    }

    private void cancelAnimations(ImageView incoming, ImageView outgoing) {
        AnimatorSet incomingSet = incoming == imageA ? animationA : animationB;
        AnimatorSet outgoingSet = outgoing == imageA ? animationA : animationB;
        if (incomingSet != null) incomingSet.cancel();
        if (outgoingSet != null) outgoingSet.cancel();
        incoming.animate().cancel();
        outgoing.animate().cancel();
    }

    private void configureImageViewForBitmap(ImageView view, Bitmap bitmap) {
        int rootWidth = Math.max(root.getWidth(), getResources().getDisplayMetrics().widthPixels);
        int rootHeight = Math.max(root.getHeight(), getResources().getDisplayMetrics().heightPixels);
        if (rootWidth <= 0 || rootHeight <= 0 || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            rootAddDefaults(view);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return;
        }

        float imageAspect = (float) bitmap.getWidth() / (float) bitmap.getHeight();
        float screenAspect = (float) rootWidth / (float) rootHeight;
        int imageViewWidth;
        int imageViewHeight;

        if (imageAspect > screenAspect) {
            imageViewHeight = rootHeight;
            imageViewWidth = (int) Math.ceil(rootHeight * imageAspect);
        } else {
            imageViewWidth = rootWidth;
            imageViewHeight = (int) Math.ceil(rootWidth / imageAspect);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(imageViewWidth, imageViewHeight);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        view.setLayoutParams(params);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
    }

    private AnimatorSet buildIncomingAnimation(ImageView view) {
        int rootWidth = Math.max(root.getWidth(), getResources().getDisplayMetrics().widthPixels);
        int rootHeight = Math.max(root.getHeight(), getResources().getDisplayMetrics().heightPixels);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        float overflowX = Math.max(0f, params.width - rootWidth);
        float overflowY = Math.max(0f, params.height - rootHeight);

        boolean panHorizontally = overflowX > overflowY;
        boolean reverse = random.nextBoolean();
        float startX = 0f;
        float endX = 0f;
        float startY = 0f;
        float endY = 0f;

        if (panHorizontally && overflowX > 0f) {
            startX = reverse ? -overflowX : 0f;
            endX = reverse ? 0f : -overflowX;
            float smallDriftY = rootHeight * 0.015f;
            startY = randomBetween(-smallDriftY, smallDriftY);
            endY = -startY;
        } else if (overflowY > 0f) {
            startY = reverse ? -overflowY : 0f;
            endY = reverse ? 0f : -overflowY;
            float smallDriftX = rootWidth * 0.015f;
            startX = randomBetween(-smallDriftX, smallDriftX);
            endX = -startX;
        } else {
            float maxPanX = rootWidth * 0.035f;
            float maxPanY = rootHeight * 0.035f;
            startX = randomBetween(-maxPanX, maxPanX);
            startY = randomBetween(-maxPanY, maxPanY);
            endX = -startX;
            endY = -startY;
        }

        float startScale = 1.01f;
        float endScale = 1.05f;

        view.setPivotX(params.width / 2f);
        view.setPivotY(params.height / 2f);
        view.setTranslationX(startX);
        view.setTranslationY(startY);
        view.setScaleX(startScale);
        view.setScaleY(startScale);

        ObjectAnimator fade = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        fade.setDuration(FADE_MS);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", startScale, endScale);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", startScale, endScale);
        ObjectAnimator translationX = ObjectAnimator.ofFloat(view, "translationX", startX, endX);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(view, "translationY", startY, endY);
        scaleX.setDuration(PAN_MS);
        scaleY.setDuration(PAN_MS);
        translationX.setDuration(PAN_MS);
        translationY.setDuration(PAN_MS);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.playTogether(fade, scaleX, scaleY, translationX, translationY);
        return set;
    }

    private float randomBetween(float min, float max) {
        if (max <= min) return 0f;
        return min + random.nextFloat() * (max - min);
    }

    private Bitmap decodeOrientedBitmap(File photo) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photo.getAbsolutePath(), bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(photo.getAbsolutePath(), options);
        if (bitmap == null) return null;

        int orientation = readExifOrientation(photo);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            default:
                return bitmap;
        }

        try {
            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (oriented != bitmap) bitmap.recycle();
            return oriented;
        } catch (OutOfMemoryError ignored) {
            return bitmap;
        }
    }

    private int sampleSize(int width, int height) {
        int targetWidth = Math.max(root.getWidth() * 2, 1920);
        int targetHeight = Math.max(root.getHeight() * 2, 1080);
        int sample = 1;
        while (width / sample > targetWidth * 2 || height / sample > targetHeight * 2) {
            sample *= 2;
        }
        return sample;
    }

    private int readExifOrientation(File photo) {
        try {
            ExifInterface exif = new ExifInterface(photo.getAbsolutePath());
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private String photoDateLabel(File photo) {
        String exifDate = readExifDate(photo);
        if (exifDate != null) {
            return "📸 " + exifDate;
        }
        return "";
    }

    private String readExifDate(File photo) {
        try {
            ExifInterface exif = new ExifInterface(photo.getAbsolutePath());
            String raw = exif.getAttribute("DateTimeOriginal");
            if (raw == null) raw = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (raw == null) return null;

            Date parsed = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw);
            if (parsed == null) return raw;
            return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed);
        } catch (IOException ignored) {
            return null;
        } catch (ParseException ignored) {
            return null;
        }
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        message.bringToFront();
    }
}

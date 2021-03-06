package com.mathrithms.mShare.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.mathrithms.mShare.GlideApp;
import com.mathrithms.mShare.activity.WelcomeActivity;
import com.mathrithms.mShare.config.AppConfig;
import com.mathrithms.mShare.database.AccessDatabase;
import com.mathrithms.mShare.dialog.ProfileEditorDialog;
import com.mathrithms.mShare.dialog.RationalePermissionRequest;
import com.mathrithms.mShare.service.CommunicationService;
import com.mathrithms.mShare.service.DeviceScannerService;
import com.mathrithms.mShare.service.WorkerService;
import com.mathrithms.mShare.util.AppUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("rawtypes")
public abstract class Activity extends AppCompatActivity
{
    public static final int REQUEST_PICK_PROFILE_PHOTO = 1000;
    private final List<WorkerService.RunningTask> mAttachedTasks = new ArrayList<>();
    private AlertDialog mOngoingRequest;
    private boolean mSkipPermissionRequest = false;
    private boolean mWelcomePageDisallowed = false;
    private boolean mExitAppRequested = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (!hasIntroductionShown() && !mWelcomePageDisallowed) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        } else if (!AppUtils.checkRunningConditions(this)) {
            if (!mSkipPermissionRequest)
                requestRequiredPermissions(true);
        } else
            AppUtils.startForegroundService(this, new Intent(this, CommunicationService.class)
                    .setAction(CommunicationService.ACTION_SERVICE_STATUS)
                    .putExtra(CommunicationService.EXTRA_STATUS_STARTED, true));

        mExitAppRequested = false;
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (!mExitAppRequested)
            AppUtils.startForegroundService(this, new Intent(this, CommunicationService.class)
                    .setAction(CommunicationService.ACTION_SERVICE_STATUS)
                    .putExtra(CommunicationService.EXTRA_STATUS_STARTED, false));
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        synchronized (mAttachedTasks) {
            for (WorkerService.RunningTask task : mAttachedTasks) {
                task.detachAnchor();
            }

            mAttachedTasks.clear();
        }
    }

    protected void onPreviousRunningTask(@Nullable WorkerService.RunningTask task)
    {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (AppUtils.checkRunningConditions(this))
            AppUtils.startForegroundService(this, new Intent(this, CommunicationService.class));
        else
            requestRequiredPermissions(!mSkipPermissionRequest);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_PROFILE_PHOTO)
            if (resultCode == RESULT_OK && data != null) {
                Uri chosenImageUri = data.getData();

                if (chosenImageUri != null) {
                    GlideApp.with(this)
                            .load(chosenImageUri)
                            .centerCrop()
                            .override(200, 200)
                            .into(new Target<Drawable>()
                            {
                                @Override
                                public void onLoadStarted(@Nullable Drawable placeholder)
                                {

                                }

                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable)
                                {

                                }

                                @Override
                                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition)
                                {
                                    try {
                                        Bitmap bitmap = Bitmap.createBitmap(AppConfig.PHOTO_SCALE_FACTOR, AppConfig.PHOTO_SCALE_FACTOR, Bitmap.Config.ARGB_8888);
                                        Canvas canvas = new Canvas(bitmap);
                                        FileOutputStream outputStream = openFileOutput("profilePicture", MODE_PRIVATE);

                                        resource.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                                        resource.draw(canvas);
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

                                        outputStream.close();

                                        notifyUserProfileChanged();
                                    } catch (Exception error) {
                                        error.printStackTrace();
                                    }
                                }

                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder)
                                {

                                }

                                @Override
                                public void getSize(@NonNull SizeReadyCallback cb)
                                {

                                }

                                @Override
                                public void removeCallback(@NonNull SizeReadyCallback cb)
                                {

                                }

                                @Nullable
                                @Override
                                public Request getRequest()
                                {
                                    return null;
                                }

                                @Override
                                public void setRequest(@Nullable Request request)
                                {

                                }

                                @Override
                                public void onStart()
                                {

                                }

                                @Override
                                public void onStop()
                                {

                                }

                                @Override
                                public void onDestroy()
                                {

                                }
                            });
                }
            }
    }

    public void onUserProfileUpdated()
    {

    }

    public void attachRunningTask(WorkerService.RunningTask task)
    {
        synchronized (mAttachedTasks) {
            mAttachedTasks.add(task);
        }
    }

    public boolean checkForTasks()
    {
        ServiceConnection serviceConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service)
            {
                WorkerService workerService = ((WorkerService.LocalBinder) service).getService();

                WorkerService.RunningTask task = workerService
                        .findTaskByHash(WorkerService.intentHash(getIntent()));

                onPreviousRunningTask(task);

                if (task != null)
                    synchronized (mAttachedTasks) {
                        attachRunningTask(task);
                    }

                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name)
            {

            }
        };

        return bindService(new Intent(Activity.this, WorkerService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Exits app closing all the active services and connections.
     * This will also prevent this activity from notifying {@link CommunicationService}
     * as the user leaves to the state of {@link Activity#onPause()}
     */
    public void exitApp()
    {
        mExitAppRequested = true;

        stopService(new Intent(this, CommunicationService.class));
        stopService(new Intent(this, DeviceScannerService.class));
        stopService(new Intent(this, WorkerService.class));

        finish();
    }

    public AccessDatabase getDatabase()
    {
        return AppUtils.getDatabase(this);
    }

    protected SharedPreferences getDefaultPreferences()
    {
        return AppUtils.getDefaultPreferences(this);
    }

    public boolean hasIntroductionShown()
    {
        return getDefaultPreferences().
                getBoolean("introduction_shown", false);
    }


    public void loadProfilePictureInto(String deviceName, ImageView imageView)
    {
        try {
            FileInputStream inputStream = openFileInput("profilePicture");
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            GlideApp.with(this)
                    .load(bitmap)
                    .circleCrop()
                    .into(imageView);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            imageView.setImageDrawable(AppUtils.getDefaultIconBuilder(this).buildRound(deviceName));
        }
    }

    public void notifyUserProfileChanged()
    {
        if (!isFinishing())
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    onUserProfileUpdated();
                }
            });
    }

    public void setSkipPermissionRequest(boolean skip)
    {
        mSkipPermissionRequest = skip;
    }

    public void requestProfilePictureChange()
    {
        startActivityForResult(new Intent(Intent.ACTION_PICK).setType("image/*"), REQUEST_PICK_PROFILE_PHOTO);
    }

    public boolean requestRequiredPermissions(boolean killActivityOtherwise)
    {
        if (mOngoingRequest != null && mOngoingRequest.isShowing())
            return false;

        for (RationalePermissionRequest.PermissionRequest request : AppUtils.getRequiredPermissions(this))
            if ((mOngoingRequest = RationalePermissionRequest.requestIfNecessary(this, request, killActivityOtherwise)) != null)
                return false;

        return true;
    }

    public void setWelcomePageDisallowed(boolean disallow)
    {
        mWelcomePageDisallowed = disallow;
    }

    public void startProfileEditor()
    {
        new ProfileEditorDialog(this).show();
    }

    public interface OnBackPressedListener
    {

        boolean onBackPressed();
    }

    public interface OnPreloadArgumentWatcher
    {
        Bundle passPreLoadingArguments();
    }
}
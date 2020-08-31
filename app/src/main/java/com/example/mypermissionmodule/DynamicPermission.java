package com.example.mypermissionmodule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DynamicPermission
{
    List<String> permissionList = new ArrayList<String>();
    String[] copyList;
    private Context context;
    private int REQUEST_PERMISSION = 100;
    private String TAG = getClass().getSimpleName();
    PermissionListener permissionListener;
    OnActivityListener onActivityListener;


    public DynamicPermission(@Nullable String[] permissionList, @NonNull Context context)
    {
        this.permissionList = Arrays.asList(permissionList);
        this.context = context;
    }
    public DynamicPermission(@NonNull Context context)
    {
        this.context = context;
    }

    public void setREQUEST_PERMISSION(int REQUEST_PERMISSION){
        this.REQUEST_PERMISSION = REQUEST_PERMISSION;
    }

    public void putPermissionList(@NonNull String permission){
        permissionList.add(permission);
    }
    public void checkPermission(){
        copyList = Arrays.copyOf(permissionList.toArray(new String[permissionList.size()]),permissionList.size());
        if(needPermissionForBlocking(context)){
            if(permissionList.size() <= 0) Toast.makeText(context, "요청권한이 없습니다.", Toast.LENGTH_SHORT).show();
            else{
                if(permissionCheck())permissionListener.onSuccess();
            }

        }else Toast.makeText(context, "권한을 획득할 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    private boolean permissionCheck(){
        boolean isPermission = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return isPermission;

        for (int i = 0; i < permissionList.size(); i++) {
            if (context.checkSelfPermission(permissionList.get(i)) != PackageManager.PERMISSION_GRANTED) isPermission = false;
        }
        if (isPermission == false) {
            ((Activity)context).requestPermissions(permissionList.toArray(new String[permissionList.size()]), REQUEST_PERMISSION);
        }
        return isPermission;
    }

    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data){
        Log.d(TAG,"[onActivityResult] requestCode = "+requestCode);
        if(permissionCheck())this.onActivityListener.onSuccess();
            else this.onActivityListener.onFailed();
    }
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG,"[onRequestPermissionResult] requestCode = "+requestCode);
        boolean isPermitted = true;
        boolean isSettingNeed = false;
        if (requestCode == REQUEST_PERMISSION && grantResults.length == permissions.length) {
            for (int i = 0; i < permissions.length; i++) {
                Log.d(TAG,"[onRequestPermissionResult] requestPermission = "+permissions[i]);

                if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                {
                    if(!((Activity)context).shouldShowRequestPermissionRationale(permissions[i])){
                        Log.d(TAG,"[onRequestPermissionResult] shouldShowRequestPermissionRationale = "+permissions[i]);
                        int finalI = i;
                        //전체 리스트에서
                        Object[] permissionStream = Arrays.stream(copyList).filter(permission -> !permissions[finalI].equals(permission)).toArray();
                        copyList = Arrays.copyOf(permissionStream,permissionStream.length,String[].class);
                        if(copyList.length <1){
                            isSettingNeed = true;
                        }
                        for (String permission : copyList){
                            Log.d(TAG,"[onRequestPermissionResult] newPermissionString = "+permission);
                        }
                    }
                    isPermitted = false;
                    Log.d(TAG,"[onRequestPermissionResult] DeniedPermission" +
                            " = "+permissions[i]);
                }else{
                    int finalI = i;
                    Log.d(TAG,"[onRequestPermissionResult] grantResults = "+permissions[i]);
                    Object[] permissionStream = Arrays.stream(copyList).filter(permission -> !permissions[finalI].equals(permission)).toArray();
                    copyList = Arrays.copyOf(permissionStream,permissionStream.length,String[].class);
                    if(!isPermitted){
                        if(copyList.length < 1) isSettingNeed = true;
                    }
                }
            }
            if(isSettingNeed){
                makeDialog("앱 실행 권한","모든 권한을 허가하지 않으면 앱을 실행할 수 없습니다.","세팅 가기","앱 종료",
                        (dialog, which) -> {
                            Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                            ((Activity)context).startActivityForResult(overlayIntent,100);
                        },(dialog, which) -> ((Activity)context).finish()).show(); //여기서 세팅 다이얼로그
                return;
            }else if(!isPermitted){
                //만일 mPermissionList가 size가 0과 같거나 작아지면,
                makeDialog("앱 실행 권한","모든 권한을 허가하지 않으면 앱을 실행할 수 없습니다.","권한 실행","앱 종료",
                        (dialog, which) -> {((Activity)context).requestPermissions(permissionList.toArray(new String[permissionList.size()]),REQUEST_PERMISSION);dialog.dismiss();},(dialog, which) -> {dialog.dismiss();((Activity)context).finish();}).show();
            }
        }
    }

    public boolean needPermissionForBlocking(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            return (mode != AppOpsManager.MODE_ALLOWED);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private Dialog makeDialog(String title, String message, String positiveBtnMessage, String negativeBtnMessage, DialogInterface.OnClickListener positive, DialogInterface.OnClickListener negative){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setMessage(message);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setNegativeButton(positiveBtnMessage,positive);
        alertDialogBuilder.setPositiveButton(negativeBtnMessage,negative);
        return alertDialogBuilder.create();
    }

    public void permissionDialog(String message, String intentAction){
        if(!Settings.canDrawOverlays(context)){
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setTitle("권한 요청");
            alertDialogBuilder.setMessage(message);
            alertDialogBuilder.setCancelable(false);
            alertDialogBuilder.setNegativeButton("건너뛰기", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            alertDialogBuilder.setPositiveButton("권한실행", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent overlayIntent = new Intent(intentAction,
                            Uri.parse("package:"+context.getPackageName()));
                    context.startActivity(overlayIntent);
                }
            });
            alertDialogBuilder.show();
        }
    }

    public interface PermissionListener{
        void onSuccess();
        void onFailed();

    }

    public interface OnActivityListener{
        void onSuccess();
        void onFailed();
    }

    public void setPermissionListener(PermissionListener permissionListener){
        this.permissionListener = permissionListener;
    }

    public void setOnActivityListener(OnActivityListener onActivityListener){
        this.onActivityListener = onActivityListener;
    }
}


package com.tradereview.app.evidence;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Standalone test APK process does not contain the target APK's Kotlin dependencies. */
public final class TestImageProvider extends ContentProvider {
    public static Uri uri() { return uri("good.png"); }
    public static Uri uri(String name) { return Uri.parse("content://com.tradereview.app.test.images/" + name); }
    @Override public boolean onCreate() { return true; }
    private File fixture(String name) throws FileNotFoundException {
        if (!Arrays.asList("good.png", "second.png", "gps.jpg", "odd.png", "ocr.png", "bad.jpg", "huge.png", "missing.png", "denied.png").contains(name)) throw new FileNotFoundException("Unknown fixture");
        if (name.equals("denied.png")) throw new SecurityException("Synthetic revoked permission");
        if (name.equals("missing.png")) throw new FileNotFoundException("Synthetic missing image");
        File root = new File(getContext().getCacheDir(), "synthetic-images"); root.mkdirs();
        File file = new File(root, name); if (file.exists()) return file;
        try {
            if (name.equals("bad.jpg")) { try (FileOutputStream out = new FileOutputStream(file)) { out.write(new byte[]{1,2,3}); } }
            else if (name.equals("huge.png")) { try (FileOutputStream out = new FileOutputStream(file)) { for (int i=0; i<6; i++) out.write(new byte[1024*1024]); } }
            else if (name.equals("ocr.png")) {
                Bitmap bitmap=Bitmap.createBitmap(1280,1440,Bitmap.Config.ARGB_8888);
                try {
                    Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
                    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(48f);
                    String[] lines={"交易日期: 2024-02-29","市场: 国内期货","品种: 螺纹钢","合约: rb2701","方向: 做空","币种: CNY",
                        "Entry price: 3500.25","Exit price: 3490.125","Lots: 2.5","Fees: 3.25","Realized net P&L: -25.35","合成样本，不含个人信息"};
                    for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],40f,90f+i*100f,paint);
                    try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
                } finally {bitmap.recycle();}
            } else {
                Bitmap bitmap = Bitmap.createBitmap(name.equals("odd.png") ? 4097 : 640, name.equals("odd.png") ? 100 : 400, Bitmap.Config.ARGB_8888);
                try {
                    Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.DKGRAY); paint.setTextSize(27f);
                    canvas.drawText("合成截图：仅用于自动化测试",25f,50f,paint);
                    paint.setColor(Color.LTGRAY); paint.setStrokeWidth(1f);
                    for (int y=90; y<=360; y+=45) canvas.drawLine(20f,y,620f,y,paint);
                    for (int x=60; x<=580; x+=65) canvas.drawLine(x,80f,x,360f,paint);
                    paint.setColor(name.equals("second.png") ? Color.rgb(190,45,60) : Color.rgb(0,125,90)); paint.setStrokeWidth(5f);
                    float[] points={120f,180f,150f,240f,200f,285f,265f,310f};
                    for (int i=0; i<points.length-1; i++) canvas.drawLine(55f+i*75,390f-points[i],130f+i*75,390f-points[i+1],paint);
                    try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(name.endsWith("jpg") ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,90,out); }
                } finally { bitmap.recycle(); }
                if (name.equals("gps.jpg")) {
                    ExifInterface exif=new ExifInterface(file.getPath());
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,"1/1,0/1,0/1"); exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,"1/1,0/1,0/1"); exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,"E");
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6"); exif.saveAttributes();
                }
            }
        } catch (IOException error) { FileNotFoundException missing=new FileNotFoundException("Cannot create synthetic fixture"); missing.initCause(error); throw missing; }
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Read-only fixture");
        return ParcelFileDescriptor.open(fixture(uri.getLastPathSegment()),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return uri.getLastPathSegment().endsWith("jpg") ? "image/jpeg" : "image/png"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder) {
        try { File f=fixture(uri.getLastPathSegment());MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c; }
        catch (FileNotFoundException error) { throw new IllegalArgumentException(error); }
    }
    @Override public Bundle call(String method,String arg,Bundle extras) {
        if ("erase".equals(method) && Arrays.asList("gps.jpg","good.png","second.png").contains(arg)) new File(new File(getContext().getCacheDir(),"synthetic-images"),arg).delete();
        return null;
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException(); }
}

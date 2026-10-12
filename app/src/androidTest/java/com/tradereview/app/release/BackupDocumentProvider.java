package com.tradereview.app.release;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

/** Synthetic SAF fixture; standalone test-APK process has no target Kotlin runtime. */
public final class BackupDocumentProvider extends ContentProvider {
    public static Uri uri() { return uri("normal"); }
    public static Uri uri(String kind) {
        return Uri.parse("content://com.tradereview.app.test.v100backup/" + kind + "/" + UUID.randomUUID().toString().replace("-", ""));
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder) { return new MatrixCursor(new String[]{"_display_name","_size"}); }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException(); }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(uri.getPathSegments().size()!=2)throw new FileNotFoundException("Invalid synthetic document");
        String kind=uri.getPathSegments().get(0),id=uri.getPathSegments().get(1);
        if(!id.matches("[0-9a-f]{32}"))throw new FileNotFoundException("Invalid synthetic ID");
        File file=new File(getContext().getCacheDir(),"v100-document-"+id);
        if(mode.contains("w") && kind.equals("write-fail"))throw new FileNotFoundException("Synthetic output failure");
        if(mode.equals("r") && kind.equals("no-read"))throw new FileNotFoundException("Synthetic write-only provider");
        if(mode.equals("r") && kind.equals("corrupt")){
            try(RandomAccessFile value=new RandomAccessFile(file,"rw")) {
                int first=value.read();if(first<0)throw new IOException("Empty fixture");value.seek(0);value.write(first^1);
            }catch(IOException cause){FileNotFoundException failure=new FileNotFoundException("Cannot damage synthetic fixture");failure.initCause(cause);throw failure;}
        }
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode));
    }
}

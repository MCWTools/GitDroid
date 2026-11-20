package com.ghpusher.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Base64;

import androidx.activity.result.ActivityResult;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Storage Access Framework bridge: pick files/folders, stat/read/list for watching, write for pull. */
@CapacitorPlugin(name = "Saf")
public class SafPlugin extends Plugin {

    private static final int MAX_FILES = 20000;

    private void persist(Intent data, Uri uri) {
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContext().getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // some providers do not allow persistable grants; the grant still works for this session
        }
    }

    /* ------------------------------------------------------------------ pick files */
    @PluginMethod
    public void pickFiles(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, i, "filesResult");
    }

    @ActivityCallback
    private void filesResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            call.reject("cancelled");
            return;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        JSArray arr = new JSArray();
        for (Uri u : uris) {
            persist(data, u);
            JSObject o = new JSObject();
            o.put("uri", u.toString());
            o.put("name", displayName(u));
            arr.put(o);
        }
        JSObject res = new JSObject();
        res.put("files", arr);
        call.resolve(res);
    }

    private String displayName(Uri u) {
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(u, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String seg = u.getLastPathSegment();
        return seg == null ? "file" : seg;
    }

    /* ------------------------------------------------------------------ save one file (Save-as dialog) */
    @PluginMethod
    public void createDocument(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(call.getString("mime", "application/octet-stream"));
        i.putExtra(Intent.EXTRA_TITLE, call.getString("name", "file"));
        startActivityForResult(call, i, "createResult");
    }

    @ActivityCallback
    private void createResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.reject("cancelled");
            return;
        }
        try {
            Uri uri = data.getData();
            byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            OutputStream os = getContext().getContentResolver().openOutputStream(uri, "wt");
            if (os == null) { call.reject("cannot write"); return; }
            os.write(bytes);
            os.close();
            JSObject o = new JSObject();
            o.put("uri", uri.toString());
            call.resolve(o);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /* ------------------------------------------------------------------ pick directory */
    @PluginMethod
    public void pickDirectory(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, i, "treeResult");
    }

    @ActivityCallback
    private void treeResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.reject("cancelled");
            return;
        }
        Uri tree = data.getData();
        persist(data, tree);
        JSObject o = new JSObject();
        o.put("uri", tree.toString());
        o.put("name", tree.getLastPathSegment());
        call.resolve(o);
    }

    /* ------------------------------------------------------------------ stat / read */
    @PluginMethod
    public void stat(PluginCall call) {
        Cursor c = null;
        try {
            Uri u = Uri.parse(call.getString("uri"));
            c = getContext().getContentResolver().query(u, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null);
            JSObject o = new JSObject();
            if (c != null && c.moveToFirst()) {
                o.put("exists", true);
                o.put("name", c.getString(0));
                o.put("size", c.getLong(1));
                o.put("lastModified", c.getLong(2));
            } else {
                o.put("exists", false);
            }
            call.resolve(o);
        } catch (Exception e) {
            JSObject o = new JSObject();
            o.put("exists", false);
            call.resolve(o);
        } finally {
            if (c != null) c.close();
        }
    }

    @PluginMethod
    public void read(PluginCall call) {
        try {
            Uri u = Uri.parse(call.getString("uri"));
            InputStream is = getContext().getContentResolver().openInputStream(u);
            if (is == null) { call.reject("cannot open"); return; }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            JSObject o = new JSObject();
            o.put("data", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP));
            call.resolve(o);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /* ------------------------------------------------------------------ list a tree recursively */
    @PluginMethod
    public void listTree(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("uri"));
            JSArray out = new JSArray();
            int[] count = new int[]{0};
            walk(getContext().getContentResolver(), tree, DocumentsContract.getTreeDocumentId(tree), "", out, count);
            JSObject o = new JSObject();
            o.put("files", out);
            call.resolve(o);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    private void walk(ContentResolver cr, Uri tree, String docId, String rel, JSArray out, int[] count) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        List<String[]> dirs = new ArrayList<>();
        Cursor c = cr.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null);
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                String path = rel.isEmpty() ? name : rel + "/" + name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!".git".equals(name)) dirs.add(new String[]{id, path});
                } else {
                    if (count[0]++ >= MAX_FILES) continue;
                    JSObject f = new JSObject();
                    f.put("path", path);
                    f.put("size", c.getLong(3));
                    f.put("lastModified", c.getLong(4));
                    f.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                    out.put(f);
                }
            }
        } finally {
            c.close();
        }
        for (String[] d : dirs) walk(cr, tree, d[0], d[1], out, count);
    }

    /* ------------------------------------------------------------------ write into a tree (Pull) */
    private String findChild(ContentResolver cr, Uri tree, String parentDocId, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId);
        Cursor c = cr.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
        if (c == null) return null;
        try {
            while (c.moveToNext()) {
                if (name.equals(c.getString(1))) return c.getString(0);
            }
        } finally {
            c.close();
        }
        return null;
    }

    @PluginMethod
    public void writeFile(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("uri"));
            String path = call.getString("path", "");
            boolean overwrite = Boolean.TRUE.equals(call.getBoolean("overwrite", false));
            ContentResolver cr = getContext().getContentResolver();
            String[] parts = path.split("/");
            String parentId = DocumentsContract.getTreeDocumentId(tree);
            for (int k = 0; k < parts.length - 1; k++) {
                if (parts[k].isEmpty()) continue;
                String id = findChild(cr, tree, parentId, parts[k]);
                if (id == null) {
                    Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId);
                    Uri made = DocumentsContract.createDocument(cr, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, parts[k]);
                    if (made == null) { call.reject("cannot create folder " + parts[k]); return; }
                    id = DocumentsContract.getDocumentId(made);
                }
                parentId = id;
            }
            String name = parts[parts.length - 1];
            String existing = findChild(cr, tree, parentId, name);
            Uri target;
            if (existing != null) {
                if (!overwrite) {
                    JSObject o = new JSObject();
                    o.put("status", "skipped");
                    call.resolve(o);
                    return;
                }
                target = DocumentsContract.buildDocumentUriUsingTree(tree, existing);
            } else {
                Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId);
                target = DocumentsContract.createDocument(cr, parentUri, "application/octet-stream", name);
                if (target == null) { call.reject("cannot create " + name); return; }
            }
            OutputStream os = cr.openOutputStream(target, "wt");
            if (os == null) { call.reject("cannot write " + name); return; }
            os.write(Base64.decode(call.getString("data", ""), Base64.DEFAULT));
            os.close();
            JSObject o = new JSObject();
            o.put("status", "written");
            call.resolve(o);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /* ------------------------------------------------------------------ keep-alive service */
    @PluginMethod
    public void startService(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && getActivity() != null
                    && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4242);
            }
            Intent i = new Intent(getContext(), KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
            else getContext().startService(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void stopService(PluginCall call) {
        try {
            getContext().stopService(new Intent(getContext(), KeepAliveService.class));
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }
}

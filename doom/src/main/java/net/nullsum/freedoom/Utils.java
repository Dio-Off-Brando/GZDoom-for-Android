package net.nullsum.freedoom;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Toast;

import com.beloko.touchcontrols.ActionInput;
import com.beloko.touchcontrols.ControlConfig;
import com.beloko.touchcontrols.ControlConfig.Type;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

;

public class Utils {
    public static void copyFreedoomFilesToSD(Activity responsibleActivity) {
        String iniFolderName = "/gzdoom_dev";
        String iniFileName = "zdoom.ini";
        String fullBaseDir = AppSettings.getQuakeFullDir();

        // Freedoom additions
        Utils.copyAsset(responsibleActivity, "freedoom1.wad", fullBaseDir);
        Utils.copyAsset(responsibleActivity, "freedoom2.wad", fullBaseDir);
        // Freedoom licence and credits
        Utils.copyAsset(responsibleActivity, "COPYING.txt", fullBaseDir);
        Utils.copyAsset(responsibleActivity, "CREDITS.txt", fullBaseDir);
        // copy a custom gzdoom iniFile to set midi device to fluidsynth
        File tester = new File(fullBaseDir + iniFolderName + "/" + iniFileName);
        if (!tester.exists()) {
            Log.d(LOG, "zdoom.ini file not present, copying custom one");
            Utils.copyAsset(responsibleActivity, iniFileName, fullBaseDir + iniFolderName);
        } else {
            Log.d(LOG, "zdoom.ini file is already present");
        }
    }

    static String LOG = "Utils";

    static public void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
        out.close();
    }

    static public void copyFile(InputStream in, OutputStream out,ProgressDialog pb) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
            pb.setProgress(pb.getProgress() + 1024);
        }
        out.close();
    }

    static public  void showDownloadDialog(final Activity act,String title,final String KEY,final String directory,final String file)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setMessage(title)
        .setCancelable(true)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Download stuff here
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public static String checkFiles(String basePath,String[] files_to_ceck)
    {
        File[] files = new File(basePath ).listFiles();
        boolean ok=true;

        String filesNotFound = "";

        String[] expected;
        expected = files_to_ceck;

        if (files == null)
            files = new File[0];

        if (files!=null)
        {
            for (File f: files)
            {
                Log.d(LOG,"FILES: " + f.toString());

            }

            for (String e: expected)
            {
                boolean found=false;
                for (File f: files)
                {
                    if (f.toString().toLowerCase().endsWith(e.toLowerCase()))
                        found = true;
                }
                if (!found)
                {
                    Log.d(LOG,"Didnt find " + e);
                    filesNotFound +=  e + "\n";
                    ok = false;
                }
            }
        }

        if (filesNotFound.contentEquals(""))
            return null;
        else
            return filesNotFound;

    }

    static public void copyPNGAssets(Context ctx,String dir) {
        String prefix = "";

        File d = new File(dir);
        if (!d.exists())
            d.mkdirs();

        AssetManager assetManager = ctx.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        for(String filename : files) {
            if (filename.endsWith("png") && filename.startsWith(prefix)){
                InputStream in = null;
                OutputStream out = null;
                //Log.d("test","file = " + filename);
                try {
                    in = assetManager.open(filename);
                    out = new FileOutputStream(dir + "/" + filename.substring(prefix.length()));
                    copyFile(in, out);
                    in.close();
                    in = null;
                    out.flush();
                    out.close();
                    out = null;
                } catch(IOException e) {
                    Log.e("tag", "Failed to copy asset file: " + filename, e);
                }
            }
        }
    }

    public static void ExtractAsset(Context ctx,String file, String dest)
    {
        ExtractAsset.ctx = ctx;
        new ExtractAsset().execute(file,dest);
    }

    static private class ExtractAsset extends AsyncTask<String, Integer, Long> {

        private ProgressDialog progressBar;
        String errorstring= null;
        static Context ctx;

        @Override
        protected void onPreExecute() {
            progressBar = new ProgressDialog(ctx);
            progressBar.setMessage("Extracting files..");
            progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressBar.setCancelable(false);
            progressBar.show();
        }

        int getTotalZipSize(String file)
        {
            int ret = 0;
            try {
                ZipFile zf = new ZipFile(file);
                Enumeration e = zf.entries();
                while (e.hasMoreElements()) {
                    ZipEntry ze = (ZipEntry) e.nextElement();
                    String name = ze.getName();

                    ret += ze.getSize();
                    long compressedSize = ze.getCompressedSize();
                }
            } catch (IOException ex) {
                System.err.println(ex);
            }
            return ret;
        }

        protected Long doInBackground(String... info) {

            String file = info[0];
            String basePath = info[1];

            boolean isLocal = false;

            progressBar.setProgress(0);

            try
            {

                BufferedInputStream in = null;
                FileOutputStream fout = null;


                AssetManager assetManager = ctx.getAssets();
                InputStream ins = assetManager.open(file);

                progressBar.setMax(1024*1024*5); //TODO FIX ME

                in = new BufferedInputStream(ins);

                if (file.endsWith(".zip"))
                {
                    ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in));
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if(entry.isDirectory()) {
                            // Assume directories are stored parents first then children.
                            System.err.println("Extracting directory: " + entry.getName());
                            // This is not robust, just for demonstration purposes.
                            (new File(basePath, entry.getName())).mkdirs();
                            continue;
                        }
                        Log.d(LOG,"Extracting file: " + entry.getName());
                        (new File(basePath, entry.getName())).getParentFile().mkdirs();
                        BufferedInputStream zin = new BufferedInputStream(zis);
                        OutputStream out =  new FileOutputStream(new File(basePath, entry.getName()));
                        Utils.copyFile(zin,out,progressBar);
                    }
                }
                else
                {
                    File outZipFile = new File(basePath,"temp.zip");

                    fout = new FileOutputStream(outZipFile);
                    byte data[] = new byte[1024];
                    int count;
                    while ((count = in.read(data, 0, 1024)) != -1)
                    {
                        fout.write(data, 0, count);
                        progressBar.setProgress(progressBar.getProgress() + count);
                    }
                    in.close();
                    fout.close();

                    outZipFile.renameTo(new File(basePath , file));
                    return 0l;
                }

            } catch (IOException e) {
                errorstring = e.toString();
                return 1l;
            }

            return 0l;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Long result) {
            progressBar.dismiss();
            if (errorstring!=null)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setMessage("Error accessing server: " + errorstring)
                        .setCancelable(true)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });

                builder.show();
            }
        }
    }

    static public String[] creatArgs(String appArgs)
    {
        ArrayList<String> a = new ArrayList<String>(Arrays.asList(appArgs.split(" ")));

        Iterator<String> iter = a.iterator();
        while (iter.hasNext()) {
            if (iter.next().contentEquals("")) {
                iter.remove();
            }
        }

        return a.toArray(new String[a.size()]);
    }


    public static void expand(final View v) {
        v.measure(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        final int targtetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? LayoutParams.WRAP_CONTENT
                                : (int)(targtetHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(targtetHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    public static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if(interpolatedTime == 1){
                    v.setVisibility(View.GONE);
                }else{
                    v.getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration((int)(initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }


    static final int BUFFER_SIZE = 1024;

    static public String getLogCat() {
        String[] logcatArgs = new String[] {"logcat", "-d", "-v", "time"};

        Process logcatProc = null;
        try {
            logcatProc = Runtime.getRuntime().exec(logcatArgs);
        }
        catch (IOException e) {
            return null;
        }

        BufferedReader reader = null;
        String response = null;
        try {
            String separator = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append(separator);
            }
            response = sb.toString();
        }
        catch (IOException e) {
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {}
            }
        }

        return response;
    }


    static public void copyAsset(Context ctx,String file,String destdir) {
        AssetManager assetManager = ctx.getAssets();

        InputStream in = null;
        OutputStream out = null;

        try {
            in = assetManager.open(file);
            out = new FileOutputStream(destdir + "/" + file);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        }
        catch(IOException e) {
            Log.e("tag", "Failed to copy asset file: " + file);
            e.printStackTrace();
        }
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
            int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static void loadArgs(Context ctx,ArrayList<String> args)
    {
        File cacheDir = ctx.getFilesDir();

        FileInputStream fis = null;
        ObjectInputStream in = null;
        try
        {
            fis = new FileInputStream(new File(cacheDir,"args_hist.dat"));
            in = new ObjectInputStream(fis);
            ArrayList<String> argsHistory = (ArrayList<String>)in.readObject();
            args.clear();
            args.addAll(argsHistory);
            in.close();
            return;
        }
        catch(IOException ex)
        {

        }
        catch(ClassNotFoundException ex)
        {

        }
        //failed load, load default
        args.clear();
    }


    public static void saveArgs(Context ctx,ArrayList<String> args)
    {
        File cacheDir = ctx.getFilesDir();

        if (!cacheDir.exists())
            cacheDir.mkdirs();

        FileOutputStream fos = null;
        ObjectOutputStream out = null;
        try
        {
            fos = new FileOutputStream(new File(cacheDir,"args_hist.dat"));
            out = new ObjectOutputStream(fos);
            out.writeObject(args);
            out.close();
        }
        catch(IOException ex)
        {
            Toast.makeText(ctx,"Error saving args History list: " + ex.toString(), Toast.LENGTH_LONG).show();
        }
    }

    public static void setImmersionMode(final Activity act)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            if (AppSettings.immersionMode)
            {
                act.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY );

                View decorView = act.getWindow().getDecorView();
                decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        Log.d(LOG,"onSystemUiVisibilityChange");

                        act.getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                                | View.SYSTEM_UI_FLAG_IMMERSIVE
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY );

                    }
                });
            }
        }
    }

    public static void onWindowFocusChanged(final Activity act,final boolean hasFocus)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            if (AppSettings.immersionMode)
            {
                Handler handler = new Handler();

                handler.postDelayed(new Runnable() {
                    public void run() {

                        if (hasFocus) {
                            act.getWindow().getDecorView().setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                        }
                    }

                }, 2000);
            }
        }
    }

    public static ArrayList<ActionInput> getGameGamepadConfig()
    {
        ArrayList<ActionInput> actions = new ArrayList<ActionInput>();

        actions.add(new ActionInput("analog_look_pitch","Look Up/Look Down",ControlConfig.ACTION_ANALOG_PITCH,Type.ANALOG));
        actions.add(new ActionInput("analog_look_yaw","Look Left/Look Right",ControlConfig.ACTION_ANALOG_YAW,Type.ANALOG));
        actions.add(new ActionInput("analog_move_fwd","Forward/Back", ControlConfig.ACTION_ANALOG_FWD, Type.ANALOG));
        actions.add(new ActionInput("analog_move_strafe","Strafe",ControlConfig.ACTION_ANALOG_STRAFE,Type.ANALOG));
        actions.add(new ActionInput("attack","Attack",ControlConfig.PORT_ACT_ATTACK,Type.BUTTON));
        actions.add(new ActionInput("attack_alt","Alt Attack (GZ)",ControlConfig.PORT_ACT_ALT_ATTACK,Type.BUTTON));
        actions.add(new ActionInput("back","Move Backwards",ControlConfig.PORT_ACT_BACK,Type.BUTTON));
        actions.add(new ActionInput("crouch","Crouch (GZ)",ControlConfig.PORT_ACT_DOWN,Type.BUTTON));
        actions.add(new ActionInput("custom_0","Custom A (GZ)",ControlConfig.PORT_ACT_CUSTOM_0,Type.BUTTON));
        actions.add(new ActionInput("custom_1","Custom B (GZ)",ControlConfig.PORT_ACT_CUSTOM_1,Type.BUTTON));
        actions.add(new ActionInput("custom_2","Custom C (GZ)",ControlConfig.PORT_ACT_CUSTOM_2,Type.BUTTON));
        actions.add(new ActionInput("custom_3","Custom D (GZ)",ControlConfig.PORT_ACT_CUSTOM_3,Type.BUTTON));
        actions.add(new ActionInput("custom_4","Custom E (GZ)",ControlConfig.PORT_ACT_CUSTOM_4,Type.BUTTON));
        actions.add(new ActionInput("custom_5","Custom F (GZ)",ControlConfig.PORT_ACT_CUSTOM_5,Type.BUTTON));
        actions.add(new ActionInput("fly_down","Fly Down",ControlConfig.PORT_ACT_FLY_DOWN,Type.BUTTON));
        actions.add(new ActionInput("fly_up","Fly Up",ControlConfig.PORT_ACT_FLY_UP,Type.BUTTON));
        actions.add(new ActionInput("fwd","Move Forward",ControlConfig.PORT_ACT_FWD,Type.BUTTON));
        actions.add(new ActionInput("inv_drop","Drop Item",ControlConfig.PORT_ACT_INVDROP,Type.BUTTON));
        actions.add(new ActionInput("inv_next","Next Item",ControlConfig.PORT_ACT_INVNEXT,Type.BUTTON));
        actions.add(new ActionInput("inv_prev","Prev Item",ControlConfig.PORT_ACT_INVPREV,Type.BUTTON));
        actions.add(new ActionInput("inv_use","Use Item",ControlConfig.PORT_ACT_INVUSE,Type.BUTTON));
        actions.add(new ActionInput("jump","Jump (GZ)",ControlConfig.PORT_ACT_JUMP,Type.BUTTON));
        actions.add(new ActionInput("left","Strafe Left",ControlConfig.PORT_ACT_MOVE_LEFT,Type.BUTTON));
        actions.add(new ActionInput("look_left","Look Left",ControlConfig.PORT_ACT_LEFT,Type.BUTTON));
        actions.add(new ActionInput("look_right","Look Right",ControlConfig.PORT_ACT_RIGHT,Type.BUTTON));
        actions.add(new ActionInput("map_down","Automap Down",ControlConfig.PORT_ACT_MAP_DOWN,Type.BUTTON));
        actions.add(new ActionInput("map_left","Automap Left",ControlConfig.PORT_ACT_MAP_LEFT,Type.BUTTON));
        actions.add(new ActionInput("map_right","Automap Right",ControlConfig.PORT_ACT_MAP_RIGHT,Type.BUTTON));
        actions.add(new ActionInput("map_show","Show Automap",ControlConfig.PORT_ACT_MAP,Type.BUTTON));
        actions.add(new ActionInput("map_up","Automap Up",ControlConfig.PORT_ACT_MAP_UP,Type.BUTTON));
        actions.add(new ActionInput("map_zoomin","Automap Zoomin",ControlConfig.PORT_ACT_MAP_ZOOM_IN,Type.BUTTON));
        actions.add(new ActionInput("map_zoomout","Automap Zoomout",ControlConfig.PORT_ACT_MAP_ZOOM_OUT,Type.BUTTON));
        actions.add(new ActionInput("menu_back","Menu Back",ControlConfig.MENU_BACK,Type.MENU));
        actions.add(new ActionInput("menu_down","Menu Down",ControlConfig.MENU_DOWN,Type.MENU));
        actions.add(new ActionInput("menu_left","Menu Left",ControlConfig.MENU_LEFT,Type.MENU));
        actions.add(new ActionInput("menu_right","Menu Right",ControlConfig.MENU_RIGHT,Type.MENU));
        actions.add(new ActionInput("menu_select","Menu Select",ControlConfig.MENU_SELECT,Type.MENU));
        actions.add(new ActionInput("menu_up","Menu Up",ControlConfig.MENU_UP,Type.MENU));
        actions.add(new ActionInput("next_weapon","Next Weapon",ControlConfig.PORT_ACT_NEXT_WEP,Type.BUTTON));
        actions.add(new ActionInput("prev_weapon","Previous Weapon",ControlConfig.PORT_ACT_PREV_WEP,Type.BUTTON));
        actions.add(new ActionInput("quick_load","Quick Load (GZ)",ControlConfig.PORT_ACT_QUICKLOAD,Type.BUTTON));
        actions.add(new ActionInput("quick_save","Quick Save (GZ)",ControlConfig.PORT_ACT_QUICKSAVE,Type.BUTTON));
        actions.add(new ActionInput("right","Strafe Right",ControlConfig.PORT_ACT_MOVE_RIGHT,Type.BUTTON));
        actions.add(new ActionInput("show_keys","Show Keys",ControlConfig.PORT_ACT_SHOW_KEYS,Type.BUTTON));
        actions.add(new ActionInput("show_weap","Show Stats/Weapons",ControlConfig.PORT_ACT_SHOW_WEAPONS,Type.BUTTON));
        actions.add(new ActionInput("speed","Run On",ControlConfig.PORT_ACT_SPEED,Type.BUTTON));
        actions.add(new ActionInput("strafe_on","Strafe On",ControlConfig.PORT_ACT_STRAFE,Type.BUTTON));
        actions.add(new ActionInput("use","Use/Open",ControlConfig.PORT_ACT_USE,Type.BUTTON));

        return actions;
    }
}

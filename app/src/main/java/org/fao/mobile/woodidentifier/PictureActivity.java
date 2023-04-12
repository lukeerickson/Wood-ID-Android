package org.fao.mobile.woodidentifier;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.apache.commons.io.FileUtils;
import org.fao.mobile.woodidentifier.utils.ModelHelper;
import org.fao.mobile.woodidentifier.utils.Utils;
import org.pytorch.Module;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class PictureActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView errorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture);

        Intent intent = getIntent();
        String label = intent.getStringExtra("label");
        this.imageView = findViewById(R.id.imageSample2);
        this.errorMessage = findViewById(R.id.errorMessage);


        Context ctx = getApplicationContext();
        final File filesDir = ctx.getFilesDir();
        //Log.i("Files Directory", String.valueOf(filesDir));

        String[] extensions = {"model.zip", "phone_database.json"};

        Collection files = FileUtils.listFiles(filesDir, extensions, false);
        //Log.i("Files", files.toString());

        for (Object o : files) {
            Log.i("Files", String.valueOf(o));
        }

        //String assetPath = Utils.assetFilePath(context,
          //      "model.zip");

        int uid = 0;

        List<String> mLines = readLine();
        for(int i = 0; i < mLines.size(); i++) {
            Log.i("Apple", "Line: " + mLines.get(i));
            Log.i("Apple", "Label: " + label);
            if(mLines.get(i).toLowerCase().equals(label))
                    uid = i;
        }

        Log.i("Apple", "uid: " + uid);

        String filePath = "imgdb/";
        //if(uid == 0)
            //filePath += "000";
        if(uid >= 0 && uid < 10)
            filePath += "00" + uid;
        if(uid >= 10 && uid < 100)
            filePath += "0" + uid;
        if(uid >= 100)
            filePath += uid;

        filePath += ".png";

        Bitmap bitmap = null;
        try {
            // creating bitmap from packaged into app android asset 'image.jpg',
            // app/src/main/assets/image.jpg
            //Log.i("Lemon", "Image uid: " + uid);
            bitmap = BitmapFactory.decodeStream(getAssets().open(filePath));
        } catch (Exception e) {
            Log.e("PytorchHelloWorld", "Error reading assets", e);
            finish();
        }

        imageView.setImageBitmap(bitmap);

        /*
        AssetManager manager = getAssets();

        InputStream is = null;
        try {
            is = manager.open("phone_database.json");

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
        //return new String(buffer, "UTF-8");
            Log.i("Buffer", "Buffer: " + buffer.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

         */
        /*Log.i("Manager", "Manager: " + manager.toString());

        try {
            manager.open("model.zip");
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        //for(int i = 0; i < manager.list(null); i++) {

        //}

        //Bitmap bmImg = BitmapFactory.decodeFile("file:///data/user/0/org.fao.mobile.woodidentifier/files/692e2f59-1866-46bc-905e-e2aef8a86408.jpg");
        //imageView.setImageBitmap(bmImg);

        //Glide.with(PictureActivity.this).load(Uri.decode("C:\\Users\\lukee\\OneDrive\\Documents\\GitHub\\wood-id-android\\app\\src\\main\\res\\drawable\\background_assorted.jpg")).into(imageView);

        //File rootDataDir = getActivity().getFilesDir();

        //Glide.with(PictureActivity.this).load(Uri.decode("file:///data/user/0/org.fao.mobile.woodidentifier/files/692e2f59-1866-46bc-905e-e2aef8a86408.jpg")).into(imageView);

        //Glide.with(PictureActivity.this).load(Uri.decode("/data/user/0/org.fao.mobile.woodidentifier/files/20230201110920/reference/acacia_auriculiformis/20220126122137Z_corrected.jpg")).into(imageView);
        //errorMessage.setVisibility(View.INVISIBLE);

        //"res\\drawable-hdpi\\ic_calendar_month_outline_black_18dp.jpg"

        //C:\Users\lukee\OneDrive\Documents\GitHub\wood-id-android\app\src\main\res\drawable\background_assorted.jpg

        //Glide.with(PictureActivity.this).load(Uri.decode(inferenceLog.imagePath)).into(imageView);

        errorMessage.setVisibility(View.INVISIBLE);

        /*
        Glide.with(mContext)
    .load(url)
    .placeholder(R.drawable.YourIconForPlaceholder)
    .error(R.drawable.YourIconWhenfailed)
    .into(imageView);
         */
        /*
        try {
            Glide.with(PictureActivity.this).load("file://model_path/pterocarpus").into(imageView);
        }
        catch (Exception ie) {
            Log.i("Glide error", "Glide error");
            errorMessage.setVisibility(View.VISIBLE);
        }
        */


        //referenceImageArr[i] = "file://" + "model_path" + "/" + referenceImages.getString(i);

    }

    // reads in lines of labels.txt
    // allows us to match each class w/ the reference image we want to display
    public List<String> readLine() {
        List<String> mLines = new ArrayList<>();

        try {
            InputStream is = this.getAssets().open("labels.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;

            while ((line = reader.readLine()) != null)
                mLines.add(line);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return mLines;
    }


}
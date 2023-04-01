package org.fao.mobile.woodidentifier;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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

import java.io.File;
import java.util.Collection;
import java.util.Iterator;

public class PictureActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView errorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture);
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

        try {
            Glide.with(PictureActivity.this).load("file://model_path/pterocarpus").into(imageView);
        }
        catch (Exception ie) {
            Log.i("Glide error", "Glide error");
            errorMessage.setVisibility(View.VISIBLE);
        }



        //referenceImageArr[i] = "file://" + "model_path" + "/" + referenceImages.getString(i);

    }


}
package org.fao.mobile.woodidentifier.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelHelper {
    public static final int INPUT_TENSOR_WIDTH = 512;
    public static final int INPUT_TENSOR_HEIGHT = 512;
    public static final String TAG = ModelHelper.class.toString();
    public static final String MODEL_MOBILE_PT = "asset://model.zip";
    private static final String CLASS_LABELS = "labels.txt";
    private static final String MODEL_PATH = "model_path";
    private static final String MODEL_VERSION = "model_version";
    private static final long STOCK_VERSION = 202210270535L;

    private final Module mModule;
    private static ModelHelper instance = null;
    private final FloatBuffer mInputTensorBuffer;
    private final long version;
    private int cropFactor;

    List<String> classLabels;

    public static ModelHelper getHelperInstance(Context context) {
        if (instance == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int cropFactor = Integer.parseInt(prefs.getString(SharedPrefsUtil.CROP_FACTOR, "2048"));
            String modelPath = prefs.getString(MODEL_PATH, null);
            long modelVersion = prefs.getLong(MODEL_VERSION, 0L);
            String assetPath = modelPath;
            List<String> classLabels = getClasses(context, assetPath);
            if (classLabels == null) return null;

            Log.d(TAG, "Total classes " + classLabels.size());
            final String moduleFileAbsoluteFilePath = new File(assetPath, "model.pt").getAbsolutePath();
            instance = new ModelHelper(moduleFileAbsoluteFilePath, classLabels, cropFactor, modelVersion);
        }
        return instance;
    }

    @Nullable
    public static String setupDefaultModel(Context context, SharedPreferences prefs) {
        String modelPath = prefs.getString(MODEL_PATH, null);
        long modelVersion = prefs.getLong(MODEL_VERSION, 0L);
        if (modelPath == null) {
            modelPath = setupModel(context, prefs);
        } else {
            if (modelVersion < STOCK_VERSION) {
                Log.i(TAG, "updating current version " + modelVersion + " to " + STOCK_VERSION);
                try {
                    Files.deleteIfExists(Paths.get(modelPath));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                modelPath = setupModel(context, prefs);
            }
        }
        return modelPath;
    }

    private static String setupModel(Context context, SharedPreferences prefs) {
        Log.i(TAG, "setting up stock model");
        try {
            String modelPath = ModelHelper.prepareModel(context, MODEL_MOBILE_PT);

            //read model version
            File modelInfo = new File(modelPath, "model.json");
            JSONObject jsonObject = new JSONObject(new String(Files.readAllBytes(Paths.get(modelInfo.getCanonicalPath()))));

            prefs.edit().putString(MODEL_PATH, modelPath).putLong(MODEL_VERSION, jsonObject.getLong("version")).commit();
            return modelPath;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Extract a model archive
     *
     * @param context
     * @param archivePath
     * @return
     * @throws IOException
     */
    public static String prepareModel(Context context, String archivePath) throws IOException {
        String assetPath;
        if (archivePath.startsWith("asset://")) {
            assetPath = Utils.assetFilePath(context,
                    archivePath.replace("asset://", ""));
        } else {
            assetPath = archivePath;
        }
        File locaFilesDirectory = context.getFilesDir();
        DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
        String dirname = simpleDateFormat.format(new Date());
        String extractPath = new File(locaFilesDirectory, dirname).getCanonicalPath();
        Log.i(TAG, "extracting to directory " + extractPath);
        Files.createDirectories(Paths.get(extractPath));
        unpackZip(assetPath, extractPath);
        return extractPath;

    }

    @Nullable
    private static List<String> getClasses(Context context, String assetPath) {
        if (assetPath == null) return null;

        File f = new File(assetPath, CLASS_LABELS);
        List<String> classLabels = new ArrayList<>();
        Log.d(TAG, "Reading class labels");
        try (FileReader fr = new FileReader(f); BufferedReader reader = new BufferedReader(fr)) {
            while (reader.ready()) {
                classLabels.add(reader.readLine().toLowerCase());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classLabels;
    }

    public List<String> getClassLabels() {
        return classLabels;
    }

    private static boolean unpackZip(String zipPath, String extractPath) {
        InputStream is;
        ZipInputStream zis;
        try {
            String itemfilename;
            is = new FileInputStream(zipPath);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                itemfilename = ze.getName();
                if (ze.isDirectory()) {
                    File fmd = new File(extractPath + itemfilename);
                    fmd.mkdirs();
                    continue;
                }
                Log.d(TAG, "extracting " + extractPath + "/" + itemfilename);
                FileOutputStream fout = new FileOutputStream(extractPath + "/" + itemfilename);

                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public long getVersion() {
        return version;
    }

    public static final class Result {
        private Integer[] top;
        Double[] scores;
        int height;

        public Integer[] getTop() {
            return top;
        }

        public void setTop(Integer[] top) {
            this.top = top;
        }

        public Double[] getScores() {
            return scores;
        }

        public void setScores(Double[] scores) {
            this.scores = scores;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        int width;

        public int getClassIndex() {
            return classIndex;
        }

        public String getClassLabel() {
            return classLabel;
        }

        public float getScore() {
            return score;
        }

        private final int classIndex;
        private final String classLabel;
        private final float score;

        public Result(int classIndex, String classLabel, float score) {
            this.classIndex = classIndex;
            this.classLabel = classLabel;
            this.score = score;
        }

        public static Result emptyResult() {
            Result result = new Result(0, "", 0.0f);
            result.setScores(new Double[0]);
            result.setTop(new Integer[0]);
            return result;
        }

        public void putTopK(Integer[] top) {
            this.top = top;
        }
    }

    public ModelHelper(String modelAbsolutePath, List<String> classLabels, int cropFactor, long version) {
        Log.d(TAG, "Loading mobile model ..." + modelAbsolutePath + " crop factor " + cropFactor);
        this.mModule = Module.load(modelAbsolutePath);
        this.classLabels = classLabels;
        this.cropFactor = cropFactor;
        this.version = version;
        this.mInputTensorBuffer = Tensor.allocateFloatBuffer(3 * INPUT_TENSOR_WIDTH * INPUT_TENSOR_HEIGHT);
    }

    public Result runInference(InputStream fis) throws IOException {
        Bitmap bitmapA;
        bitmapA = BitmapFactory.decodeStream(fis);

        if (bitmapA.getWidth() < cropFactor || bitmapA.getHeight() < cropFactor) {
            Log.w(TAG, "Phone camera's resolution is too low for inference. Output resolution must be greater than " + cropFactor + " x " + cropFactor + ", bitmap is " + bitmapA.getWidth() + " x " + bitmapA.getHeight());
            cropFactor = Math.min(bitmapA.getWidth(), bitmapA.getHeight());
            Log.w(TAG, "crop factor adjusted to " + cropFactor + " x " + cropFactor);
        }

        int startX = bitmapA.getWidth() / 2 - cropFactor / 2;
        int startY = bitmapA.getHeight() / 2 - cropFactor / 2;

        //crop while respecting aspect ratio
        Bitmap cropped = Bitmap.createBitmap(bitmapA, startX, startY, cropFactor, cropFactor, null, true);

        //scale image to input tensor size
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(cropped, INPUT_TENSOR_WIDTH, INPUT_TENSOR_HEIGHT, true);
        Tensor mInputTensor = Tensor.fromBlob(mInputTensorBuffer, new long[]{1, 3, INPUT_TENSOR_HEIGHT, INPUT_TENSOR_WIDTH});
        TensorImageUtils.bitmapToFloatBuffer(scaledBitmap,
                0,
                0,
                INPUT_TENSOR_WIDTH,
                INPUT_TENSOR_HEIGHT,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB,
                mInputTensorBuffer,
                0);

        final long moduleForwardStartTime = SystemClock.elapsedRealtime();
        Log.i(TAG, "running inference ...");
        final Tensor outputTensor = mModule.forward(IValue.from(mInputTensor)).toTensor();

        final long moduleForwardDuration = SystemClock.elapsedRealtime() - moduleForwardStartTime;

        final float[] scores = outputTensor.getDataAsFloatArray();
        Log.i(TAG, "Inference done " + scores.length + " total scores. took = " + moduleForwardDuration + "ms");
        Integer[] top = Utils.topK(scores, scores.length);
        Log.i(TAG, "result " + top[0] + ": " + classLabels.get(top[0]));
        Result resultBuffer = new Result(top[0], classLabels.get(top[0]), scores[top[0]]);

        resultBuffer.setTop(top);
        Double doubles[] = new Double[scores.length];
        for (int i = 0; i < scores.length; i++) {
            doubles[i] = (double) scores[i];
        }
        resultBuffer.setScores(doubles);
        resultBuffer.setHeight(bitmapA.getHeight());
        resultBuffer.setWidth(bitmapA.getWidth());
        return resultBuffer;
    }
}

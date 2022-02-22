package org.fao.mobile.woodidentifier.utils;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ACCURACY_THRESHOLD;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.room.Room;

import org.fao.mobile.woodidentifier.AppDatabase;
import org.fao.mobile.woodidentifier.BaseCamera2Activity;
import org.fao.mobile.woodidentifier.models.ModelVersion;
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
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelHelper {
    public static final int INPUT_TENSOR_WIDTH = 512;
    public static final int INPUT_TENSOR_HEIGHT = 512;
    public static final String TAG = ModelHelper.class.toString();
    public static final String MODEL_MOBILE_PT = "asset://model.zip";
    private static final String CLASS_LABELS = "labels.txt";
    public static final String MODEL_PATH = "model_path";
    public static final String MODEL_VERSION = "model_version";
    public static final String MODEL_NAME = "model_name";
    private static final long STOCK_VERSION = 202210270535L;
    private static final String SPECIES_DATABASE = "species_database.json";

    private final Module mModule;
    private static ModelHelper instance = null;
    private final FloatBuffer mInputTensorBuffer;
    private final long version;
    private final String name;
    private final Context context;
    private int cropFactor;

    List<String> classLabels;

    public static ModelHelper getHelperInstance(Context context) {
        if (instance == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int cropFactor = Integer.parseInt(prefs.getString(SharedPrefsUtil.CROP_FACTOR, "2048"));
            String modelPath = prefs.getString(MODEL_PATH, null);
            long modelVersion = prefs.getLong(MODEL_VERSION, 0L);
            String modelName = prefs.getString(MODEL_NAME, "default");
            List<String> classLabels = getClasses(context, modelPath);
            if (classLabels == null) return null;

            Log.d(TAG, "Total classes " + classLabels.size());
            final String moduleFileAbsoluteFilePath = new File(modelPath, "model.pt").getAbsolutePath();
            instance = new ModelHelper(context, moduleFileAbsoluteFilePath, classLabels, cropFactor, modelVersion, modelName);
        }
        return instance;
    }

    /**
     * Update current model helper instance
     * @param context
     */
    public static void refreshInstance(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int cropFactor = Integer.parseInt(prefs.getString(SharedPrefsUtil.CROP_FACTOR, "2048"));
        String modelPath = prefs.getString(MODEL_PATH, null);
        long modelVersion = prefs.getLong(MODEL_VERSION, 0L);
        String modelName = prefs.getString(MODEL_NAME, "default");
        List<String> classLabels = getClasses(context, modelPath);
        Log.d(TAG, "Total classes " + classLabels.size());
        final String moduleFileAbsoluteFilePath = new File(modelPath, "model.pt").getAbsolutePath();
        instance = new ModelHelper(context, moduleFileAbsoluteFilePath, classLabels, cropFactor, modelVersion, modelName);
    }

    @Nullable
    public static String setupDefaultModel(Context context, SharedPreferences prefs) throws IOException {
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

    private static String setupModel(Context context, SharedPreferences prefs) throws IOException {
        Log.i(TAG, "setting up stock model");
        ModelVersion modelVersion = registerModel(context, MODEL_MOBILE_PT, true);
        activateModel(context, modelVersion);
        return modelVersion.path;

    }

    /**
     * Registers a model into the system
     * @param context
     * @param archivePath
     * @param activate
     * @return
     * @throws IOException
     */
    public static ModelVersion registerModel(Context context, String archivePath, boolean activate) throws IOException {
        String modelPath = ModelHelper.prepareModel(context, archivePath);
        //read model version
        File modelInfo = new File(modelPath, "model.json");
        JSONObject jsonObject = null;
        try {
            AppDatabase db = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "wood-id").build();
            String name;
            jsonObject = new JSONObject(new String(Files.readAllBytes(Paths.get(modelInfo.getCanonicalPath()))));
            if (jsonObject.has("name")) {
                name = jsonObject.getString("name");
            } else {
                name = "default";
            }
            long version = jsonObject.getLong("version");
            ModelVersion modelVersion = db.modelVersionsDAO().find(name, version);
            if (modelVersion == null) {

                modelVersion = new ModelVersion();
                modelVersion.active = false;
                modelVersion.version = jsonObject.getLong("version");
                if (jsonObject.has("description")) {
                    modelVersion.description = jsonObject.getString("description");
                }
                modelVersion.timestamp = new Date().getTime();
                if (jsonObject.has("name")) {
                    modelVersion.name = jsonObject.getString("name");
                } else {
                    modelVersion.name = "Wood ID model";
                }
                modelVersion.path = modelPath;
                modelVersion.active = activate;
                modelVersion.threshold = jsonObject.optDouble("threshold", 0.0f);
                db.modelVersionsDAO().insert(modelVersion);
            } else {
                //Cleanup extracted model
                File archiveFile = new File(archivePath);
                archiveFile.delete();
            }

            return modelVersion;
        } catch (JSONException e) {
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
        DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss", Locale.getDefault());
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classLabels;
    }

    @Nullable
    static JSONObject getSpeciesDatabase(Context context, String assetPath) throws IOException, JSONException {
        if (assetPath == null) return null;
        Log.d(TAG, "Reading class labels");
        return new JSONObject(Utils.readFileToStringSimple(new File(assetPath, SPECIES_DATABASE)));
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

    public String getName() {
        return name;
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

    public ModelHelper(Context context, String modelAbsolutePath, List<String> classLabels, int cropFactor, long version, String name) {
        Log.d(TAG, "Loading mobile model ..." + modelAbsolutePath + " crop factor " + cropFactor);
        this.mModule = Module.load(modelAbsolutePath);
        this.classLabels = classLabels;
        this.cropFactor = cropFactor;
        this.version = version;
        this.context = context;
        this.name = name;
        this.mInputTensorBuffer = Tensor.allocateFloatBuffer(3 * INPUT_TENSOR_WIDTH * INPUT_TENSOR_HEIGHT);
    }

    public Result runInference(String name, InputStream fis) throws IOException {
        Bitmap bitmapA;
        bitmapA = BitmapFactory.decodeStream(fis);
        Log.i(TAG, "bitmap resolution " + bitmapA.getWidth() + "," + bitmapA.getHeight());
//        if (bitmapA.getWidth() < cropFactor || bitmapA.getHeight() < cropFactor) {
//            Log.w(TAG, "Phone camera's resolution is too low for inference. Output resolution must be greater than " + cropFactor + " x " + cropFactor + ", bitmap is " + bitmapA.getWidth() + " x " + bitmapA.getHeight());
//            cropFactor = Math.min(bitmapA.getWidth(), bitmapA.getHeight());
//            Log.w(TAG, "crop factor adjusted to " + cropFactor + " x " + cropFactor);
//        }
//
//        int startX = bitmapA.getWidth() / 2 - cropFactor / 2;
//        int startY = bitmapA.getHeight() / 2 - cropFactor / 2;

        //crop while respecting aspect ratio
//        Bitmap cropped = Bitmap.createBitmap(bitmapA, startX, startY, cropFactor, cropFactor, null, true);

        //scale image to input tensor size
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmapA, INPUT_TENSOR_WIDTH, INPUT_TENSOR_HEIGHT, true);
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
        Double[] doubles = new Double[scores.length];
        for (int i = 0; i < scores.length; i++) {
            doubles[i] = (double) scores[i];
        }
        resultBuffer.setScores(doubles);
        resultBuffer.setHeight(bitmapA.getHeight());
        resultBuffer.setWidth(bitmapA.getWidth());
        return resultBuffer;
    }

    public static void activateModel(Context context, ModelVersion modelVersion) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(MODEL_PATH, modelVersion.path).
                putLong(MODEL_VERSION, modelVersion.version).
                putString(MODEL_NAME, modelVersion.name).
                putFloat(ACCURACY_THRESHOLD, (float)modelVersion.threshold).
                commit();
        ModelHelper.refreshInstance(context);
    }
}

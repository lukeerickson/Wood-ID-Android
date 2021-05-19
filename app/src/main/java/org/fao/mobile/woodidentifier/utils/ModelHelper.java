package org.fao.mobile.woodidentifier.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class ModelHelper {
    public static final int INPUT_TENSOR_WIDTH = 512;
    public static final int INPUT_TENSOR_HEIGHT = 512;
    public static final String TAG = ModelHelper.class.toString();
    public static final String MODEL_MOBILE_PT = "fips_wood_model_mobile.pt";
    private static final String CLASS_LABELS = "labels.txt";
    private final Module mModule;
    private static ModelHelper instance = null;

    List<String> classLabels;

    public static ModelHelper getHelperInstance(Context context) {
        if (instance == null) {
            String assetPath = Utils.assetFilePath(context,
                    MODEL_MOBILE_PT);
            assert assetPath != null;
            String labelsPath = Utils.assetFilePath(context, CLASS_LABELS);
            File f = new File(labelsPath);
            List<String> classLabels = new ArrayList<>();
            Log.d(TAG, "Reading class labels");
            try (FileReader fr = new FileReader(f); BufferedReader reader = new BufferedReader(fr)) {
                while (reader.ready()) {
                    classLabels.add(reader.readLine());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Total classes " + classLabels.size());
            final String moduleFileAbsoluteFilePath = new File(assetPath).getAbsolutePath();
            instance = new ModelHelper(moduleFileAbsoluteFilePath, classLabels);
        }
        return instance;
    }

    public static final class Result {
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
    }

    public ModelHelper(String modelAbsolutePath, List<String> classLabels) {
        Log.d(TAG, "Loading mobile model ..." + modelAbsolutePath);
        this.mModule = Module.load(modelAbsolutePath);
        this.classLabels = classLabels;
    }

    public Result runInference(String imagePath) throws IOException {
        Log.d(TAG, "Loading file from " + imagePath);
        try(FileInputStream fis = new FileInputStream(imagePath.replace("document/raw:",""))) {
            Bitmap bitmapA;
            bitmapA = BitmapFactory.decodeStream(fis);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmapA, INPUT_TENSOR_WIDTH, INPUT_TENSOR_HEIGHT, true);
            FloatBuffer mInputTensorBuffer = Tensor.allocateFloatBuffer(3 * INPUT_TENSOR_WIDTH * INPUT_TENSOR_HEIGHT);
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
            Log.d(TAG, "running inference ...");
            final Tensor outputTensor = mModule.forward(IValue.from(mInputTensor)).toTensor();

            final long moduleForwardDuration = SystemClock.elapsedRealtime() - moduleForwardStartTime;

            final float[] scores = outputTensor.getDataAsFloatArray();
            Log.d(TAG, "Inference done " + scores.length + " total scores. took = " + moduleForwardDuration + "ms");
            int[] top = Utils.topK(scores, scores.length);
            Log.i(TAG, "result " + top[0] + ": " + classLabels.get(top[0]));

            return new Result(top[0], classLabels.get(top[0]), scores[top[0]]);
        }
    }
}

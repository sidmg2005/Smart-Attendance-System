package com.example.hostel;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class FaceRecognitionHelper {

    private Interpreter interpreter;

    private final int INPUT_SIZE = 112;
    private final int EMBEDDING_SIZE = 192;

    public FaceRecognitionHelper(AssetManager assetManager) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // 🔥 better performance

            interpreter = new Interpreter(loadModelFile(assetManager, "facenet.tflite"), options);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ByteBuffer loadModelFile(AssetManager assetManager, String filename) throws Exception {

        AssetFileDescriptor fileDescriptor = assetManager.openFd(filename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());

        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // 🔥 MAIN EMBEDDING FUNCTION
    public float[] getEmbedding(Bitmap bitmap) {

        if (bitmap == null || interpreter == null) return null;

        // Resize
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        inputBuffer.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // Normalize pixels (-1 to 1)
        for (int pixel : pixels) {

            float r = (((pixel >> 16) & 0xFF) - 127.5f) / 127.5f;
            float g = (((pixel >> 8) & 0xFF) - 127.5f) / 127.5f;
            float b = ((pixel & 0xFF) - 127.5f) / 127.5f;

            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
        }

        float[][] output = new float[1][EMBEDDING_SIZE];

        try {
            interpreter.run(inputBuffer, output);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return l2Normalize(output[0]);
    }

    // 🔥 DISTANCE (LOWER = BETTER MATCH)
    public float compare(float[] emb1, float[] emb2) {

        if (emb1 == null || emb2 == null) return Float.MAX_VALUE;

        float sum = 0;

        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            sum += diff * diff;
        }

        return (float) Math.sqrt(sum);
    }

    // 🔥 NORMALIZATION
    private float[] l2Normalize(float[] emb) {

        if (emb == null) return null;

        float sum = 0f;

        for (float v : emb) {
            sum += v * v;
        }

        float norm = (float) Math.sqrt(sum);

        if (norm == 0) return emb;

        float[] normalized = new float[emb.length];

        for (int i = 0; i < emb.length; i++) {
            normalized[i] = emb[i] / norm;
        }

        return normalized;
    }
}
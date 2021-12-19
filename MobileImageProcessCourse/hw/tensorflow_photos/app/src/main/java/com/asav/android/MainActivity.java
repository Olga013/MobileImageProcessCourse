package com.asav.android;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.FragmentManager;
import android.app.FragmentTransaction;

import android.content.*;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;

import android.view.View;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.asav.android.db.EXIFData;
import com.asav.android.db.ImageAnalysisResults;
import com.asav.android.PhotoProcessor;
import com.asav.android.db.SceneData;
import com.asav.android.db.TopCategoriesData;
import com.asav.android.db.RectFloat;

import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import android.media.ExifInterface;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import com.asav.android.mtcnn.Box;
import com.asav.android.mtcnn.MTCNNModel;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import kotlin.NotImplementedError;


/**
 * Created by ....
 */

public class MainActivity extends AppCompatActivity {

    /** Tag for the {@link Log}. */
    private static final String TAG = "MainActivity";
    private final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private HighLevelVisualPreferences preferencesFragment;
    private Photos photosFragment;
    private ImageView imageView;
    private Mat sampledImage=null;
    private Mat img=null;
    private static int minFaceSize=40;

    private ProgressBar progressBar;
    private TextView progressBarinsideText;

    private Thread photoProcessingThread=null;
    private Map<String,Long> photosTaken;
    private ArrayList<String> photosFilenames;
    private int currentPhotoIndex=0;
    private PhotoProcessor photoProcessor = null;
    private AgeGenderEthnicityTfLiteClassifier facialAttributeClassifier = null;
    private EmotionTfLiteClassifier emotionClassifierTfLite = null;
    private MTCNNModel mtcnnFaceDetector=null;

    //private String[] categoryList;

    private Map<String, Set<String>> categoriesHistograms=new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.main_activity);
        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
        else
            init();
    }
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                } break;
                default:
                {
                    super.onManagerConnected(status);
                    Toast.makeText(getApplicationContext(),
                            "OpenCV error",
                            Toast.LENGTH_SHORT).show();
                } break;
            }
        }
    };
    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_age:
                photoProcessor = PhotoProcessor.getPhotoProcessor(this);
                photosTaken = photoProcessor.getCameraImages();

                photosFilenames=new ArrayList<String>(photosTaken.keySet());
                currentPhotoIndex=0;

                progressBar=(ProgressBar) findViewById(R.id.progress);
                progressBar.setMax(photosFilenames.size());
                progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
                progressBarinsideText.setText("");


                //categoryList = getResources().getStringArray(R.array.category_list);
                categoriesHistograms = new HashMap<>();

                photosFragment=new Photos();
                Bundle args = new Bundle();
                args.putStringArray("photosTaken", new String[]{"0"});
                args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
                photosFragment.setArguments(args);

                photoProcessingThread = new Thread(() -> {
                    processAllPhotosAge(facialAttributeClassifier, photosFragment);
                }, "photo-processing-thread");
                progressBar.setVisibility(View.VISIBLE);
                preferencesFragment = new HighLevelVisualPreferences();
                Bundle prefArgs = new Bundle();
                prefArgs.putInt("color", Color.GREEN);
                prefArgs.putString("title", "High-Level topCategories");
                preferencesFragment.setArguments(prefArgs);

                PreferencesClick(null);
                photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
                photoProcessingThread.start();
                break;


            case R.id.action_gender:
                photoProcessor = PhotoProcessor.getPhotoProcessor(this);
                photosTaken = photoProcessor.getCameraImages();
                photosFilenames=new ArrayList<String>(photosTaken.keySet());
                currentPhotoIndex=0;

                progressBar=(ProgressBar) findViewById(R.id.progress);
                progressBar.setMax(photosFilenames.size());
                progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
                progressBarinsideText.setText("");

                //categoryList = getResources().getStringArray(R.array.category_list);
                categoriesHistograms = new HashMap<>();

                photosFragment=new Photos();
                args = new Bundle();
                args.putStringArray("photosTaken", new String[]{"0"});
                args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
                photosFragment.setArguments(args);

                photoProcessingThread = new Thread(() -> {
                    processAllPhotosGender(facialAttributeClassifier, photosFragment);
                }, "photo-processing-thread");
                progressBar.setVisibility(View.VISIBLE);
                preferencesFragment = new HighLevelVisualPreferences();
                prefArgs = new Bundle();
                prefArgs.putInt("color", Color.GREEN);
                prefArgs.putString("title", "High-Level topCategories");
                preferencesFragment.setArguments(prefArgs);


                PreferencesClick(null);

                photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
                photoProcessingThread.start();
                break;

            case R.id.action_ethnicity:
                photoProcessor = PhotoProcessor.getPhotoProcessor(this);
                photosTaken = photoProcessor.getCameraImages();
                photosFilenames=new ArrayList<String>(photosTaken.keySet());
                currentPhotoIndex=0;

                progressBar=(ProgressBar) findViewById(R.id.progress);
                progressBar.setMax(photosFilenames.size());
                progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
                progressBarinsideText.setText("");

                //categoryList = getResources().getStringArray(R.array.category_list);
                categoriesHistograms = new HashMap<>();

                photosFragment=new Photos();
                args = new Bundle();
                args.putStringArray("photosTaken", new String[]{"0"});
                args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
                photosFragment.setArguments(args);

                photoProcessingThread = new Thread(() -> {
                    processAllPhotosEthnicity(facialAttributeClassifier, photosFragment);
                }, "photo-processing-thread");
                progressBar.setVisibility(View.VISIBLE);
                preferencesFragment = new HighLevelVisualPreferences();
                prefArgs = new Bundle();
                prefArgs.putInt("color", Color.GREEN);
                prefArgs.putString("title", "High-Level topCategories");
                preferencesFragment.setArguments(prefArgs);

                PreferencesClick(null);

                photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
                photoProcessingThread.start();
                break;

            case R.id.action_emotion:
                photoProcessor = PhotoProcessor.getPhotoProcessor(this);
                photosTaken = photoProcessor.getCameraImages();
                photosFilenames=new ArrayList<String>(photosTaken.keySet());
                currentPhotoIndex=0;

                progressBar=(ProgressBar) findViewById(R.id.progress);
                progressBar.setMax(photosFilenames.size());
                progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
                progressBarinsideText.setText("");

                //categoryList = getResources().getStringArray(R.array.category_list);
                categoriesHistograms = new HashMap<>();
                photosFragment=new Photos();
                args = new Bundle();
                args.putStringArray("photosTaken", new String[]{"0"});
                args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
                photosFragment.setArguments(args);

                photoProcessingThread = new Thread(() -> {
                    processAllPhotosEmotion(emotionClassifierTfLite, photosFragment);
                }, "photo-processing-thread");
                progressBar.setVisibility(View.VISIBLE);
                preferencesFragment = new HighLevelVisualPreferences();
                prefArgs = new Bundle();
                prefArgs.putInt("color", Color.GREEN);
                prefArgs.putString("title", "High-Level topCategories");
                preferencesFragment.setArguments(prefArgs);

                PreferencesClick(null);

                photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
                photoProcessingThread.start();
                break;

            case R.id.action_comparefaces:
                photoProcessor = PhotoProcessor.getPhotoProcessor(this);
                photosTaken = photoProcessor.getCameraImages();
                photosFilenames=new ArrayList<String>(photosTaken.keySet());
                currentPhotoIndex=0;

                progressBar=(ProgressBar) findViewById(R.id.progress);
                progressBar.setMax(photosFilenames.size());
                progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
                progressBarinsideText.setText("");

                //categoryList = getResources().getStringArray(R.array.category_list);
                categoriesHistograms = new HashMap<>();

                photosFragment=new Photos();
                args = new Bundle();
                args.putStringArray("photosTaken", new String[]{"0"});
                args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
                photosFragment.setArguments(args);

                photoProcessingThread = new Thread(() -> {
                    processAllPhotosFace(photosFragment);
                }, "photo-processing-thread");


                progressBar.setVisibility(View.VISIBLE);
                preferencesFragment = new HighLevelVisualPreferences();
                prefArgs = new Bundle();
                prefArgs.putInt("color", Color.GREEN);
                prefArgs.putString("title", "High-Level topCategories");
                preferencesFragment.setArguments(prefArgs);

                PreferencesClick(null);

                photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
                photoProcessingThread.start();
                break;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }



    private Mat convertToMat(String fname)
    {   Bitmap bmp = null;
        Mat resImage=null;
        try {
            bmp = BitmapFactory.decodeFile(fname);
            Mat rgbImage=new Mat();
            Utils.bitmapToMat(bmp, rgbImage);
            ExifInterface exif = new ExifInterface(fname);//selectedImageUri.getPath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
            switch (orientation)
            {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    //get the mirrored image
                    rgbImage=rgbImage.t();
                    //flip on the y-axis
                    Core.flip(rgbImage, rgbImage, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    //get up side down image
                    rgbImage=rgbImage.t();
                    //Flip on the x-axis
                    Core.flip(rgbImage, rgbImage, 0);
                    break;
            }

            Display display = getWindowManager().getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;
            double downSampleRatio= calculateSubSampleSize(rgbImage,width,height);
            resImage=new Mat();
            Imgproc.resize(rgbImage, resImage, new
                    Size(),downSampleRatio,downSampleRatio,Imgproc.INTER_AREA);
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown: " + e+" "+Log.getStackTraceString(e));
            resImage=null;
        }
        return resImage;
    }

    private static double calculateSubSampleSize(Mat srcImage, int reqWidth,
                                                 int reqHeight) {
        final int height = srcImage.height();
        final int width = srcImage.width();
        double inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final double heightRatio = (double) reqHeight / (double) height;
            final double widthRatio = (double) reqWidth / (double) width;
            inSampleSize = heightRatio<widthRatio ? heightRatio :widthRatio;
        }
        return inSampleSize;
    }
    private void displayImage(Mat image)
    {
        Bitmap bitmap = Bitmap.createBitmap(image.cols(),
                image.rows(),Bitmap.Config.RGB_565);
        Utils.matToBitmap(image, bitmap);
        imageView.setImageBitmap(bitmap);
    }




    public String mtcnnDetectionAndAttributesRecognition(TfLiteClassifier classifier, String filename, ResultType type){
        sampledImage=convertToMat(filename);
        Bitmap bmp = Bitmap.createBitmap(sampledImage.cols(), sampledImage.rows(),Bitmap.Config.RGB_565);
        Utils.matToBitmap(sampledImage, bmp);
        ClassifierResult res = null;

        Bitmap resizedBitmap=bmp;
        double minSize=600.0;
        double scale=Math.min(bmp.getWidth(),bmp.getHeight())/minSize;
        if(scale>1.0) {
            resizedBitmap = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()/scale), (int)(bmp.getHeight()/scale), false);
            bmp=resizedBitmap;
        }
        long startTime = SystemClock.uptimeMillis();
        Vector<Box> bboxes = mtcnnFaceDetector.detectFaces(resizedBitmap, minFaceSize);//(int)(bmp.getWidth()*MIN_FACE_SIZE));
        Log.i(TAG, "Timecost to run mtcnn: " + Long.toString(SystemClock.uptimeMillis() - startTime));

        Bitmap tempBmp = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tempBmp);
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setAntiAlias(true);
        p.setFilterBitmap(true);
        p.setDither(true);
        p.setColor(Color.BLUE);
        p.setStrokeWidth(5);

        Paint p_text = new Paint();
        p_text.setColor(Color.WHITE);
        p_text.setStyle(Paint.Style.FILL);
        p_text.setColor(Color.GREEN);
        p_text.setTextSize(24);

        c.drawBitmap(bmp, 0, 0, null);

        for (Box box : bboxes) {

            p.setColor(Color.RED);
            android.graphics.Rect bbox = new android.graphics.Rect(Math.max(0,bmp.getWidth()*box.left() / resizedBitmap.getWidth()),
                    Math.max(0,bmp.getHeight()* box.top() / resizedBitmap.getHeight()),
                    bmp.getWidth()* box.right() / resizedBitmap.getWidth(),
                    bmp.getHeight() * box.bottom() / resizedBitmap.getHeight()
            );

            c.drawRect(bbox, p);

            if(classifier!=null && bbox.width()>0 && bbox.height()>0) {
                Bitmap faceBitmap = Bitmap.createBitmap(bmp, bbox.left, bbox.top, bbox.width(), bbox.height());
                Bitmap resultBitmap = Bitmap.createScaledBitmap(faceBitmap, classifier.getImageSizeX(), classifier.getImageSizeY(), false);
                res = classifier.classifyFrame(resultBitmap);
                c.drawText(res.toString(), bbox.left, Math.max(0, bbox.top - 20), p_text);
                Log.i(TAG, res.toString());
            }
        }

        if (res == null) {
            return String.format("other");
        }
        switch (type) {
            case AGE:
                if (((FaceData)res).getAge() < 18){
                    return String.format("young");
                }
                if (((FaceData)res).getAge() >= 18 && (((FaceData)res).getAge() <= 60)){
                    return String.format("adult");
                }
                if (((FaceData)res).getAge() > 60){
                    return String.format("old");
                }
                //return Integer.toString(((FaceData)res).getAge());
            case ETHNICITY:
                return ((FaceData)res).getEthnicity(((FaceData)res).ethnicityScores);
            case GENDER:
                return String.format("%s", ((FaceData)res).isMale()?"male" : "female");
            case EMOTION:
                return ((EmotionData)res).getEmotion(((EmotionData)res).emotionScores);
            case FACE:
                return null;
                //return String.format("%s", ((FaceData)res).isMale()?"male" : "female");

        }
        return null;
        //imageView.setImageBitmap(tempBmp);
    }


    private boolean matchFaces(String filename1, String filename2){
        Mat img1 =convertToMat(filename1);
        Mat img2 =convertToMat(filename2);
        Mat resImage = new Mat();
        if(img2.rows()!=img1.rows()){
            Imgproc.resize(img2,img2,img1.size());
        }
        List<Mat> src = Arrays.asList(img1, img2);
        Core.hconcat(src, resImage);
        List<FaceFeatures> features1=getFacesFeatures(img1);
        List<FaceFeatures> features2=getFacesFeatures(img2);
        for(FaceFeatures face1 : features1){
            double minDist=10000;
            FaceFeatures bestFace=null;
            for(FaceFeatures face2 : features2){
                double dist = 0;
                for (int i = 0; i < face1.features.length; ++i) {
                    dist += (face1.features[i] - face2.features[i]) * (face1.features[i] - face2.features[i]);
                }
                dist = Math.sqrt(dist);
                if(dist<minDist){
                    minDist=dist;
                    bestFace=face2;
                }
            }
            if(bestFace!=null && minDist<1){
                return true;

            }
        }
        return false;
    }


    private List<FaceFeatures> getFacesFeatures(Mat img){
        Bitmap bmp = Bitmap.createBitmap(img.cols(), img.rows(),Bitmap.Config.RGB_565);
        Utils.matToBitmap(img, bmp);

        Bitmap resizedBitmap=bmp;
        double minSize=600.0;
        double scale=Math.min(bmp.getWidth(),bmp.getHeight())/minSize;
        if(scale>1.0) {
            resizedBitmap = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()/scale), (int)(bmp.getHeight()/scale), false);
            bmp=resizedBitmap;
        }
        long startTime = SystemClock.uptimeMillis();
        Vector<Box> bboxes = mtcnnFaceDetector.detectFaces(resizedBitmap, minFaceSize);//(int)(bmp.getWidth()*MIN_FACE_SIZE));
        Log.i(TAG, "Timecost to run mtcnn: " + Long.toString(SystemClock.uptimeMillis() - startTime));

        List<FaceFeatures> facesInfo=new ArrayList<>();
        for (Box box : bboxes) {
            android.graphics.Rect bbox = new android.graphics.Rect(Math.max(0,bmp.getWidth()*box.left() / resizedBitmap.getWidth()),
                    Math.max(0,bmp.getHeight()* box.top() / resizedBitmap.getHeight()),
                    bmp.getWidth()* box.right() / resizedBitmap.getWidth(),
                    bmp.getHeight() * box.bottom() / resizedBitmap.getHeight()
            );
            Bitmap faceBitmap = Bitmap.createBitmap(bmp, bbox.left, bbox.top, bbox.width(), bbox.height());
            Bitmap resultBitmap = Bitmap.createScaledBitmap(faceBitmap, facialAttributeClassifier.getImageSizeX(), facialAttributeClassifier.getImageSizeY(), false);
            FaceData res=(FaceData)facialAttributeClassifier.classifyFrame(resultBitmap);
            facesInfo.add(new FaceFeatures(res.features,0.5f*(box.left()+box.right()) / resizedBitmap.getWidth(),0.5f*(box.top()+box.bottom()) / resizedBitmap.getHeight()));
        }
        return facesInfo;
    }

    private class FaceFeatures{
        public FaceFeatures(float[] feat, float x, float y){
            features=feat;
            centerX=x;
            centerY=y;
        }
        public float[] features;
        public float centerX,centerY;
    }



    private void init(){
        //checkServerSettings();
        try {
            mtcnnFaceDetector = MTCNNModel.Companion.create(getAssets());
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing MTCNNModel!"+e);
        }
        try {
            facialAttributeClassifier=new AgeGenderEthnicityTfLiteClassifier(getApplicationContext());
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing AgeGenderEthnicityTfLiteClassifier!", e);
        }

        try {
            emotionClassifierTfLite =new EmotionTfLiteClassifier(getApplicationContext());
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing EmotionTfLiteClassifier!", e);
        }
    }
    public synchronized Map<String, Set<String>> getCategoriesHistograms(){
        return categoriesHistograms;
    }
    public enum ResultType {
        AGE,
        GENDER,
        ETHNICITY,
        EMOTION,
        FACE
    }

    private void processAllPhotosAge(TfLiteClassifier classifier, Photos photos){
        //ImageAnalysisResults previousPhotoProcessedResult=null;
        photos.photosResults = new ArrayList<>();
        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    String res = mtcnnDetectionAndAttributesRecognition(classifier, filename, ResultType.AGE);
                    photos.photosResults.add(res);

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(res, filename);
                    final int progress=currentPhotoIndex+1;
                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText(""+100*progress/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
    }

    private void processAllPhotosGender(TfLiteClassifier classifier, Photos photos){
        //ImageAnalysisResults previousPhotoProcessedResult=null;
        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    String res = mtcnnDetectionAndAttributesRecognition(classifier, filename, ResultType.GENDER);
                    photos.photosResults.add(res);

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(res, filename);
                    final int progress=currentPhotoIndex+1;
                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText(""+100*progress/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
    }


    private void processAllPhotosEthnicity(TfLiteClassifier classifier, Photos photos){
        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    String res = mtcnnDetectionAndAttributesRecognition(classifier, filename, ResultType.ETHNICITY);
                    photos.photosResults.add(res);

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(res, filename);
                    final int progress=currentPhotoIndex+1;
                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText(""+100*progress/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
    }


    private void processAllPhotosEmotion(TfLiteClassifier classifier, Photos photos){
        //ImageAnalysisResults previousPhotoProcessedResult=null;
        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    String res = mtcnnDetectionAndAttributesRecognition(classifier, filename, ResultType.EMOTION);
                    photos.photosResults.add(res);

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(res, filename);
                    final int progress=currentPhotoIndex+1;
                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText(""+100*progress/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
    }

    private void processAllPhotosFace(Photos photos){
        //ImageAnalysisResults previousPhotoProcessedResult=null;
        int otherIdx = -1;
        HashMap<String, HashSet<String>> classes = new HashMap<>();
        HashMap<String, String> filename2class = new HashMap<>();
        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);
                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    HashSet<String> similarPhotos = new HashSet<>();
                    for (otherIdx = currentPhotoIndex + 1; otherIdx<photosTaken.size(); ++otherIdx) {
                        boolean res = matchFaces(filename, photosFilenames.get(otherIdx));
                        if (res) {
                            similarPhotos.add(photosFilenames.get(otherIdx));
                        }
                    }
                    boolean photoAddedToClass = false;
                    for (HashSet<String> fnames: classes.values()) {
                        if (fnames.contains(filename)) {
                            photoAddedToClass = true;
                            break;
                        }
                    }
                    if (!photoAddedToClass) {
                        int new_class = classes.size();
                        similarPhotos.add(filename);
                        classes.put("Face" + Integer.toString(new_class), similarPhotos);
                        for (String fname: similarPhotos) {
                            filename2class.put(fname, "Face" + Integer.toString(new_class));
                        }
                    }
                    photos.photosResults.add(filename2class.get(filename));

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(filename2class.get(filename), filename);
                    final int progress=currentPhotoIndex+1;
                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText(""+100*progress/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
    }


    private synchronized void processRecognitionResults(String res, String filename){


        if (!categoriesHistograms.containsKey(res)) {
            categoriesHistograms.put(res, new HashSet<>());
        }
        categoriesHistograms.get(res).add(filename);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                preferencesFragment.updateChart();
            }
        });
    }

    public void PreferencesClick(View view) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_switch, preferencesFragment);
        fragmentTransaction.commit();
    }
    public void PhotosClick(View view) {
        FragmentManager fm = getFragmentManager();
        if(fm.getBackStackEntryCount()==0) {
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            fragmentTransaction.replace(R.id.fragment_switch, photosFragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                   getPackageManager()
                            .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }
    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            int status=ContextCompat.checkSelfPermission(this,permission);
            if (ContextCompat.checkSelfPermission(this,permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                Map<String, Integer> perms = new HashMap<String, Integer>();
                boolean allGranted = true;
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                        allGranted = false;
                }
                // Check for ACCESS_FINE_LOCATION
                if (allGranted) {
                    // All Permissions Granted
                    init();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Some Permission is Denied", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}

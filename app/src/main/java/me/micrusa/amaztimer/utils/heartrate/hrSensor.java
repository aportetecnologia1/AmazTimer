package me.micrusa.amaztimer.utils.heartrate;


import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;

import com.pixplicity.easyprefs.library.Prefs;

import java.util.Date;

import me.micrusa.amaztimer.R;
import me.micrusa.amaztimer.TCX.Constants;
import me.micrusa.amaztimer.TCX.SaveTCX;
import me.micrusa.amaztimer.TCX.TCXUtils;
import me.micrusa.amaztimer.TCX.data.Lap;
import me.micrusa.amaztimer.TCX.data.TCXData;
import me.micrusa.amaztimer.TCX.data.Trackpoint;
import me.micrusa.amaztimer.defValues;
import me.micrusa.amaztimer.utils.file;
import me.micrusa.amaztimer.utils.prefUtils;
import me.micrusa.amaztimer.utils.utils;

@SuppressWarnings("CanBeFinal")
public class hrSensor implements SensorEventListener {
    private hrListener listener;
    private final me.micrusa.amaztimer.utils.heartrate.latestTraining latestTraining = new latestTraining();
    private long startTime;
    private int accuracy = 2;
    private String latestHrTime;
    private int latestHr = 0;

    private static hrSensor hrSensor;

    //All tcx needed stuff
    private String currentLapStatus = Constants.STATUS_RESTING;
    private Lap currentLap;
    private TCXData TCXData;

    public static hrSensor getInstance(){
        return hrSensor;
    }

    public static hrSensor initialize(hrListener listener){
        hrSensor = new hrSensor(listener);
        return hrSensor;
    }

    private hrSensor(hrListener listener) {
        //Setup sensor manager, sensor and textview
        this.TCXData = new TCXData();
        this.listener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int v = (int) event.values[0];
        if (isAccuracyValid() && v > 25 && v < 230 /*Limit to range 25-230 to avoid fake readings*/) {
            //Get hr value and set the text if battery saving mode is disabled
            if(latestHr != v){
                listener.onHrChanged(v);
                latestHr = v;
            }
            //Send hr value to latestTraining array
            latestTraining.addHrValue(v);
            //Set latest hr value
            String currentDate = TCXUtils.formatDate(new Date());
            //Create Trackpoint and add it to current Lap
            if (!currentDate.equals(this.latestHrTime)) //This will limit trackpoints to 1/s
                currentLap.addTrackpoint(new Trackpoint(v, new Date()));
            this.latestHrTime = currentDate;
        } else {
            //Logger.info("hrSensor: unvalid heart rate: " + String.valueOf(v) + " with " + String.valueOf(this.accuracy) + " accuracy");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor param1Sensor, int param1Int) {
        this.accuracy = param1Int;
    }

    public void registerListener(Context context) {
        utils.setupPrefs(context);
        //Clean all values to avoid merging other values
        latestTraining.cleanAllValues();
        //Register listener with delay in defValues class
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sm.registerListener(this, sm.getDefaultSensor(defValues.HRSENSOR), defValues.HRSENSOR_DELAY);
        //Register start time
        this.startTime = System.currentTimeMillis();
    }

    private boolean isAccuracyValid(){
        //Disabled for testing purposes
        return true; //this.accuracy >= defValues.ACCURACY_RANGE[0] && this.accuracy <= defValues.ACCURACY_RANGE[1];
    }

    public void unregisterListener(Context context) {
        //Unregister listener to avoid battery drain
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sm.unregisterListener(this);
        //Save time and send it to latestTraining
        long endTime = System.currentTimeMillis();
        int totalTimeInSeconds = (int) (endTime - startTime) / 1000;
        latestTraining.saveDataToFile(context, totalTimeInSeconds);
        if (Prefs.getBoolean(defValues.KEY_TCX, defValues.DEFAULT_TCX)) {
            addCurrentLap();
            boolean result = SaveTCX.saveToFile(this.TCXData);
            resetTcxData();
            utils.setLang(context, Prefs.getString(defValues.KEY_LANG, "en"));
            if (result)
                Toast.makeText(context, R.string.tcxexporting, Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(context, R.string.tcxerror, Toast.LENGTH_SHORT).show();
        } else {
            resetTcxData();
        }
    }

    private void resetTcxData(){
        this.currentLap = null;
        this.TCXData = new TCXData();
    }

    private void addCurrentLap(){
        if (this.currentLap != null) {
            this.currentLap.setIntensity(this.currentLapStatus);
            this.currentLap.endLap(System.currentTimeMillis());
            this.currentLap.calcCalories(prefUtils.getAge(),
                    prefUtils.getWeight(),
                    prefUtils.isMale());
            this.TCXData.addLap(this.currentLap);
        }
    }

    public void newLap(String lapStatus){
        this.addCurrentLap();
        this.currentLapStatus = lapStatus;
        this.currentLap = new Lap();
    }

    public static interface hrListener{
        void onHrChanged(int hr);
    }
}
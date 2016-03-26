/**
 * Example of using libmuse library on android.
 * Interaxon, Inc. 2015
 */

package com.interaxon.test.libmuse;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.interaxon.libmuse.Accelerometer;
import com.interaxon.libmuse.AnnotationData;
import com.interaxon.libmuse.ConnectionState;
import com.interaxon.libmuse.Eeg;
import com.interaxon.libmuse.LibMuseVersion;
import com.interaxon.libmuse.MessageType;
import com.interaxon.libmuse.Muse;
import com.interaxon.libmuse.MuseArtifactPacket;
import com.interaxon.libmuse.MuseConfiguration;
import com.interaxon.libmuse.MuseConnectionListener;
import com.interaxon.libmuse.MuseConnectionPacket;
import com.interaxon.libmuse.MuseDataListener;
import com.interaxon.libmuse.MuseDataPacket;
import com.interaxon.libmuse.MuseDataPacketType;
import com.interaxon.libmuse.MuseFileFactory;
import com.interaxon.libmuse.MuseFileReader;
import com.interaxon.libmuse.MuseFileWriter;
import com.interaxon.libmuse.MuseManager;
import com.interaxon.libmuse.MusePreset;
import com.interaxon.libmuse.MuseVersion;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

/**
 * In this simple example MainActivity implements 2 MuseHeadband listeners
 * and updates UI when data from Muse is received. Similarly you can implement
 * listers for other data or register same listener to listen for different type
 * of data.
 * For simplicity we create Listeners as inner classes of MainActivity. We pass
 * reference to MainActivity as we want listeners to update UI thread in this
 * example app.
 * You can also connect multiple muses to the same phone and register same
 * listener to listen for data from different muses. In this case you will
 * have to provide synchronization for data members you are using inside
 * your listener.
 *
 * Usage instructions:
 * 1. Enable bluetooth on your device
 * 2. Pair your device with muse
 * 3. Run this project
 * 4. Press Refresh. It should display all paired Muses in Spinner
 * 5. Make sure Muse headband is waiting for connection and press connect.
 * It may take up to 10 sec in some cases.
 * 6. You should see EEG and accelerometer data as well as connection status,
 * Version information and MuseElements (alpha, beta, theta, delta, gamma waves)
 * on the screen.
 */
public class MainActivity extends Activity implements OnClickListener {
    /*
    private LineGraphSeries<DataPoint> alphaSeries;
    private LineGraphSeries<DataPoint> betaSeries;
    private LineGraphSeries<DataPoint> thetaSeries;
    */
    private LineGraphSeries<DataPoint> aSeries;
    private LineGraphSeries<DataPoint> bSeries;
    private LineGraphSeries<DataPoint> tSeries;
    private LineGraphSeries<DataPoint> dSeries;
    private int lastX = 0;

    private MediaPlayer mp = null;

    /**
     * Connection listener updates UI with new connection status and logs it.
     */
    class ConnectionListener extends MuseConnectionListener {

        final WeakReference<Activity> activityRef;

        ConnectionListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(MuseConnectionPacket p) {
            final ConnectionState current = p.getCurrentConnectionState();
            final String status = p.getPreviousConnectionState().toString() +
                    " -> " + current;
            final String full = "Muse " + p.getSource().getMacAddress() +
                    " " + status;
            Log.i("Muse Headband", full);
            Activity activity = activityRef.get();
            // UI thread is used here only because we need to update
            // TextView values. You don't have to use another thread, unless
            // you want to run disconnect() or connect() from connection packet
            // handler. In this case creating another thread is required.
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView statusText =
                                (TextView) findViewById(R.id.con_status);
                        statusText.setText(status);
                        TextView museVersionText =
                                (TextView) findViewById(R.id.version);
                        if (current == ConnectionState.CONNECTED) {
                            MuseVersion museVersion = muse.getMuseVersion();
                            String version = museVersion.getFirmwareType() +
                                    " - " + museVersion.getFirmwareVersion() +
                                    " - " + Integer.toString(
                                    museVersion.getProtocolVersion());
                            museVersionText.setText(version);
                        } else {
                            museVersionText.setText(R.string.undefined);
                        }
                    }
                });
            }
        }
    }

    /**
     * Data listener will be registered to listen for: Accelerometer,
     * Eeg and Relative Alpha bandpower packets. In all cases we will
     * update UI with new values.
     * We also will log message if Artifact packets contains "blink" flag.
     * DataListener methods will be called from execution thread. If you are
     * implementing "serious" processing algorithms inside those listeners,
     * consider to create another thread.
     */
    class DataListener extends MuseDataListener {

        final WeakReference<Activity> activityRef;
        private MuseFileWriter fileWriter;

        DataListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(MuseDataPacket p) {
            switch (p.getPacketType()) {
                case ALPHA_RELATIVE:
                    updateAlphaRelative(p.getValues());
                    break;
                case BETA_RELATIVE:
                    updateBetaRelative(p.getValues());
                    break;
                case THETA_RELATIVE:
                    updateThetaRelative(p.getValues());
                    break;
                case DELTA_RELATIVE:
                    updateDeltaRelative(p.getValues());
                    break;
                /*
                case ALPHA_ABSOLUTE:
                    updateAlphaAbsolute(p.getValues());
                    break;
                case BETA_ABSOLUTE:
                    updateBetaAbsolute(p.getValues());
                    break;
                case THETA_ABSOLUTE:
                    updateThetaAbsolute(p.getValues());
                    break;
                */
                case BATTERY:
                    fileWriter.addDataPacket(1, p);
                    // It's library client responsibility to flush the buffer,
                    // otherwise you may get memory overflow.
                    if (fileWriter.getBufferedMessagesSize() > 8096)
                        fileWriter.flush();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket p) {
            if (p.getHeadbandOn() && p.getBlink()) {
                Log.i("Artifacts", "blink");
            }
        }

        private void updateAlphaRelative(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView a1 = (TextView) findViewById(R.id.a1);
                        //TextView a2 = (TextView) findViewById(R.id.a2);
                        //TextView a3 = (TextView) findViewById(R.id.a3);
                        TextView a4 = (TextView) findViewById(R.id.a4);
                        TextView a5 = (TextView) findViewById(R.id.a5);
                        a1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        /*
                        a2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        a3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        */
                        a4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        int count = 0;
                        double sum = 0.0d;
                        double avg = 0.0d;
                        if (!Double.isNaN(data.get(Eeg.TP9.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP9.ordinal());
                        }
                        if (!Double.isNaN(data.get(Eeg.TP10.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP10.ordinal());
                        }
                        if (count > 0) {
                            avg = sum / count;
                            if (avg > 0.5) {
                                alarm();
                            }
                            aSeries.appendData(new DataPoint(lastX++, avg), true, 10);
                        }
                        a5.setText(String.format("%6.2f", avg));
                    }
                });
            }
        }

        public void alarm() {
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.alarm);
            mp.start();
        }

        private void updateBetaRelative(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView b1 = (TextView) findViewById(R.id.b1);
                        //TextView b2 = (TextView) findViewById(R.id.b2);
                        //TextView b3 = (TextView) findViewById(R.id.b3);
                        TextView b4 = (TextView) findViewById(R.id.b4);
                        TextView b5 = (TextView) findViewById(R.id.b5);
                        b1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        /*
                        b2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        b3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        */
                        b4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        int count = 0;
                        double sum = 0.0d;
                        double avg = 0.0d;
                        if (!Double.isNaN(data.get(Eeg.TP9.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP9.ordinal());
                        }
                        if (!Double.isNaN(data.get(Eeg.TP10.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP10.ordinal());
                        }
                        if (count > 0) {
                            avg = sum / count;
                            if (avg > 0.5) {
                                alarm();
                            }
                            bSeries.appendData(new DataPoint(lastX++, avg), true, 10);
                        }
                        b5.setText(String.format("%6.2f", avg));
                    }
                });
            }
        }

        private void updateThetaRelative(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView t1 = (TextView) findViewById(R.id.t1);
                        //TextView t2 = (TextView) findViewById(R.id.t2);
                        //TextView t3 = (TextView) findViewById(R.id.t3);
                        TextView t4 = (TextView) findViewById(R.id.t4);
                        TextView t5 = (TextView) findViewById(R.id.t5);
                        t1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        /*
                        t2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        t3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        */
                        t4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        int count = 0;
                        double sum = 0.0d;
                        double avg = 0.0d;
                        if (!Double.isNaN(data.get(Eeg.TP9.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP9.ordinal());
                        }
                        if (!Double.isNaN(data.get(Eeg.TP10.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP10.ordinal());
                        }
                        if (count > 0) {
                            avg = sum / count;
                            if (avg > 0.5) {
                                alarm();
                            }
                            tSeries.appendData(new DataPoint(lastX++, avg), true, 10);
                        }
                        t5.setText(String.format("%6.2f", avg));
                    }
                });
            }
        }

        private void updateDeltaRelative(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView d1 = (TextView) findViewById(R.id.d1);
                        //TextView d2 = (TextView) findViewById(R.id.d2);
                        //TextView d3 = (TextView) findViewById(R.id.d3);
                        TextView d4 = (TextView) findViewById(R.id.d4);
                        TextView d5 = (TextView) findViewById(R.id.d5);
                        d1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        /*
                        d2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        d3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        */
                        d4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        int count = 0;
                        double sum = 0.0d;
                        double avg = 0.0d;
                        if (!Double.isNaN(data.get(Eeg.TP9.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP9.ordinal());
                        }
                        if (!Double.isNaN(data.get(Eeg.TP10.ordinal()))) {
                            count++;
                            sum += data.get(Eeg.TP10.ordinal());
                        }
                        if (count > 0) {
                            avg = sum / count;
                            if (avg > 0.5) {
                                alarm();
                            }
                            dSeries.appendData(new DataPoint(lastX++, avg), true, 10);
                        }
                        d5.setText(String.format("%6.2f", avg));
                    }
                });
            }
        }

        /*
        private void updateAlphaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView alpha1 = (TextView) findViewById(R.id.alpha1);
                        TextView alpha2 = (TextView) findViewById(R.id.alpha2);
                        TextView alpha3 = (TextView) findViewById(R.id.alpha3);
                        TextView alpha4 = (TextView) findViewById(R.id.alpha4);
                        alpha1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        alpha2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        alpha3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        alpha4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        alphaSeries.appendData(new DataPoint(lastX++, data.get(Eeg.TP9.ordinal())), true, 10);
                    }
                });
            }
        }

        private void updateBetaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView beta1 = (TextView) findViewById(R.id.beta1);
                        TextView beta2 = (TextView) findViewById(R.id.beta2);
                        TextView beta3 = (TextView) findViewById(R.id.beta3);
                        TextView beta4 = (TextView) findViewById(R.id.beta4);
                        beta1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        beta2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        beta3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        beta4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        betaSeries.appendData(new DataPoint(lastX++, data.get(Eeg.TP9.ordinal())), true, 10);
                    }
                });
            }
        }

        private void updateThetaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView theta1 = (TextView) findViewById(R.id.theta1);
                        TextView theta2 = (TextView) findViewById(R.id.theta2);
                        TextView theta3 = (TextView) findViewById(R.id.theta3);
                        TextView theta4 = (TextView) findViewById(R.id.theta4);
                        theta1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        theta2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        theta3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        theta4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                        thetaSeries.appendData(new DataPoint(lastX++, data.get(Eeg.TP9.ordinal())), true, 10);
                    }
                });
            }
        }
        */

        public void setFileWriter(MuseFileWriter fileWriter) {
            this.fileWriter  = fileWriter;
        }
    }

    private Muse muse = null;
    private ConnectionListener connectionListener = null;
    private DataListener dataListener = null;
    private boolean dataTransmission = true;
    private MuseFileWriter fileWriter = null;

    public MainActivity() {
        // Create listeners and pass reference to activity to them
        WeakReference<Activity> weakActivity =
                new WeakReference<Activity>(this);

        connectionListener = new ConnectionListener(weakActivity);
        dataListener = new DataListener(weakActivity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);
        Button pauseButton = (Button) findViewById(R.id.pause);
        pauseButton.setOnClickListener(this);

        GraphView aGraph = (GraphView) findViewById(R.id.graph_alpha_relative);
        GraphView bGraph = (GraphView) findViewById(R.id.graph_beta_relative);
        GraphView tGraph = (GraphView) findViewById(R.id.graph_theta_relative);
        GraphView dGraph = (GraphView) findViewById(R.id.graph_delta_relative);
        aSeries = new LineGraphSeries<DataPoint>();
        bSeries = new LineGraphSeries<DataPoint>();
        tSeries = new LineGraphSeries<DataPoint>();
        dSeries = new LineGraphSeries<DataPoint>();
        aGraph.addSeries(aSeries);
        bGraph.addSeries(bSeries);
        tGraph.addSeries(tSeries);
        dGraph.addSeries(dSeries);

        /*
        GraphView alphaGraph = (GraphView) findViewById(R.id.graph_alpha_absolute);
        GraphView betaGraph = (GraphView) findViewById(R.id.graph_beta_absolute);
        GraphView thetaGraph = (GraphView) findViewById(R.id.graph_theta_absolute);
        alphaSeries = new LineGraphSeries<DataPoint>();
        betaSeries = new LineGraphSeries<DataPoint>();
        thetaSeries = new LineGraphSeries<DataPoint>();
        alphaGraph.addSeries(alphaSeries);
        betaGraph.addSeries(betaSeries);
        thetaGraph.addSeries(thetaSeries);
        */

        // customize graph
        Viewport aViewport = aGraph.getViewport();
        aViewport.setYAxisBoundsManual(true);
        aViewport.setMinY(-1);
        aViewport.setMaxY(1);
        aViewport.setScrollable(true);
        Viewport bViewport = bGraph.getViewport();
        bViewport.setYAxisBoundsManual(true);
        bViewport.setMinY(-1);
        bViewport.setMaxY(1);
        bViewport.setScrollable(true);
        Viewport tViewport = tGraph.getViewport();
        tViewport.setYAxisBoundsManual(true);
        tViewport.setMinY(-1);
        tViewport.setMaxY(1);
        tViewport.setScrollable(true);
        Viewport dViewport = dGraph.getViewport();
        dViewport.setYAxisBoundsManual(true);
        dViewport.setMinY(-1);
        dViewport.setMaxY(1);
        dViewport.setScrollable(true);

        /*
        Viewport alphaViewport = alphaGraph.getViewport();
        alphaViewport.setYAxisBoundsManual(true);
        alphaViewport.setMinY(-1);
        alphaViewport.setMaxY(1);
        alphaViewport.setScrollable(true);
        Viewport betaViewport = betaGraph.getViewport();
        betaViewport.setYAxisBoundsManual(true);
        betaViewport.setMinY(-1);
        betaViewport.setMaxY(1);
        betaViewport.setScrollable(true);
        Viewport thetaViewport = thetaGraph.getViewport();
        thetaViewport.setYAxisBoundsManual(true);
        thetaViewport.setMinY(-1);
        thetaViewport.setMaxY(1);
        thetaViewport.setScrollable(true);
        */

        // // Uncommet to test Muse File Reader
        //
        // // file can be big, read it in a separate thread
        // Thread thread = new Thread(new Runnable() {
        //     public void run() {
        //         playMuseFile("testfile.muse");
        //     }
        // });
        // thread.start();

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        fileWriter = MuseFileFactory.getMuseFileWriter(
                new File(dir, "new_muse_file.muse"));
        Log.i("Muse Headband", "libmuse version=" + LibMuseVersion.SDK_VERSION);
        fileWriter.addAnnotationString(1, "MainActivity onCreate");
        dataListener.setFileWriter(fileWriter);
    }

    @Override
    public void onClick(View v) {
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        if (v.getId() == R.id.refresh) {
            MuseManager.refreshPairedMuses();
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            List<String> spinnerItems = new ArrayList<String>();
            for (Muse m: pairedMuses) {
                String dev_id = m.getName() + "-" + m.getMacAddress();
                Log.i("Muse Headband", dev_id);
                spinnerItems.add(dev_id);
            }
            ArrayAdapter<String> adapterArray = new ArrayAdapter<String> (
                    this, android.R.layout.simple_spinner_item, spinnerItems);
            musesSpinner.setAdapter(adapterArray);
        }
        else if (v.getId() == R.id.connect) {
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            if (pairedMuses.size() < 1 ||
                    musesSpinner.getAdapter().getCount() < 1) {
                Log.w("Muse Headband", "There is nothing to connect to");
            }
            else {
                muse = pairedMuses.get(musesSpinner.getSelectedItemPosition());
                ConnectionState state = muse.getConnectionState();
                if (state == ConnectionState.CONNECTED ||
                        state == ConnectionState.CONNECTING) {
                    Log.w("Muse Headband",
                            "doesn't make sense to connect second time to the same muse");
                    return;
                }
                configureLibrary();
                fileWriter.open();
                fileWriter.addAnnotationString(1, "Connect clicked");
                /**
                 * In most cases libmuse native library takes care about
                 * exceptions and recovery mechanism, but native code still
                 * may throw in some unexpected situations (like bad bluetooth
                 * connection). Print all exceptions here.
                 */
                try {
                    muse.runAsynchronously();
                } catch (Exception e) {
                    Log.e("Muse Headband", e.toString());
                }
            }
        }
        else if (v.getId() == R.id.disconnect) {
            if (muse != null) {
                /**
                 * true flag will force libmuse to unregister all listeners,
                 * BUT AFTER disconnecting and sending disconnection event.
                 * If you don't want to receive disconnection event (for ex.
                 * you call disconnect when application is closed), then
                 * unregister listeners first and then call disconnect:
                 * muse.unregisterAllListeners();
                 * muse.disconnect(false);
                 */
                muse.disconnect(true);
                fileWriter.addAnnotationString(1, "Disconnect clicked");
                fileWriter.flush();
                fileWriter.close();
            }
        }
        else if (v.getId() == R.id.pause) {
            dataTransmission = !dataTransmission;
            if (muse != null) {
                muse.enableDataTransmission(dataTransmission);
            }
        }
    }

    /*
     * Simple example of getting data from the "*.muse" file
     */
    private void playMuseFile(String name) {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(dir, name);
        final String tag = "Muse File Reader";
        if (!file.exists()) {
            Log.w(tag, "file doesn't exist");
            return;
        }
        MuseFileReader fileReader = MuseFileFactory.getMuseFileReader(file);
        while (fileReader.gotoNextMessage()) {
            MessageType type = fileReader.getMessageType();
            int id = fileReader.getMessageId();
            long timestamp = fileReader.getMessageTimestamp();
            Log.i(tag, "type: " + type.toString() +
                    " id: " + Integer.toString(id) +
                    " timestamp: " + String.valueOf(timestamp));
            switch(type) {
                case EEG: case BATTERY: case ACCELEROMETER: case QUANTIZATION:
                    MuseDataPacket packet = fileReader.getDataPacket();
                    Log.i(tag, "data packet: " + packet.getPacketType().toString());
                    break;
                case VERSION:
                    MuseVersion version = fileReader.getVersion();
                    Log.i(tag, "version" + version.getFirmwareType());
                    break;
                case CONFIGURATION:
                    MuseConfiguration config = fileReader.getConfiguration();
                    Log.i(tag, "config" + config.getBluetoothMac());
                    break;
                case ANNOTATION:
                    AnnotationData annotation = fileReader.getAnnotation();
                    Log.i(tag, "annotation" + annotation.getData());
                    break;
                default:
                    break;
            }
        }
    }


    private void configureLibrary() {
        muse.registerConnectionListener(connectionListener);
        muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
        muse.registerDataListener(dataListener, MuseDataPacketType.BETA_RELATIVE);
        muse.registerDataListener(dataListener, MuseDataPacketType.THETA_RELATIVE);
        muse.registerDataListener(dataListener, MuseDataPacketType.DELTA_RELATIVE);
        /*
        muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_ABSOLUTE);
        muse.registerDataListener(dataListener, MuseDataPacketType.BETA_ABSOLUTE);
        muse.registerDataListener(dataListener, MuseDataPacketType.THETA_ABSOLUTE);
        */
        muse.registerDataListener(dataListener, MuseDataPacketType.ARTIFACTS);
        muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
        muse.setPreset(MusePreset.PRESET_14);
        muse.enableDataTransmission(dataTransmission);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

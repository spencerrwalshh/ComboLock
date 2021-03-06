package hpittin1ccunnin5msausne2.combolock;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView combo_1, combo_2, combo_3;
    private Button generate_combo_button;
    private ImageView inner_combo_img, arrow;

    private SensorManager mSensorManager;
    private Sensor accelerometer, gyroscope;

    private float timestamp = 0;
    private int correct_count = 0;
    private boolean not_done = true;
    private int[] current_combination;
    private float time_spend_at_correct_num = 0;
    private final String log_tag = "development";

    //Each tick on the lock is separated by 9 degrees
    int deg_per_tick = 9;

    //Convert nanoseconds to seconds
    private static final float NS2S = 1.0f / 1000000000.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initSensors();

        combo_1 = (TextView) findViewById(R.id.combo_1);
        combo_2 = (TextView) findViewById(R.id.combo_2);
        combo_3 = (TextView) findViewById(R.id.combo_3);

        generate_combo_button = (Button) findViewById(R.id.generate_combo_button);
        generate_combo_button.setOnClickListener(new GenerateComboBtnListener());
        inner_combo_img = (ImageView) findViewById(R.id.inner_combo_img);

        arrow = (ImageView) findViewById(R.id.arrow);

        //Correct image distortion
        inner_combo_img.setRotation(4);

        //Reset arrow to fix bug
        resetArrow();

        current_combination = new int[3];

        generateRandomCombination();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    private void initSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        //Warning shows up on phones with gyroscopes
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            List<Sensor> gyroscopeSensors = mSensorManager.getSensorList(Sensor.TYPE_GYROSCOPE);
            gyroscope = gyroscopeSensors.get(0);
            mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Toast.makeText(getApplicationContext(), "Phone has no gyroscope", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Generates three random numbers that correspond to the combination needed to unlock the
     * combination lock.
     * Sets the global array (current_combination) to the randomly selected values for use
     * throughout the app.
     * Sets the current_combination_label to display the current combination
     */
    private void generateRandomCombination() {
        Random num_generator = new Random();

        //First combo number should not be in range -5 to 5.
        int temp_combo_one;
        do {
            temp_combo_one = num_generator.nextInt(40);
        } while (temp_combo_one >= -5 && temp_combo_one <= 5);

        //Combo numbers should not repeat
        //Consecutive combo numbers should be separated by ~5 ticks in either direction
        int temp_combo_two;
        do {
            temp_combo_two = num_generator.nextInt(40);
        }
        while (temp_combo_two == temp_combo_one || (temp_combo_two >= temp_combo_one - 5 && temp_combo_two <= temp_combo_one + 5));

        //If second combo number is a 0, we don't want the third combo number to be in the 38 to 40 range
        int temp_combo_three;
        do {
            temp_combo_three = num_generator.nextInt(40);
        }
        while (temp_combo_three == temp_combo_one || temp_combo_three == temp_combo_two || (temp_combo_three >= temp_combo_two - 5 && temp_combo_three <= temp_combo_two + 5) || (temp_combo_two == 0 && temp_combo_three >= 38));

        current_combination[0] = temp_combo_one;
        current_combination[1] = temp_combo_two;
        current_combination[2] = temp_combo_three;

        //Display combination
        String combo_one = Integer.toString(current_combination[0]);
        String combo_two = Integer.toString(current_combination[1]);
        String combo_three = Integer.toString(current_combination[2]);

        combo_1.setText(combo_one);
        combo_2.setText(combo_two);
        combo_3.setText(combo_three);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (not_done) {

            int deg_leeway = 10;
            float x_axis_reading = event.values[0];
            int current_combo = 0;

            //Get the degrees the user must spin the phone
            current_combo = getCurrentCombo();

            int lower_limit = current_combo - deg_leeway;
            int upper_limit = current_combo + deg_leeway;

            if (timestamp != 0) {

                final float time_interval = (event.timestamp - timestamp) * NS2S;
                float radians = x_axis_reading * time_interval;

                //Low pass filter - stops shaking
                if (Math.abs(radians) >= .04) {

                    int rotation_from_pivot = (int) (inner_combo_img.getRotation() + (int) Math.toDegrees(radians));

                    int outer_speed_trap_lower = lower_limit - (deg_leeway * 5);
                    int outer_speed_trap_upper = upper_limit + (deg_leeway * 5);
                    int inner_speed_trap_lower = lower_limit - (deg_leeway * 2);
                    int inner_speed_trap_upper = upper_limit + (deg_leeway * 2);

                    //Speed zones. If you are close to combination number, slow down spinning speed
                    if (rotation_from_pivot >= outer_speed_trap_lower && rotation_from_pivot <= outer_speed_trap_upper) {
                        rotation_from_pivot = (int) (inner_combo_img.getRotation() + (int) Math.toDegrees(radians / 2));
                        if (rotation_from_pivot >= inner_speed_trap_lower && rotation_from_pivot <= inner_speed_trap_upper) {
                            rotation_from_pivot = (int) (inner_combo_img.getRotation() + (int) Math.toDegrees(radians / 4));
                        }
                    }

                    //Full rotation
                    if (Math.abs(rotation_from_pivot) > 360) {
                        rotation_from_pivot = (rotation_from_pivot % 360);
                    }

                    inner_combo_img.setRotation(rotation_from_pivot);

                    //---Debug---//
                    //Log.d(log_tag, "Correct count: " + Integer.toString(correct_count));
                    //Log.d(log_tag, "Current combo: " + Integer.toString(current_combo));
                    //Log.d(log_tag, "Rotation from pivot: " + Integer.toString(rotation_from_pivot));

                }

                //Not moving - check if user is on correct combination number
                else {
                    int rotation_from_pivot = (int) (inner_combo_img.getRotation());
                    if (rotation_from_pivot >= lower_limit && rotation_from_pivot <= upper_limit) {

                        //True if user has been on the correct number for 0.75 seconds
                        if (time_spend_at_correct_num >= 0.75) {
                            time_spend_at_correct_num = 0;
                            switch (correct_count) {
                                case 0:
                                    combo_1.setTextColor(Color.GREEN);
                                    break;
                                case 1:
                                    combo_2.setTextColor(Color.GREEN);
                                    break;
                                case 2:
                                    combo_3.setTextColor(Color.GREEN);
                                    break;
                            }
                            correct_count++;
                            //Log.d(log_tag, "Combo correct!");
                        }

                        time_spend_at_correct_num += time_interval;
                        //---Debug---//
                        //Log.d(log_tag, "time: " + (int) time_spend_at_correct_num);
                    }
                }

                //Combination is correct. Go to hidden screen
                if (correct_count >= 3) {
                    correct_count = 0;
                    time_spend_at_correct_num = 0;
                    Toast.makeText(getApplicationContext(), "You have successfully unlocked the lock", Toast.LENGTH_LONG).show();
                    not_done = false;
                    Intent intent = new Intent(this, ComboUnlockedActivity.class);
                    startActivity(intent);
                    //Log.d(log_tag, "Stop.");
                }

            }
            timestamp = event.timestamp;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


    /**
     * Returns the necessary number of degrees that the user must spin the phone to land on a
     * combination number.
     *
     * @return  Number of degrees that user must rotate the phone.
     */
    private int getCurrentCombo() {

        int current_combo = 0;

        //Configure first combination number
        //Gyroscope gives negative readings when the phone is rotated CW
        //Make current combo negative to force user to spin CW
        if (correct_count == 0) {
            current_combo = -(current_combination[correct_count] * deg_per_tick);
        }

        //Configure second combination number
        //Gyroscope gives positive readings when the phone is rotated CCW
        //Make current combo positive to force user to spin CCW
        else if (correct_count == 1) {
            if (current_combination[correct_count] != 0) {
                //If first combo num is larger than second, make the second number negative so
                //the user has to spin the phone a smaller distance
                if (current_combination[0] > current_combination[1]) {
                    current_combo = -(current_combination[1] * deg_per_tick);
                } else {
                    //Make second combo positive to force user to spin CCW
                    current_combo = 360 - (current_combination[correct_count] * deg_per_tick);
                }
            } else {
                current_combo = current_combination[correct_count];
            }

            //Switch arrow direction
            flipArrow();
        }

        //Configure third combination number
        else if (correct_count == 2) {
            if (current_combination[correct_count] != 0) {
                //If the combo numbers are in sorted order, make the third number positive so the
                //user has to spin the phone a smaller distance from the second number
                if (current_combination[2] > current_combination[1] && current_combination[0] < current_combination[1]) {
                    current_combo = 360 - (current_combination[2] * deg_per_tick);
                } else {
                    //Make third combo negative to force user to spin CW
                    current_combo = -(current_combination[2] * deg_per_tick);
                }
            } else {
                current_combo = -current_combination[correct_count];
            }

            //Switch arrow direction
            resetArrow();
        }

        return current_combo;
    }

    /**
     * Set arrow to initial orientation. Arrow will be pointing to the right.
     */
    private void resetArrow() {
        arrow.setRotation(0);
        arrow.setScaleX(1);
        arrow.setRotation(-45);
    }

    /**
     * Flip the arrow from the original orientation. Arrow will be pointing to the left.
     */
    private void flipArrow() {
        arrow.setRotation(0);
        arrow.setScaleX(-1);
        arrow.setRotation(50);
    }

    /**
     *
     */
    private class GenerateComboBtnListener implements View.OnClickListener {
        /**
         * @param v
         */
        @Override
        public void onClick(View v) {
            //Generate new combo
            generateRandomCombination();

            combo_1.setTextColor(Color.BLACK);
            combo_2.setTextColor(Color.BLACK);
            combo_3.setTextColor(Color.BLACK);

            time_spend_at_correct_num = 0;
            correct_count = 0;
            not_done = true;

            //Set inner lock image to original offset
            inner_combo_img.setRotation(4);

            //Reset arrow
            arrow.setRotation(0);
            arrow.setScaleX(1);
            arrow.setRotation(-45);

            //---Debug---//
            //Log.d(log_tag, "Reset.");

        }
    }
}


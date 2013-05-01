package com.jkrichma.lecarldrive;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends IOIOActivity implements SensorEventListener {
	private SensorManager mSensorManager;
	private Sensor accel;
	private ToggleButton toggleDrive;
	private ToggleButton toggleDirection;
	private ToggleButton toggleAuto;
	private static final String TAG = "leCarl - ";
	private float irLeftVal;
	private float irFrontVal;
	private float irRightVal;
	TextView xViewA = null;
	TextView yViewA = null;
	TextView irLeftView = null;
	TextView irFrontView = null;
	TextView irRightView = null;

	float accelx, accely;
	boolean drive;
	float direction;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Get an instance of the sensor service, and use that to get an
		// instance of
		// a particular sensor.
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		xViewA = (TextView) findViewById(R.id.textTurn);
		yViewA = (TextView) findViewById(R.id.textSpeed);
		irLeftView = (TextView) findViewById(R.id.textIRLeft);
		irFrontView = (TextView) findViewById(R.id.textIRFront);
		irRightView = (TextView) findViewById(R.id.textIRRight);

		toggleDrive = (ToggleButton) findViewById(R.id.toggleCarl);
		toggleDirection = (ToggleButton) findViewById(R.id.toggleDirection);
		toggleAuto = (ToggleButton) findViewById(R.id.toggleAuto);

		drive = false;
		direction = 1; // +1 => fwd, -1 => rev
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Do something here if sensor accuracy changes.
	}

	@Override
	public final void onSensorChanged(SensorEvent event) {

		// for now just display the IR values here
		irLeftView.setText(Float.toString(irLeftVal));
		irFrontView.setText(Float.toString(irFrontVal));
		irRightView.setText(Float.toString(irRightVal));

		// Do something with this sensor data.

		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			accelx += event.values[0] - accelx;
			accely += event.values[1] - accely;

			if (drive) {
				xViewA.setText("Turn Rate: " + accelx);
				yViewA.setText("Speed: " + accely);
			} else {
				yViewA.setText("Paused...");
				xViewA.setText("...press button to drive");
			}
			break;
		}
	}

	@Override
	protected void onResume() {
		// Register a listener for the sensor.
		super.onResume();
		accelx = accely = 0;
		drive = false;
		direction = 1;
		mSensorManager.registerListener(this, accel,
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause() {
		// Be sure to unregister the sensor when the activity pauses.
		super.onPause();
		mSensorManager.unregisterListener(this);
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper {

		private PwmOutput pwmSpeed;
		private PwmOutput pwmSteering;

		// constants for direction array
		private static final int DIR = 0;
		private static final int TURN = 1;
		private static final int VELO = 2;
		private static final float IR_CLEAR = 0.75f;
		private static final float IR_COLLISION = 2.0f;

		float speed;
		float[] cmdPwm;
		float[] cmdPwmPrev;

		AnalogInput in40;
		AnalogInput in41;
		AnalogInput in42;

		float speedScale = -10;
		float turnScale = 50;
		float stopSpeed = 1500;
		float stopTurn = 1500;

		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {
//			pwmSpeed = ioio_.openPwmOutput(7, 100);
//			pwmSteering = ioio_.openPwmOutput(6, 100);
			pwmSpeed = ioio_.openPwmOutput(5, 100);	// roboeaters
			pwmSteering = ioio_.openPwmOutput(10, 100); // roboeaters
			in40 = ioio_.openAnalogInput(40);
			in41 = ioio_.openAnalogInput(41);
			in42 = ioio_.openAnalogInput(42);
			cmdPwm = new float[3];
			cmdPwmPrev = new float[3];
			cmdPwm[DIR] = 1;
			cmdPwm[TURN] = stopTurn;
			cmdPwm[VELO] = stopSpeed;
			cmdPwmPrev = cmdPwm;
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {

			// get IR readings
			try {
				irLeftVal = in40.getVoltage();
				irFrontVal = in41.getVoltage();
				irRightVal = in42.getVoltage();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			// transition from off to on
			if (!drive && toggleDrive.isChecked()) {
				drive = true;
				accelx = 0;
				accely = 0;
				cmdPwm[DIR] = 1;
				cmdPwm[TURN] = stopTurn;
				cmdPwm[VELO] = stopSpeed;
				try {
					pwmSpeed.close();
					Thread.sleep(1000);
					// pwmSpeed = ioio_.openPwmOutput(7, 100);
					pwmSpeed = ioio_.openPwmOutput(5, 100);	// roboeaters
					Thread.sleep(10);
					pwmSpeed.setPulseWidth(stopSpeed);
					pwmSteering.setPulseWidth(stopTurn);
					Thread.sleep(10);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				// Log.d(TAG, "drive OFF to ON");
			}
			// transition from on to off
			else if (drive && !toggleDrive.isChecked()) {
				drive = false;

				// Log.d(TAG, "drive ON to OFF");
			} else if (drive) {
				if (toggleAuto.isChecked()) {
					cmdPwmPrev = cmdPwm;
					cmdPwm = autoDrive(cmdPwm[DIR], irLeftVal, irFrontVal,
							irRightVal);
					try {
						pwmSpeed.setPulseWidth(cmdPwm[VELO]);
						pwmSteering.setPulseWidth(cmdPwm[TURN]);
						Thread.sleep(10);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					if (cmdPwmPrev[DIR] != cmdPwm[DIR]) {
						try {
							pwmSpeed.close();
							Thread.sleep(1000);
							// pwmSpeed = ioio_.openPwmOutput(7, 100);
							pwmSpeed = ioio_.openPwmOutput(5, 100);	// roboeaters
							Log.d(TAG, "reversed direction");
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					} 
				} else {

					if (direction > 0) {
						speed = (stopSpeed - 100) + accely * speedScale
								* direction;
						if (speed > stopSpeed) {
							speed = stopSpeed;
						}

						pwmSpeed.setPulseWidth(speed);
						// Log.d(TAG, "Fwd = " + speed);
					} else {
						speed = stopSpeed + accely * speedScale * direction;
						if (speed < stopSpeed) {
							speed = stopSpeed;
						}
						// Log.d(TAG, "Bwd = " + speed);
					}
					try {
						pwmSpeed.setPulseWidth(speed);
						pwmSteering
								.setPulseWidth(stopTurn + accelx * turnScale);
						Thread.sleep(10);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
				// Log.d(TAG, "driving");
			} else if ((direction == 1 && toggleDirection.isChecked())
					|| (direction == -1 && !toggleDirection.isChecked())) {
				direction = direction * -1;
				try {
					pwmSpeed.setPulseWidth(stopSpeed);
					pwmSteering.setPulseWidth(stopTurn);
					Thread.sleep(10);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else { // drive is false
				try {
					pwmSpeed.setPulseWidth(stopSpeed);
					pwmSteering.setPulseWidth(stopTurn);
					Thread.sleep(10);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				// Log.d(TAG, "paused");
			}
		}

		private float[] autoDrive(float dir, float irL, float irF, float irR) {
			float autoTurnScale = 300;
			float autoSpeedScale = 25;
			float[] cmd;
			cmd = new float[3];

			// Create a population code based on the IR positions and the IR
			// values
			// The X or cosine part of the popcode relates to the direction and
			// magnitude of the turn
			double x = irL * Math.cos(Math.PI * .75) + irF
					* Math.cos(Math.PI * .5) + irR * Math.cos(Math.PI * .25);

			// The Y or sine part of the popcode relates to the forward speed
			// The side IRs are discounted so that the robot will not slow
			// down as much when something is on its side.
			double y = .5 * irL * Math.sin(Math.PI * .75) + irF
					* Math.sin(Math.PI * .5) + .5 * irR
					* Math.sin(Math.PI * .25);

			if (dir > 0) {

				// check if IRs above threshold, and reverse direction
				if (irL > IR_COLLISION || irR > IR_COLLISION
						|| irF > IR_COLLISION) {
					cmd[DIR] = -1; // reverse direction
					cmd[TURN] = stopTurn;
					cmd[VELO] = stopSpeed;
				} else {
					cmd[DIR] = 1;
					cmd[TURN] = (float) (stopTurn + autoTurnScale * x);
					cmd[VELO] = (float) ((stopSpeed - 150) + autoSpeedScale * y);

					// check for forward speed goes below stationary
					if (cmd[VELO] > stopSpeed) {
						cmd[VELO] = stopSpeed;
					}
					else if (cmd[VELO] < (stopSpeed-150)) {
						cmd[VELO] = stopSpeed - 150;
					}
				}
			} else {
				if (irL < IR_CLEAR && irR < IR_CLEAR && irF < IR_CLEAR) {
					cmd[DIR] = 1; // forward direction
					cmd[TURN] = stopTurn;
					cmd[VELO] = stopSpeed;
				} else {
					cmd[DIR] = -1;
					cmd[TURN] = (float) (stopTurn - autoTurnScale * 2.0 * x);
					cmd[VELO] = stopSpeed + 100;
				}
			}

			// Log.d(TAG, "dir=" + cmd[DIR] + " fwd=" + cmd[VELO] + " turn=" +
			// cmd[TURN]);

			return cmd;
		}

	}

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}
}

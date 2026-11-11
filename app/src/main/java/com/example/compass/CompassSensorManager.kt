package com.example.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class CompassData(
    val azimuth: Float = 0f,
    val rawAzimuth: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
) {
    val roundedAzimuth: Int
        get() = azimuth.roundToInt().let { if (it >= 360) 0 else it }

    val cardinalDirection: String
        get() = when (roundedAzimuth) {
            in 338..359, in 0..22 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SW"
            in 248..292 -> "W"
            in 293..337 -> "NW"
            else -> "N"
        }
}

class CompassSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _compassData = MutableStateFlow(CompassData())
    val compassData: StateFlow<CompassData> = _compassData.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Fallback sensor arrays
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    private var currentFilteredAzimuth = 0f
    private var isFirstReading = true
    private val alpha = 0.15f // Exponential moving average factor

    fun startListening() {
        if (sensorManager == null) return

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        } else {
            accelerometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
            magneticSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                computeOrientation(event.accuracy)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                hasAccelerometer = true
                if (hasMagnetometer) {
                    computeFallbackOrientation(event.accuracy)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                hasMagnetometer = true
                if (hasAccelerometer) {
                    computeFallbackOrientation(event.accuracy)
                }
            }
        }
    }

    private fun computeOrientation(accuracy: Int) {
        // Remap coordinate system for portrait camera view held upright facing forward
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedMatrix
        )
        SensorManager.getOrientation(remappedMatrix, orientation)

        var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        azimuthDeg = (azimuthDeg + 360f) % 360f

        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

        applyFilterAndEmit(azimuthDeg, pitchDeg, rollDeg, accuracy)
    }

    private fun computeFallbackOrientation(accuracy: Int) {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        if (success) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientation)

            var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            azimuthDeg = (azimuthDeg + 360f) % 360f

            val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

            applyFilterAndEmit(azimuthDeg, pitchDeg, rollDeg, accuracy)
        }
    }

    private fun applyFilterAndEmit(rawAzimuth: Float, pitch: Float, roll: Float, accuracy: Int) {
        if (isFirstReading) {
            currentFilteredAzimuth = rawAzimuth
            isFirstReading = false
        } else {
            // Shortest circular distance calculation to smoothly cross 0° / 360°
            val diff = (rawAzimuth - currentFilteredAzimuth + 540f) % 360f - 180f
            currentFilteredAzimuth = (currentFilteredAzimuth + alpha * diff + 360f) % 360f
        }

        _compassData.value = CompassData(
            azimuth = currentFilteredAzimuth,
            rawAzimuth = rawAzimuth,
            pitch = pitch,
            roll = roll,
            accuracy = accuracy
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        _compassData.value = _compassData.value.copy(accuracy = accuracy)
    }
}

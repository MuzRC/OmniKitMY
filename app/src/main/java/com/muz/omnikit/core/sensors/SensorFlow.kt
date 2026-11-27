package com.muz.omnikit.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object SensorFlow {

    fun hasSensor(context: Context, sensorType: Int): Boolean {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return manager?.getDefaultSensor(sensorType) != null
    }

    fun observeSensor(
        context: Context,
        sensorType: Int,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI
    ): Flow<FloatArray> = callbackFlow {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(sensorType)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.copyOf())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, samplingPeriodUs)
        awaitClose {
            manager.unregisterListener(listener)
        }
    }
}

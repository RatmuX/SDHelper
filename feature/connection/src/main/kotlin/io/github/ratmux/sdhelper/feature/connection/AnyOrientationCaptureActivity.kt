package io.github.ratmux.sdhelper.feature.connection

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * The scanner library declares its own activity as `sensorLandscape`, which shows the camera image sideways or
 * upside down when the phone is held upright. This subclass only exists so the manifest can declare
 * `fullSensor` for it (the library's documented way to allow any orientation).
 */
class AnyOrientationCaptureActivity : CaptureActivity()

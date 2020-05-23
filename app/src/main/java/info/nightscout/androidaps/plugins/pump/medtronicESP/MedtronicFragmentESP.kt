package info.nightscout.androidaps.plugins.pump.medtronicESP

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.plugins.pump.medtronicESP.events.EventStatusChanged
import info.nightscout.androidaps.plugins.pump.medtronicESP.utils.TimeUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.medtronicesp_fragment.*
import org.slf4j.LoggerFactory
import java.text.DecimalFormat


class MedtronicFragmentESP : Fragment() {
    private val log = LoggerFactory.getLogger("Medtronic")
    private var disposable: CompositeDisposable = CompositeDisposable()

    var precision = DecimalFormat("0.00")
    
    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.medtronicesp_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Click listeners for buttons
        medtronicESP_button_connect.setOnClickListener {
            val medtronic: MedtronicESPPlugin = MedtronicESPPlugin.getPlugin()
            if (medtronic.sMedtronicService == null) {
                log.error("Service not running on click")
            } else {
                if (MedtronicESPPump.getInstance().fatalError) {
                    medtronic.sMedtronicService.disconnectESP()
                } else if (!medtronic.sMedtronicService.runThread) { //Start connecting to pump
                    medtronic.sMedtronicService.connectESP()
                } else if (medtronic.sMedtronicService.runThread) { // Stop connecting to pump
                    medtronic.sMedtronicService.disconnectESP()
                }
                ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("Clicked refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { updateGUI() }
                    }
                })
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += RxBus
                .toObservable(EventStatusChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updatePumpStatus() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val pump: MedtronicESPPump = MedtronicESPPump.getInstance()
                    medtronicESP_basabasalrate.setText(java.lang.String.valueOf(pump.baseBasal))
                    medtronicESP_tempbasal.setText(java.lang.String.valueOf(pump.tempBasal))
                }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventPumpStatusChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    // GUI functions
    @Synchronized
    fun updateGUI() {
        resetGUI()
        val medtronic: MedtronicESPPlugin = MedtronicESPPlugin.getPlugin()
        if (medtronic.sMedtronicService == null) {
            
            med_service_status.setText(MainApp.gs(R.string.med_service_null))
            return
        }
        val pump: MedtronicESPPump = MedtronicESPPump.getInstance()
        if (pump.fatalError) {
            med_service_status.setText(MainApp.gs(R.string.med_service_error))
            medtronicESP_button_connect.setText(MainApp.gs(R.string.med_button_label_reset))
            return
        }
        if (pump.isFakingConnection) {
            med_service_status.setText(MainApp.gs(R.string.med_service_faking))
            medtronicESP_button_connect.setText(MainApp.gs(R.string.med_button_label_faking))
        } else if (!medtronic.sMedtronicService.getRunThread()) {
            med_service_status.setText(MainApp.gs(R.string.med_service_halted))
            medtronicESP_button_connect.setText(MainApp.gs(R.string.med_button_label_connect))
        } else {
            med_service_status.setText(MainApp.gs(R.string.med_service_running))
            medtronicESP_button_connect.setText(MainApp.gs(R.string.med_button_label_stop))
            updatePumpStatus()
            medtronicESP_battery.setText(java.lang.String.valueOf(pump.batteryRemaining))
        }
        medtronicESP_basabasalrate.setText(precision.format(pump.baseBasal).toString())
        medtronicESP_tempbasal.setText(precision.format(pump.tempBasal).toString())
    }

    private fun resetGUI() {
        med_service_status.setText("")
        med_connection_status.setText("")
        med_connection_ext_status.setText("")
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_sleeping))
        medtronicESP_basabasalrate.setText("")
        medtronicESP_tempbasal.setText("")
        medtronicESP_battery.setText("")
        medtronicESP_button_connect.setText("")
    }

    private fun updatePumpStatus() {
        val pump: MedtronicESPPump = MedtronicESPPump.getInstance()
        if (pump.sleepStartTime !== 0L) { // Device sleeping.
            deviceSleeping()
            return
        }
        when (pump.connectPhase) {
            0 -> deviceInitializing()
            1 -> deviceScanning()
            2 -> deviceConnecting()
            3 -> deviceConnected()
            4 -> deviceCommunicating()
        }
    }

    private fun deviceSleeping() {
        //Long agoMsec = System.currentTimeMillis() - pump.sleepStartTime;
        //int agoMin = (int) (agoMsec / 60d / 1000d);
        val pump: MedtronicESPPump = MedtronicESPPump.getInstance()
        var text = MainApp.gs(R.string.med_state_sleeping) +
                DateUtil.timeString(pump.sleepStartTime)
        med_connection_status.setText(text)
        //med_connection_status.setText(MainApp.gs(R.string.med_state_sleeping) + "\n" +
        //                DateUtil.timeString(pump.sleepStartTime) +
        //                " (" + String.format(MainApp.gs(R.string.minago),
        //                agoMin) + ")");
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_sleeping))
        text = precision.format(TimeUtil.countdownTimer(
                pump.sleepStartTime,
                pump.wakeInterval)) +
                MainApp.gs(R.string.med_progress_time)
        med_connection_ext_status.setText(text)
    }

    private fun deviceInitializing() {
        med_connection_status.setText(MainApp.gs(R.string.med_state_waiting))
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_progress))
        med_connection_ext_status.setText(MainApp.gs(R.string.med_progress_waiting))
    }

    private fun deviceScanning() {
        med_connection_status.setText(MainApp.gs(R.string.med_state_scanning))
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_time))
        val text: String = precision.format(TimeUtil.elapsedTime(
                MedtronicESPPump.getInstance().scanStartTime)) +
                MainApp.gs(R.string.med_progress_time)
        med_connection_ext_status.setText(text)
    }

    private fun deviceConnecting() {
        med_connection_status.setText(MainApp.gs(R.string.med_state_connecting))
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_attempt))
        med_connection_ext_status.setText(java.lang.String.valueOf(MedtronicESPPump.getInstance().connectionAttempts))
    }

    private fun deviceConnected() {
        med_connection_status.setText(MainApp.gs(R.string.med_state_connected))
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_attempt))
        med_connection_ext_status.setText(java.lang.String.valueOf(MedtronicESPPump.getInstance().connectionAttempts))
    }

    private fun deviceCommunicating() {
        med_connection_status.setText(MainApp.gs(R.string.med_state_communicating))
        med_connection_ext_status_label.setText(MainApp.gs(R.string.med_progress_label_progress))
        updatePumpProgress()
    }

    private fun updatePumpProgress() {
        var status = MainApp.gs(R.string.med_progress_command_1)
        val pump: MedtronicESPPump = MedtronicESPPump.getInstance()
        when (pump.actionState) {
            0 -> status += MainApp.gs(R.string.med_progress_command_ping)
            1 -> status += MainApp.gs(R.string.med_progress_command_bolus)
            2 -> status += MainApp.gs(R.string.med_progress_command_temp)
            3 -> status += MainApp.gs(R.string.med_progress_command_sleep)
        }
        status += MainApp.gs(R.string.med_progress_command_2) + (pump.commandRetries + 1)
        med_connection_ext_status.setText(status)
    }
}

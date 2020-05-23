package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationAddAction
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import kotlinx.android.synthetic.main.automation_dialog_choose_action.*

class ChooseActionDialog : DialogFragmentWithDate() {

    private var checkedIndex = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // restore checked radio button
        savedInstanceState?.let { bundle ->
            checkedIndex = bundle.getInt("checkedIndex")
        }

        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_choose_action, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (a in AutomationPlugin.getActionDummyObjects()) {
            val radioButton = RadioButton(context)
            radioButton.setText(a.friendlyName())
            radioButton.tag = a.javaClass
            automation_radioGroup.addView(radioButton)
        }

        if (checkedIndex != -1)
            (automation_radioGroup.getChildAt(checkedIndex) as RadioButton).isChecked = true
    }

    override fun submit(): Boolean {
        instantiateAction()?.let {
            RxBus.send(EventAutomationAddAction(it))
            RxBus.send(EventAutomationUpdateGui())
        }
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("checkedIndex", determineCheckedIndex())
    }

    private fun instantiateAction(): Action? {
        return getActionClass()?.let {
            it.newInstance() as Action
        }
    }

    private fun getActionClass(): Class<*>? {
        val radioButtonID = automation_radioGroup.checkedRadioButtonId
        val radioButton = automation_radioGroup.findViewById<RadioButton>(radioButtonID)
        return radioButton?.let {
            it.tag as Class<*>
        }
    }

    private fun determineCheckedIndex(): Int {
        for (i in 0 until automation_radioGroup.childCount) {
            if ((automation_radioGroup.getChildAt(i) as RadioButton).isChecked)
                return i
        }
        return -1
    }
}

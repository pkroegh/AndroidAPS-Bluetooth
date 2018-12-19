package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Basal_Set_Suspend_Off extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Basal_Set_Suspend_Off() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF;
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Turning off suspend");
        }
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else {
                log.error("Result Error: " + result);
                failed = true;
            }
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__SET_SUSPEND_OFF";
    }
}

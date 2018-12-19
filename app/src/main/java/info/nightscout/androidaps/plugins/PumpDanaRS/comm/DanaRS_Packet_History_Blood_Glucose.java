package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_History_Blood_Glucose extends DanaRS_Packet_History_ {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_History_Blood_Glucose() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE;
    }

    public DanaRS_Packet_History_Blood_Glucose(long from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__BLOOD_GLUCOSE";
    }
}

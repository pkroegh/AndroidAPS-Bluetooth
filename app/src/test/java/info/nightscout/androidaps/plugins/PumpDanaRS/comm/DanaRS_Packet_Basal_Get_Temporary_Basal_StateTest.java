package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 01.08.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Basal_Get_Temporary_Basal_StateTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        DanaRS_Packet_Basal_Get_Temporary_Basal_State packet = new DanaRS_Packet_Basal_Get_Temporary_Basal_State();

        // test message decoding
        packet.handleMessage(createArray(50,(byte) 0));
        assertEquals(false, packet.failed);
        packet.handleMessage(createArray(50,(byte) 1));
        assertEquals(true, packet.failed);

        assertEquals("BASAL__TEMPORARY_BASAL_STATE", packet.getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith) {
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = fillWith;
        }
        return ret;
    }
}

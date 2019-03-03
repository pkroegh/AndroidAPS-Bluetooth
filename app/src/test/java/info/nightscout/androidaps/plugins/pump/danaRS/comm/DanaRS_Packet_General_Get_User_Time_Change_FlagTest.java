package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 08.08.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_General_Get_User_Time_Change_FlagTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        DanaRS_Packet_General_Get_User_Time_Change_Flag packet = new DanaRS_Packet_General_Get_User_Time_Change_Flag();

        // test params
        byte[] testparams = packet.getRequestParams();
        assertEquals(null, packet.getRequestParams());

        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, (byte) 0));
        assertEquals(true, packet.failed);
        // everything ok :)
        packet = new DanaRS_Packet_General_Get_User_Time_Change_Flag();
        packet.handleMessage(createArray(18, (byte) 0));
        assertEquals(false, packet.failed);

        assertEquals("REVIEW__GET_USER_TIME_CHANGE_FLAG", packet.getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }

    double[] createArray(int length, double fillWith){
        double[] ret = new double[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}
package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen Georgiev on 8/9/2018.
 */


@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Option_Set_User_OptionTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        DanaRS_Packet_Option_Set_User_Option packet = new DanaRS_Packet_Option_Set_User_Option();

        // test params
        byte[] params = packet.getRequestParams();
        DanaRPump pump = DanaRPump.getInstance();
        assertEquals((byte) (pump.lcdOnTimeSec & 0xff) ,params[3]);

        // test message decoding
        packet.handleMessage(createArray(3, (byte) 0));
        assertEquals(false, packet.failed);
        // everything ok :)
        packet.handleMessage(createArray(17, (byte) 1));
        assertEquals(true, packet.failed);

        assertEquals("OPTION__SET_USER_OPTION", packet.getFriendlyName());
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